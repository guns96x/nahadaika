"""Оцінка якості «розумного часу»: озвучує фрази, шле в Gemini (через Firebase AI Logic) і звіряє з еталоном.

Запуск:  pip install edge-tts   (потрібні також ffmpeg і local.properties з firebase.apiKey/projectId)
         python tools/eval_gemini.py [--text] [модель ...]   за замовчуванням: gemini-3.5-flash-lite gemini-3.1-flash-lite
         --text — слати не звук, а саму фразу (як після «Сказати»); корисно, щоб відрізнити помилки промпта від помилок слуху.

Фіксоване «зараз» — середа 30.09.2026 10:00, Europe/Kyiv, година за замовчуванням 9:00 (як у застосунку й VoiceParserTest).
Промпт і схема — ті самі, що в app/src/main/java/ua/nahadaika/voice/Gemini.kt; змінюючи там, змінюйте й тут.
"""
import asyncio
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
props = dict(
    line.strip().split("=", 1)
    for line in open(os.path.join(HERE, "..", "local.properties"), encoding="utf-8")
    if "=" in line and not line.startswith("#")
)
KEY, PROJECT = props["firebase.apiKey"], props["firebase.projectId"]
# Ключ обмежено застосунком ua.nahadaika — представляємось ним (SHA-1 релізного підпису, публічний).
CERT = props.get("firebase.certSha1", "1E05D4AB7EE688E3013B71E10C6206D2B5B0E1A1")

NOW = "2026-09-30T10:00"
PROMPT = "\n".join([
    "You turn a short voice note into reminders for a reminder app.",
    "The user speaks mostly Ukrainian, sometimes Russian or English.",
    f"Current local time: {NOW} (Wednesday), time zone Europe/Kyiv.",
    "If only a day is named, use 09:00. Weeks start on Monday.",
    "transcript: what was said, verbatim.",
    "reminders: one item per thing to remember. what = short text of the thing, without the time words, in the language of the speech.",
    "Always return an item for a thing to remember, even when no time is named (then when = null).",
    "when = local date-time yyyy-MM-ddTHH:mm in the user's time zone, or null if no time can be determined. Never answer with a time in the past.",
    "A day or date named once applies to the next items of the same note until another is named (tomorrow at 9 ... and at 7 pm: both tomorrow).",
    "repeat = none, daily, weekly, monthly or yearly; for a repeating item, when = its first upcoming occurrence.",
    "alarm = true only if the user asked for an alarm, a wake-up or a timer.",
    "If the note contains no request to remind or to remember anything, return an empty reminders array.",
])
SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "transcript": {"type": "STRING"},
        "reminders": {"type": "ARRAY", "items": {
            "type": "OBJECT",
            "properties": {
                "what": {"type": "STRING"},
                "when": {"type": "STRING", "nullable": True},
                "repeat": {"type": "STRING", "enum": ["none", "daily", "weekly", "monthly", "yearly"]},
                "alarm": {"type": "BOOLEAN"},
            },
            "required": ["what", "when", "repeat", "alarm"],
        }},
    },
    "required": ["transcript", "reminders"],
}

# (голос, фраза, [(when або None, repeat, alarm)]) — еталон. when "2026-10-04T>=17" — будь-яка година від 17.
UK, RU, EN = "uk-UA-OstapNeural", "ru-RU-DmitryNeural", "en-US-GuyNeural"
UK2 = "uk-UA-PolinaNeural"
CASES = [
    (UK, "Нагадай завтра о дев'ятій ранку забрати посилку", [("2026-10-01T09:00", "none", False)]),
    (UK2, "Через двадцять хвилин вимкнути плиту", [("2026-09-30T10:20", "none", False)]),
    (UK, "У п'ятницю о третій дня зустріч з лікарем", [("2026-10-02T15:00", "none", False)]),
    (UK2, "Щодня о восьмій вечора пити таблетки", [("2026-09-30T20:00", "daily", False)]),
    (UK, "Розбуди мене завтра о шостій тридцять", [("2026-10-01T06:30", "none", True)]),
    (UK2, "Купити хліб", [(None, "none", False)]),
    (UK, "Післязавтра подзвонити мамі", [("2026-10-02T09:00", "none", False)]),
    (UK2, "Кожного понеділка о десятій ранку планерка", [("2026-10-05T10:00", "weekly", False)]),
    (UK, "Нагадай сьогодні о п'ятій вечора забрати дитину зі школи", [("2026-09-30T17:00", "none", False)]),
    (RU, "Поставь таймер на десять минут", [("2026-09-30T10:10", "none", True)]),
    (UK2, "Через годину зателефонувати в банк", [("2026-09-30T11:00", "none", False)]),
    (UK, "Першого листопада о дванадцятій оплатити комуналку", [("2026-11-01T12:00", "none", False)]),
    (UK2, "Завтра о дев'ятій купити молоко, а о сьомій вечора забрати посилку",
     [("2026-10-01T09:00", "none", False), ("2026-10-01T19:00", "none", False)]),
    (UK, "Щомісяця п'ятнадцятого числа платити за інтернет", [("2026-10-15T09:00", "monthly", False)]),
    (UK2, "Нагадай мені в неділю ввечері зателефонувати бабусі", [("2026-10-04T>=17", "none", False)]),
    (EN, "Wake me up tomorrow at seven", [("2026-10-01T07:00", "none", True)]),
]


