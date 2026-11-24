#!/usr/bin/env bash
# =====================================================================
# FrioVale - Teste manual de ANTES e DEPOIS dos dois controles (2FA e IDOR)
# Mesmo script para as duas fases: ele detecta se o login ainda entrega
# o token so com a senha (ANTES) ou se exige o segundo fator (DEPOIS).
#
# Uso:  API_URL=http://localhost:8080 MISSION_CODE=CODIGO_MISSAO ./teste-manual.sh
# Requer: curl e jq. Use APENAS usuarios sinteticos e o backend da propria equipe.
# =====================================================================
set -u
API_URL="${API_URL:-http://localhost:8080}"
MC="${MISSION_CODE:-CODIGO_MISSAO}"
OUT_DIR="${OUT_DIR:-../evidencias}"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/${MC}_manual_$(date +%Y%m%d_%H%M%S).txt"
exec > >(tee -a "$OUT") 2>&1

line() { printf '%s\n' "------------------------------------------------------------"; }
http() { # metodo rota token [corpo] -> imprime "STATUS<TAB>CORPO"
  local m="$1" p="$2" t="${3:-}" b="${4:-}"
  local args=(-s -o /tmp/fv_body -w '%{http_code}' -X "$m" "$API_URL$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$b" ] && args+=(--data "$b")
  local s; s=$(curl "${args[@]}"); printf '%s\t%s' "$s" "$(tr -d '\000' < /tmp/fv_body | head -c 4000)"
}
check() { # descricao status esperado
  local ok="FALHOU"; [[ "$2" =~ ^($3)$ ]] && ok="OK"
  printf '%-62s HTTP %s (esperado %s) -> %s\n' "$1" "$2" "$3" "$ok"
}

