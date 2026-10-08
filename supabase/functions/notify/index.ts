// Нагадайка: миттєві сповіщення учасників спільного чату (FCM v1).
//
// Телефон після запису в спільний чат кличе POST /functions/v1/notify з Firebase ID token у Authorization.
// Функція: 1) перевіряє токен за ключами securetoken (JWKS); 2) читає чат і токени учасників через Firestore REST
// ТОКЕНОМ САМОГО КОРИСТУВАЧА — правила Firestore самі перевіряють, що він учасник; 3) шле data-повідомлення FCM
// іншим телефонам. Ключ сервісного акаунта Firebase (лише роль на FCM) — у секреті FCM_SERVICE_ACCOUNT.
// GET — перевірка, що функція жива (її ж смикає щотижневий keep-alive у GitHub Actions, щоб проєкт не заснув).
import { createRemoteJWKSet, importPKCS8, jwtVerify, SignJWT } from "npm:jose@5.9.6";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const PROJECT = Deno.env.get("FIREBASE_PROJECT_ID") ?? "nahadaika-89a9c";
const FIRESTORE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;
const JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);
/** Не більше стількох сповіщень від одного користувача за хвилину. */
const PER_MINUTE = 30;

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

async function firebaseUid(req: Request): Promise<{ uid: string; token: string } | null> {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  try {
    const { payload } = await jwtVerify(token, JWKS, {
      issuer: `https://securetoken.google.com/${PROJECT}`,
      audience: PROJECT,
      algorithms: ["RS256"],
    });
    if (typeof payload.sub !== "string" || payload.sub.length === 0) return null;
    return { uid: payload.sub, token };
  } catch {
    return null;
  }
}

/** Документ Firestore від імені користувача; null — немає доступу або не існує. */
async function firestore(path: string, idToken: string): Promise<any | null> {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: `Bearer ${idToken}` } });
  if (!res.ok) return null;
  return await res.json();
}

/** Що не так з налаштуванням FCM — лише назва етапу, без вмісту секрету. */
class FcmSetupError extends Error {}

let cachedAccess: { token: string; until: number } | null = null;

/** OAuth-токен для FCM v1 із ключа сервісного акаунта (підпис JWT, обмін у Google). */
async function fcmAccessToken(): Promise<string> {
  if (cachedAccess && cachedAccess.until > Date.now() + 60_000) return cachedAccess.token;
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) throw new FcmSetupError("secret missing");
  let sa: { private_key?: string; client_email?: string };
  try {
    sa = JSON.parse(raw);
  } catch {
    throw new FcmSetupError("secret is not valid JSON");
  }
  if (!sa.private_key || !sa.client_email) throw new FcmSetupError("secret lacks private_key/client_email");
  let key;
  try {
    key = await importPKCS8(sa.private_key, "RS256");
  } catch {
    throw new FcmSetupError("private_key unreadable");
  }
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/firebase.messaging" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!res.ok) throw new FcmSetupError(`google oauth ${res.status}`);
  const body = await res.json();
  cachedAccess = { token: body.access_token, until: Date.now() + body.expires_in * 1000 };
  return cachedAccess.token;
}

Deno.serve(async (req) => {
  if (req.method === "GET") {
    // Keep-alive: легкий запит до бази, щоб безкоштовний проєкт не вважався неактивним.
    const { error } = await db.from("push_log").select("id", { head: true, count: "exact" }).limit(1);
    return json(error ? 500 : 200, { ok: !error });
  }
  if (req.method !== "POST") return json(405, { error: "method" });

  const user = await firebaseUid(req);
  if (!user) return json(401, { error: "auth" });

  let body: { chat?: string; urgent?: boolean; from?: string };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "body" });
  }
  const chat = body.chat ?? "";
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(chat)) return json(400, { error: "chat" });

  const { data: allowed, error } = await db.rpc("push_allow", { p_uid: user.uid, p_limit: PER_MINUTE });
  if (error) return json(500, { error: "limit" });
  if (!allowed) return json(429, { error: "too many" });

  // Читання чату пройде лише для учасника — це і є перевірка членства.
  const chatDoc = await firestore(`chats/${chat}`, user.token);
  if (!chatDoc) return json(403, { error: "not a member" });
  const members = new Set<string>(
    (chatDoc.fields?.members?.arrayValue?.values ?? []).map((v: any) => v.stringValue),
  );
  const tokens = new Set<string>();
  let page = "";
  // Сторінками: у чаті можуть лишатися старі пристрої (перевстановлення), не губимо нові за ними.
  for (let i = 0; i < 5; i++) {
    const push = await firestore(`chats/${chat}/push?pageSize=100${page ? `&pageToken=${encodeURIComponent(page)}` : ""}`, user.token);
    for (const doc of push?.documents ?? []) {
      const uid = doc.fields?.uid?.stringValue;
      const token = doc.fields?.token?.stringValue;
      // Лише чинні учасники; свій телефон-відправник не будимо (інші телефони того ж акаунта — так).
      if (uid && token && members.has(uid) && token !== body.from) tokens.add(token);
    }
    page = push?.nextPageToken ?? "";
    if (!page) break;
  }
  if (tokens.size === 0) return json(200, { sent: 0 });

  let access: string;
  try {
    access = await fcmAccessToken();
  } catch (e) {
    // Немає або зіпсований секрет FCM_SERVICE_ACCOUNT — телефони підхоплять зміни фоновою перевіркою.
    return json(503, { error: "fcm not configured", why: e instanceof FcmSetupError ? e.message : "unexpected" });
  }
  const urgent = body.urgent !== false;
  let sent = 0;
  await Promise.all([...tokens].slice(0, 100).map(async (token) => {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${PROJECT}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          // Лише що синхронізувати — вміст телефон забере сам із Firestore.
          data: { chat },
          android: { priority: urgent ? "HIGH" : "NORMAL", ttl: "86400s", collapse_key: `chat_${chat}` },
        },
      }),
    });
    if (res.ok) sent++;
  }));
  return json(200, { sent, of: tokens.size });
});
