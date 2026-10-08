// Нагадайка: лист із запрошенням на пошту (SMTP Gmail із пароля застосунку).
//
// Телефон після запису запрошення в Firestore (mailInvites/{id}) кличе POST /functions/v1/invite-mail з Firebase ID token.
// Функція: 1) перевіряє токен (JWKS securetoken); 2) читає запрошення ТОКЕНОМ КОРИСТУВАЧА — правила Firestore самі
// пускають лише того, хто його створив і є учасником чату, тож надіслати можна тільки на адресу зі справжнього запрошення;
// 3) шле лист. Секрети: GMAIL_USER, GMAIL_APP_PASSWORD (tools/setup_mail.sh). Ліміт — як у notify, через push_allow.
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { SMTPClient } from "https://deno.land/x/denomailer@1.6.0/mod.ts";

const PROJECT = Deno.env.get("FIREBASE_PROJECT_ID") ?? "nahadaika-89a9c";
const FIRESTORE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;
const PAGE = Deno.env.get("INVITE_PAGE") ?? "https://guns96x.github.io/nahadaika/";
const JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);
/** Не більше стількох листів від одного користувача за хвилину. */
const PER_MINUTE = 6;

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));

async function firebaseUser(req: Request): Promise<{ uid: string; token: string } | null> {
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

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  const user = await firebaseUser(req);
  if (!user) return json(401, { error: "auth" });

  let id = "";
  try {
    id = String((await req.json()).invite ?? "");
  } catch {
    return json(400, { error: "body" });
  }
  if (!/^[A-Za-z0-9_-]{1,64}_[^/\s]{3,200}$/.test(id)) return json(400, { error: "invite" });

  const user_ = Deno.env.get("GMAIL_USER");
  const pass = Deno.env.get("GMAIL_APP_PASSWORD");
  if (!user_ || !pass) return json(503, { error: "mail not configured" });

  const { data: allowed, error } = await db.rpc("push_allow", { p_uid: user.uid, p_limit: PER_MINUTE });
  if (error) return json(500, { error: "limit" });
  if (!allowed) return json(429, { error: "too many" });

  const res = await fetch(`${FIRESTORE}/mailInvites/${encodeURIComponent(id)}`, { headers: { Authorization: `Bearer ${user.token}` } });
  if (!res.ok) return json(403, { error: "no such invite" });
  const f = (await res.json()).fields ?? {};
  const email = f.email?.stringValue ?? "";
  const code = f.code?.stringValue ?? "";
  const chatName = f.chatName?.stringValue ?? "";
  const fromName = f.fromName?.stringValue ?? "";
  if (f.createdBy?.stringValue !== user.uid || !email || !code) return json(403, { error: "not yours" });

  const link = `${PAGE}?c=${encodeURIComponent(code)}`;
  const who = fromName ? esc(fromName) : "Вас";
  const subject = `Запрошення в «${chatName}» у Нагадайці`;
  const text =
    `${fromName ? fromName + " запрошує" : "Вас запрошують"} у спільний чат «${chatName}» у Нагадайці.\n\n` +
    `1. Встановіть або оновіть застосунок і відкрийте запрошення: ${link}\n` +
    `2. Увійдіть через Google із цією поштою (${email}) — запрошення з'явиться в застосунку.\n\n` +
    `Код запрошення: ${code}`;
  const html =
    `<div style="font-family:system-ui,sans-serif;max-width:480px;margin:auto;line-height:1.5">` +
    `<h2 style="margin:0 0 12px">Нагадайка</h2>` +
    `<p>${fromName ? who + " запрошує вас" : "Вас запрошують"} у спільний чат <b>«${esc(chatName)}»</b>.</p>` +
    `<p><a href="${esc(link)}" style="display:inline-block;padding:12px 20px;background:#6b5bd6;color:#fff;border-radius:12px;text-decoration:none;font-weight:600">Встановити або оновити й приєднатися</a></p>` +
    `<p style="color:#666;font-size:14px">Увійдіть у застосунку через Google із цією поштою (${esc(email)}) — запрошення з'явиться там.<br>Код запрошення: <b>${esc(code)}</b></p></div>`;

  const client = new SMTPClient({
    connection: { hostname: "smtp.gmail.com", port: 465, tls: true, auth: { username: user_, password: pass } },
  });
  try {
    await client.send({ from: `Нагадайка <${user_}>`, to: email, subject, content: text, html });
  } catch {
    return json(502, { error: "smtp" });
  } finally {
    await client.close().catch(() => {});
  }
  return json(200, { sent: 1 });
});
