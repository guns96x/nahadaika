"""Перевірка правил Firestore на живому проєкті трьома анонімними користувачами (без виводу ключів і токенів).

Запуск: python tools/firebase_rules_test.py  (ключ і проєкт — з local.properties). Після змін у firebase/firestore.rules.
Для деплою зі знімком правил, відкатом при помилці й очищенням синтетичних даних:
node tools/verify_deploy_firestore.cjs --apply (FIREBASE_TOOLS_ROOT — каталог firebase-tools).
Прямий запуск цього Python-файлу не очищає запрошення й тестові акаунти.
"""
import json
import os
import sys
import uuid
import urllib.error
import urllib.request

props = dict(
    line.strip().split("=", 1)
    for line in open(__import__("os").path.join(__import__("os").path.dirname(__file__), "..", "local.properties"), encoding="utf-8")
    if "=" in line and not line.startswith("#")
)
KEY = props["firebase.apiKey"]
PROJECT = props["firebase.projectId"]
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"


def call(method, url, token=None, body=None):
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, {}


def sign_up():
    s, j = call("POST", f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={KEY}", body={"returnSecureToken": True})
    assert s == 200, s
    return j["idToken"], j["localId"]


def sv(x):
    if isinstance(x, bool):
        return {"booleanValue": x}
    if isinstance(x, int):
        return {"integerValue": str(x)}
    if isinstance(x, list):
        return {"arrayValue": {"values": [sv(i) for i in x]}}
    if isinstance(x, dict):
        return {"mapValue": {"fields": {k: sv(v) for k, v in x.items()}}}
    return {"stringValue": x}


def fields(d):
    return {"fields": {k: sv(v) for k, v in d.items()}}


results = []


def check(name, got, want):
    ok = got == want
    results.append(ok)
    print(("OK   " if ok else "FAIL ") + name + ("" if ok else f" (отримали {got}, чекали {want})"))


a_tok, a = sign_up()
b_tok, b = sign_up()
c_tok, c = sign_up()  # стороння людина без запрошення

chat_id = "codexrules_" + uuid.uuid4().hex
code = uuid.uuid4().hex[:6].upper()
if os.getenv("FIREBASE_TEST_MANIFEST"):
    with open(os.environ["FIREBASE_TEST_MANIFEST"], "w", encoding="utf-8") as manifest:
        json.dump({"project": PROJECT, "chat": chat_id, "invite": code,
                   "users": [a, b, c], "fakeChat": "fake" + c[:6]}, manifest)

# Власник створює чат і запрошення.
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}", a_tok, fields({"name": "Тест", "members": [a], "names": {a: "Я"}, "owner": a, "inviteCode": code}))
check("власник створює чат", s, 200)
s, _ = call("PATCH", f"{BASE}/invites/{code}", a_tok, fields({"chatId": chat_id, "createdBy": a}))
check("власник створює запрошення", s, 200)

# Чужий не бачить і не пише.
check("сторонній не читає чат", call("GET", f"{BASE}/chats/{chat_id}", c_tok)[0], 403)
check("сторонній не пише нагадування", call("PATCH", f"{BASE}/chats/{chat_id}/reminders/x", c_tok, fields({"text": "зламав", "triggerAt": 1}))[0], 403)
check("список запрошень закритий", call("GET", f"{BASE}/invites", c_tok)[0], 403)
check("створити чат із чужими учасниками не можна", call("PATCH", f"{BASE}/chats/fake{c[:6]}", c_tok, fields({"name": "x", "members": [c, a]}))[0], 403)

# Другий учасник приєднується за кодом.
check("запрошення читається за кодом", call("GET", f"{BASE}/invites/{code}", b_tok)[0], 200)
mask = "updateMask.fieldPaths=members&updateMask.fieldPaths=names.%%60%s%%60&updateMask.fieldPaths=joinCode" % b
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}?{mask}&updateMask.fieldPaths=name", b_tok,
            fields({"members": [a, b], "names": {b: "Оля"}, "joinCode": code, "name": "Підміна"}))
check("код не дозволяє підмінити назву при вході", s, 403)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}?{mask}&updateMask.fieldPaths=names.%60{a}%60", b_tok,
            fields({"members": [a, b], "names": {a: "Підміна", b: "Оля"}, "joinCode": code}))