# Faz login completo e devolve o token de acesso em $ACCESS (pede o codigo do app)
login_user() {
  local label="$1"; read -rp "E-mail do $label: " EMAIL; read -rsp "Senha do $label: " PASS; echo
  local r; r=$(http POST /api/auth/login "" "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
  local s="${r%%$'\t'*}" body="${r#*$'\t'}"
  ACCESS=$(jq -r '.token // empty' <<<"$body"); MFA=$(jq -r '.mfaToken // empty' <<<"$body")
  if [ -n "$ACCESS" ]; then echo "[$label] login devolveu o token de acesso so com a senha (sistema SEM 2FA)"; return; fi
  if [ "$(jq -r '.enrollmentRequired' <<<"$body")" = "true" ]; then
    echo "[$label] ainda nao cadastrou o 2FA. Entre uma vez pela tela web e escaneie o QR Code."; exit 1; fi
  read -rp "Codigo atual do aplicativo autenticador do $label: " CODE
  r=$(http POST /api/auth/2fa/verify "$MFA" "{\"code\":\"$CODE\"}")
  ACCESS=$(jq -r '.token // empty' <<<"${r#*$'\t'}")
  [ -z "$ACCESS" ] && { echo "[$label] codigo recusado: ${r%%$'\t'*}"; exit 1; }
}

echo "FrioVale - teste manual | codigo de missao: $MC | API: $API_URL | $(date -Iseconds)"
line; echo "CONTROLE 1 - AUTENTICACAO EM DOIS FATORES"; line
read -rp "E-mail do usuario A: " EMAIL_A; read -rsp "Senha do usuario A: " PASS_A; echo
r=$(http POST /api/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS_A\"}"); s="${r%%$'\t'*}"; body="${r#*$'\t'}"
TOKEN_SO_SENHA=$(jq -r '.token // empty' <<<"$body"); MFA=$(jq -r '.mfaToken // empty' <<<"$body")
if [ -n "$TOKEN_SO_SENHA" ]; then
  echo "ANTES: o login com senha correta devolveu o TOKEN DE ACESSO (HTTP $s)"
  r=$(http GET /api/trips "$TOKEN_SO_SENHA"); echo "ANTES: GET /api/trips so com a senha -> HTTP ${r%%$'\t'*} (acesso liberado sem 2o fator)"
  ACCESS_A="$TOKEN_SO_SENHA"
else
  check "1. Senha correta sem OTP: login devolve so o mfaToken" "$s" "200"
  r=$(http GET /api/trips "$MFA"); check "   Contorno: mfaToken em rota protegida" "${r%%$'\t'*}" "403"
  if [ "$(jq -r '.enrollmentRequired' <<<"$body")" = "true" ]; then echo "Cadastre o 2FA pela tela web e rode de novo."; exit 1; fi
  r=$(http POST /api/auth/2fa/verify "$MFA" '{"code":"000000"}'); check "2. OTP incorreto (000000)" "${r%%$'\t'*}" "401"
  read -rp "Opcional - digite um codigo ANOTADO ha mais de 2 minutos (Enter para pular): " OLD
  [ -n "$OLD" ] && { r=$(http POST /api/auth/2fa/verify "$MFA" "{\"code\":\"$OLD\"}"); check "3. OTP expirado" "${r%%$'\t'*}" "401"; }
  read -rp "Digite o codigo ATUAL do aplicativo autenticador: " CODE
  r=$(http POST /api/auth/2fa/verify "$MFA" "{\"code\":\"$CODE\"}"); check "4. OTP valido" "${r%%$'\t'*}" "200"
  ACCESS_A=$(jq -r '.token // empty' <<<"${r#*$'\t'}")
  MFA2=$(jq -r '.mfaToken' <<<"$(http POST /api/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS_A\"}" | cut -f2-)")
  r=$(http POST /api/auth/2fa/verify "$MFA2" "{\"code\":\"$CODE\"}"); check "5. Reutilizar o mesmo OTP (replay)" "${r%%$'\t'*}" "401"
  read -rp "Opcional - codigo de recuperacao NAO usado (Enter para pular): " REC
  if [ -n "$REC" ]; then
    r=$(http POST /api/auth/2fa/recovery "$MFA2" "{\"recoveryCode\":\"$REC\"}"); check "6. Codigo de recuperacao - 1o uso" "${r%%$'\t'*}" "200"
    MFA3=$(jq -r '.mfaToken' <<<"$(http POST /api/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS_A\"}" | cut -f2-)")
    r=$(http POST /api/auth/2fa/recovery "$MFA3" "{\"recoveryCode\":\"$REC\"}"); check "7. Mesmo codigo de recuperacao de novo" "${r%%$'\t'*}" "401"
  fi
fi

line; echo "CONTROLE 2 - PROTECAO CONTRA IDOR (A tenta usar os IDs de B)"; line
login_user "usuario B (dono do dispositivo B)"; ACCESS_B="$ACCESS"
r=$(http GET /api/trips "$ACCESS_B"); TRIP_B=$(jq -r '.[0].id // empty' <<<"${r#*$'\t'}"); CONT_B=$(jq -r '.[0].containerId // empty' <<<"${r#*$'\t'}")
[ -z "$TRIP_B" ] && { echo "O usuario B precisa ter uma viagem atribuida."; exit 1; }
echo "Dispositivo do usuario B: viagem $TRIP_B, conteiner $CONT_B"
attack() { local r; r=$(http "$1" "$2" "$ACCESS_A" "${3:-}"); check "A: $4" "${r%%$'\t'*}" "${5:-403|404}"; }
attack GET  "/api/trips/$TRIP_B/telemetry"            ""  "le as leituras do dispositivo B"
attack GET  "/api/containers/$CONT_B"                 ""  "consulta o conteiner B"
attack GET  "/api/thingspeak/logs?containerId=$CONT_B" "" "le os logs brutos do dispositivo B"
attack GET  "/api/reports/trips/$TRIP_B/pdf"          ""  "baixa o PDF da viagem B"
attack PUT  "/api/trips/$TRIP_B" '{"status":"IN_TRANSIT","destination":"Alterado por A"}' "altera a configuracao da viagem B"
attack POST "/api/ia/trips/$TRIP_B/reprocess"         ""  "comando: reprocessar IA da viagem B"
attack POST "/api/impacts/sync?containerId=$CONT_B&results=1" "" "comando: sincronizar o dispositivo B"
r=$(http GET /api/trips "$ACCESS_A"); if jq -e --argjson id "$TRIP_B" 'map(.id) | index($id)' <<<"${r#*$'\t'}" >/dev/null; then
  echo "A: lista de viagens contem a viagem B -> VULNERAVEL"; else echo "A: lista de viagens NAO contem a viagem B -> OK"; fi
r=$(http GET "/api/trips/$TRIP_B/telemetry" "$ACCESS_B"); check "B: le as leituras do PROPRIO dispositivo" "${r%%$'\t'*}" "200"
line; echo "Resultado salvo em: $OUT"
echo "No log do backend procure por: [$MC] OTP BLOQUEADO  e  [$MC] IDOR BLOQUEADO"
