#!/usr/bin/env bash
# Одноразове налаштування сервера сповіщень і ключа API. Запускає ВЛАСНИК проєкту зі свого ПК —
# ключ сервісного акаунта йде з Google прямо в Supabase і одразу видаляється, у чат/агентів не потрапляє.
#
# Потрібно: gcloud (winget install Google.CloudSDK) з `gcloud auth login`, і `npx supabase login`.
# Запуск (у Git Bash, не в WSL; gcloud має бути в PATH): bash tools/setup_push.sh
set -euo pipefail

PROJECT=nahadaika-89a9c
KEY_ID=ec81d8e4-2788-47f2-bfca-51c982143333        # Android key (auto created by Firebase)
SUPABASE_REF=lqspxwvyikuarfbfgnqf
SA=nahadaika-push@$PROJECT.iam.gserviceaccount.com
RELEASE_SHA1=1E05D4AB7EE688E3013B71E10C6206D2B5B0E1A1
DEBUG_SHA1=FBD9F3539F247A194A72147318B397942867F66E

# Ніяких запитів і сторінкових переглядачів: усе має йти без участі людини.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1
export CLOUDSDK_PAGER=
exec </dev/null

echo "1/3 Обмежую ключ API пакетом ua.nahadaika і двома SHA-1…"
gcloud services api-keys update "$KEY_ID" --project="$PROJECT" \
  --allowed-application=sha1_fingerprint=$RELEASE_SHA1,package_name=ua.nahadaika \
  --allowed-application=sha1_fingerprint=$DEBUG_SHA1,package_name=ua.nahadaika >/dev/null
gcloud services api-keys describe "$KEY_ID" --project="$PROJECT" \
  --format="value(restrictions.androidKeyRestrictions.allowedApplications[].sha1Fingerprint)"

echo "2/3 Сервісний акаунт лише з правом слати FCM…"
gcloud iam service-accounts describe "$SA" --project="$PROJECT" >/dev/null 2>&1 \
  || gcloud iam service-accounts create nahadaika-push --project="$PROJECT" --display-name="Nahadaika push (FCM only)"
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" \
  --role=roles/firebasecloudmessaging.admin --condition=None >/dev/null

echo "3/3 Ключ акаунта → секрет Supabase FCM_SERVICE_ACCOUNT (файли одразу видаляються)…"
# Ключ живе лише в секреті Supabase: ключі від минулих запусків прибираємо.
for old in $(gcloud iam service-accounts keys list --iam-account="$SA" --project="$PROJECT" \
    --managed-by=user --format="value(name.basename())"); do
  gcloud iam service-accounts keys delete "$old" --iam-account="$SA" --project="$PROJECT" --quiet
done
KEY_FILE=$(mktemp)
ENV_FILE=$(mktemp)
trap 'rm -f "$KEY_FILE" "$ENV_FILE"' EXIT
gcloud iam service-accounts keys create "$KEY_FILE" --iam-account="$SA" --project="$PROJECT"
# Один рядок у одинарних лапках: так значення доходить без спотворень (аргументом через npx.cmd воно ламається).
{ printf "FCM_SERVICE_ACCOUNT='"; tr -d '\r\n' < "$KEY_FILE"; printf "'\n"; } > "$ENV_FILE"
npx --yes supabase secrets set --project-ref "$SUPABASE_REF" --env-file "$ENV_FILE" >/dev/null

echo "Готово. Перевірка: python tools/push_smoke_test.py — останній рядок має бути OK."