check("код не дозволяє підмінити ім'я іншого учасника", s, 403)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}?{mask}", b_tok, fields({"members": [a, b], "names": {b: "Оля"}, "joinCode": code}))
check("учасник приєднується за кодом", s, 200)
check("учасник не додає сторонню людину", call("PATCH", f"{BASE}/chats/{chat_id}?updateMask.fieldPaths=members", a_tok,
      fields({"members": [a, b, c]}))[0], 403)
check("учасник не видаляє іншого", call("PATCH", f"{BASE}/chats/{chat_id}?updateMask.fieldPaths=members", b_tok,
      fields({"members": [b]}))[0], 403)
check("учасник не підміняє власника", call("PATCH", f"{BASE}/chats/{chat_id}?updateMask.fieldPaths=owner", b_tok,
      fields({"owner": b}))[0], 403)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}?updateMask.fieldPaths=members&updateMask.fieldPaths=joinCode", c_tok, fields({"members": [a, c], "joinCode": "WRONG1"}))
check("з неправильним кодом приєднатись не можна", s, 403)

# Нагадування й коментарі.
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}/reminders/r1", b_tok, fields({"text": "Хліб", "triggerAt": 1893456000000, "repeat": "NONE", "alarm": False, "done": False, "authorUid": b, "authorName": "Оля"}))
check("учасник додає нагадування", s, 200)
check("нагадування від чужого імені заборонено", call("PATCH", f"{BASE}/chats/{chat_id}/reminders/spoof", b_tok,
      fields({"text": "Підміна", "triggerAt": 1893456000000, "authorUid": a}))[0], 403)
check("автора нагадування не можна підмінити", call("PATCH", f"{BASE}/chats/{chat_id}/reminders/r1?updateMask.fieldPaths=authorUid", a_tok,
      fields({"authorUid": a}))[0], 403)
s, j = call("GET", f"{BASE}/chats/{chat_id}/reminders", a_tok)
check("власник бачить нагадування Олі", len(j.get("documents", [])), 1)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}/reminders/r1?updateMask.fieldPaths=done", a_tok, fields({"done": True}))
check("власник позначає «готово»", s, 200)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}/comments/c1", b_tok, fields({"reminderId": "r1", "text": "Куплю", "authorUid": b, "authorName": "Оля", "createdAt": 1}))
check("учасник пише коментар", s, 200)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}/comments/c2", b_tok, fields({"reminderId": "r1", "text": "Підробка", "authorUid": a, "authorName": "Я", "createdAt": 2}))
check("коментар від чужого імені заборонено", s, 403)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}/comments/c1?updateMask.fieldPaths=text", b_tok, fields({"text": "Змінив"}))
check("коментар не редагується", s, 403)

# Запит «мої чати» (відновлення на новому телефоні).
q = {"structuredQuery": {"from": [{"collectionId": "chats"}], "where": {"fieldFilter": {"field": {"fieldPath": "members"}, "op": "ARRAY_CONTAINS", "value": {"stringValue": b}}}}}
s, j = call("POST", f"{BASE}:runQuery", b_tok, q)
check("запит «мої чати» повертає чат", sum(1 for x in j if "document" in x) if isinstance(j, list) else -1, 1)
s, j = call("POST", f"{BASE}:runQuery", c_tok, {"structuredQuery": {"from": [{"collectionId": "chats"}]}})
check("запит усіх чатів без фільтра заборонено", s, 403)

# Вихід і видалення: останній учасник видаляє чат.
check("власник не видаляє чат, поки є учасники", call("DELETE", f"{BASE}/chats/{chat_id}", a_tok)[0], 403)
s, _ = call("PATCH", f"{BASE}/chats/{chat_id}?updateMask.fieldPaths=members", b_tok, fields({"members": [a]}))
check("учасник виходить", s, 200)
check("вийшовший більше не читає", call("GET", f"{BASE}/chats/{chat_id}", b_tok)[0], 403)
check("останній учасник видаляє чат", call("DELETE", f"{BASE}/chats/{chat_id}", a_tok)[0], 200)

print(f"\nпройшло {sum(results)} з {len(results)}")
sys.exit(0 if all(results) else 1)
