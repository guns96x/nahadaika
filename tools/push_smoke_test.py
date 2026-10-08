"""Перевірка сервера миттєвих сповіщень (Supabase Edge Function notify) на живих проєктах.

Запуск: python tools/push_smoke_test.py  (ключ Firebase — з local.properties, ключі й токени не друкуються).
Двоє анонімних користувачів створюють чат, реєструють вигадані FCM-токени; перевіряється вхід, членство
й відправка (FCM відповість, що токени недійсні, — це нормально: важливо, що функція до нього дійшла).
Наприкінці все тестове прибирається.
"""
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
props = dict(
    line.strip().split("=", 1)
    for line in open(os.path.join(HERE, "..", "local.properties"), encoding="utf-8")
    if "=" in line and not line.startswith("#")
)
KEY, PROJECT = props["firebase.apiKey"], props["firebase.projectId"]
CERT = props.get("firebase.certSha1", "1E05D4AB7EE688E3013B71E10C6206D2B5B0E1A1")
PUSH = props.get("push.url", "https://lqspxwvyikuarfbfgnqf.supabase.co/functions/v1/notify")
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"


def call(method, url, token=None, body=None):
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Android-Package", "ua.nahadaika")
    req.add_header("X-Android-Cert", CERT)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except ValueError:
            return e.code, {}


def sign_up():
    s, j = call("POST", f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={KEY}", body={"returnSecureToken": True})
    assert s == 200, s
    return j["idToken"], j["localId"]


def fields(d):
    def sv(x):
        if isinstance(x, list):
            return {"arrayValue": {"values": [sv(i) for i in x]}}
        if isinstance(x, dict):
            return {"mapValue": {"fields": {k: sv(v) for k, v in x.items()}}}
        return {"stringValue": x}
    return {"fields": {k: sv(v) for k, v in d.items()}}


results = []


def check(name, got, want):
    ok = got == want
    results.append(ok)
    print(("OK   " if ok else "FAIL ") + name + ("" if ok else f" (отримали {got}, чекали {want})"))


a_tok, a = sign_up()
b_tok, b = sign_up()
c_tok, c = sign_up()
chat = "pushtest_" + uuid.uuid4().hex[:12]
code = uuid.uuid4().hex[:6].upper()  # запрошення не видаляються правилами — щоразу нове

check("сервер живий (GET)", call("GET", PUSH)[0], 200)
check("без входу — 401", call("POST", PUSH, body={"chat": chat})[0], 401)
check("підроблений токен — 401", call("POST", PUSH, "eyJhbGciOiJSUzI1NiJ9.e30.x", {"chat": chat})[0], 401)
check("дивна назва чату — 400", call("POST", PUSH, a_tok, {"chat": "../x"})[0], 400)

call("PATCH", f"{BASE}/chats/{chat}", a_tok, fields({"name": "Push", "members": [a], "names": {a: "A"}, "owner": a, "inviteCode": code}))
call("PATCH", f"{BASE}/invites/{code}", a_tok, fields({"chatId": chat, "createdBy": a}))
mask = "updateMask.fieldPaths=members&updateMask.fieldPaths=names.%%60%s%%60&updateMask.fieldPaths=joinCode" % b
call("PATCH", f"{BASE}/chats/{chat}?{mask}", b_tok, fields({"members": [a, b], "names": {b: "B"}, "joinCode": code}))
call("PATCH", f"{BASE}/chats/{chat}/push/{a}_device-test-a", a_tok, fields({"uid": a, "token": "fake-token-a"}))
call("PATCH", f"{BASE}/chats/{chat}/push/{b}_device-test-b", b_tok, fields({"uid": b, "token": "fake-token-b"}))

check("сторонній — 403 (правила Firestore не пускають)", call("POST", PUSH, c_tok, {"chat": chat})[0], 403)
s, j = call("POST", PUSH, a_tok, {"chat": chat, "urgent": True, "from": "fake-token-a"})
if s == 503:
    print("WAIT у функції ще немає секрету FCM_SERVICE_ACCOUNT (див. docs/SHARED.md) — решта працює")
else:
    # 200 і of=1: знайшла лише токен Б (свій пропустила). sent=0 — токен вигаданий, FCM його відхилив.
    check("учасник: функція знайшла токен іншого й звернулась до FCM", (s, j.get("of")), (200, 1))

# Прибрати.
for path, tok in ((f"chats/{chat}/push/{a}_device-test-a", a_tok), (f"chats/{chat}/push/{b}_device-test-b", b_tok)):
    call("DELETE", f"{BASE}/{path}", tok)
call("PATCH", f"{BASE}/chats/{chat}?updateMask.fieldPaths=members", b_tok, fields({"members": [a]}))
call("DELETE", f"{BASE}/chats/{chat}", a_tok)
for tok in (a_tok, b_tok, c_tok):
    call("POST", f"https://identitytoolkit.googleapis.com/v1/accounts:delete?key={KEY}", body={"idToken": tok})

print(f"\nпройшло {sum(results)} з {len(results)}")
sys.exit(0 if all(results) else 1)
