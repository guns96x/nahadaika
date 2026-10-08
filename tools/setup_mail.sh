#!/usr/bin/env bash
# Одноразово: листи-запрошення від Нагадайки через Gmail (SMTP, безкоштовно).
# Потрібен пароль застосунку Google (Акаунт Google → Безпека → Двоетапна перевірка → Паролі застосунків) і `npx supabase login`.
# Пароль вводиться тут, у приховане поле, і йде просто в секрет Supabase — у чат/агентів не потрапляє.
# Запуск (Git Bash): bash tools/setup_mail.sh
set -euo pipefail
SUPABASE_REF=lqspxwvyikuarfbfgnqf

read -r -p "Gmail, з якого шлемо листи: " GMAIL_USER
read -r -s -p "Пароль застосунку (16 символів, пробіли не важливі): " GMAIL_APP_PASSWORD
echo
# Тимчасовий файл — у поточному каталозі, а не в /tmp: так його бачить і Windows-Node (WSL, Git Bash).
ENV_FILE=".mail-secrets.$$.tmp"
trap 'rm -f "$ENV_FILE"' EXIT
umask 077
{ printf "GMAIL_USER='%s'\n" "$GMAIL_USER"; printf "GMAIL_APP_PASSWORD='%s'\n" "$(printf %s "$GMAIL_APP_PASSWORD" | tr -d ' \r\n')"; } > "$ENV_FILE"
npx --yes supabase secrets set --project-ref "$SUPABASE_REF" --env-file "$ENV_FILE" >/dev/null
echo "Готово: секрети GMAIL_USER і GMAIL_APP_PASSWORD задано. Функція invite-mail шле листи від $GMAIL_USER."