async def synth(voice, text, path_mp3):
    import edge_tts
    await edge_tts.Communicate(text, voice).save(path_mp3)


def to_m4a(mp3, m4a):
    # Як запис застосунку: AAC у контейнері MP4, моно.
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-ac", "1", "-c:a", "aac", "-b:a", "64k", m4a], check=True)


def ask(model, source, as_text=False):
    if as_text:  # як у «Сказати»: текст уже розпізнав системний розпізнавач
        content = {"text": "Voice note, transcribed by a speech recognizer (may contain mistakes): " + source}
    else:
        content = {"inline_data": {"mime_type": "audio/mp4", "data": base64.b64encode(open(source, "rb").read()).decode()}}
    body = {
        "contents": [{"parts": [{"text": PROMPT}, content]}],
        "generationConfig": {"responseMimeType": "application/json", "responseSchema": SCHEMA, "temperature": 0},
    }
    url = f"https://firebasevertexai.googleapis.com/v1beta/projects/{PROJECT}/models/{model}:generateContent"
    for attempt in range(3):
        req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST")
        req.add_header("Content-Type", "application/json")
        req.add_header("x-goog-api-key", KEY)
        req.add_header("X-Android-Package", "ua.nahadaika")
        req.add_header("X-Android-Cert", CERT)
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                text = json.loads(r.read())["candidates"][0]["content"]["parts"][0]["text"]
                return json.loads(text)
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 503) and attempt < 2:
                time.sleep(3 * (attempt + 1))
                continue
            return {"error": f"HTTP {e.code}"}
        except Exception as e:  # таймаут, обрив
            if attempt < 2:
                continue
            return {"error": str(e)[:60]}


def matches(want, got):
    wh, wr, wa = want
    gh, gr, ga = got.get("when"), got.get("repeat"), got.get("alarm")
    if wh is None:
        ok_when = gh in (None, "", "null")
    elif ">=" in wh:
        day, hour = wh.split("T>=")
        ok_when = bool(gh) and gh.startswith(day) and int(gh[11:13]) >= int(hour)
    else:
        ok_when = gh == wh
    return ok_when and gr == wr and bool(ga) == wa


def main():
    args = [a for a in sys.argv[1:] if a != "--text"]
    as_text = "--text" in sys.argv[1:]
    models = args or ["gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]
    tmp = tempfile.mkdtemp()
    files = []
    for i, (voice, text, _) in enumerate(CASES):
        if as_text:
            files.append(text)
            continue
        mp3, m4a = os.path.join(tmp, f"{i}.mp3"), os.path.join(tmp, f"{i}.m4a")
        asyncio.run(synth(voice, text, mp3))
        # Як голосова нотатка в застосунку: AAC 16 кГц моно 32 кбіт/с.
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-ac", "1", "-ar", "16000", "-c:a", "aac", "-b:a", "32k", m4a], check=True)
        files.append(m4a)
    for model in models:
        print(f"\n=== {model}{' (текст)' if as_text else ''} ===")
        passed = 0
        for (voice, text, want), m4a in zip(CASES, files):
            started = time.time()
            res = ask(model, m4a, as_text)
            took = time.time() - started
            got = res.get("reminders") if isinstance(res, dict) else None
            ok = got is not None and len(got) == len(want) and all(matches(w, g) for w, g in zip(want, got))
            passed += ok
            shown = "ERROR " + str(res.get("error")) if got is None else "; ".join(f"{g.get('when')} {g.get('repeat')}{' ⏰' if g.get('alarm') else ''}" for g in got) or "—"
            print(f"{'OK  ' if ok else 'FAIL'} {took:4.1f}s  {text}\n            → {shown}" + ("" if ok else f"   (чекали: {want})"))
        print(f"--- {passed}/{len(CASES)}")


if __name__ == "__main__":
    main()
