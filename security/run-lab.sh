#!/usr/bin/env bash
# Attack lab: runs every scan and attack in security/README.md against a
# throwaway, production-shaped local stack, then tears it down.
#
#   bash security/run-lab.sh
#
# Reports land in security/reports/<timestamp>/ (gitignored). The script
# prints a pass/fail table per tool and exits non-zero if anything gating
# failed: a gitleaks finding, a dependency with CVSS >= 7, a High from ZAP,
# or a failed k6 check that is not marked report-only.
#
# It NEVER targets anything but this machine. The targets are fixed to
# 127.0.0.1 and the lab's compose service names; there is no flag to change
# that, on purpose.
#
# Optional environment:
#   NVD_API_KEY            speeds up OWASP dependency-check (hours -> minutes on a cold cache)
#   LAB_SKIP_DEPENDENCY_CHECK=1   skip the (slow) Maven CVE scan
#   LAB_COMPOSE_OVERRIDE   an extra compose file, for environments whose image
#                          builds need a proxy or mirror
#   LAB_API_PORT / LAB_WEB_PORT   host ports (default 8081 / 8082, bound to 127.0.0.1)
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
LAB_DIR="$REPO/security"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT="$LAB_DIR/reports/$STAMP"
mkdir -p "$REPORT"
chmod 777 "$REPORT" # ZAP writes as its own non-root user

export LAB_API_PORT="${LAB_API_PORT:-8081}"
export LAB_WEB_PORT="${LAB_WEB_PORT:-8082}"
API="http://127.0.0.1:$LAB_API_PORT"
WEB="http://127.0.0.1:$LAB_WEB_PORT"
NETWORK="iunu-lab_lab"

K6_IMAGE="${K6_IMAGE:-grafana/k6:2.3.0}"
ZAP_IMAGE="${ZAP_IMAGE:-zaproxy/zap-stable:latest}"
GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-zricethezav/gitleaks:v8.30.1}"

log() { printf '\n==> %s\n' "$*"; }

# --- 1. Refuse anything that is not local -----------------------------------
for url in "$API" "$WEB"; do
  host="$(printf '%s' "$url" | sed -E 's#^[a-z]+://([^/:]+).*#\1#')"
  case "$host" in
    127.0.0.1|localhost) ;;
    *) echo "Refusing to run: $url is not local." >&2; exit 2 ;;
  esac
done
for target in http://frontend:8080 http://proxy/v3/api-docs http://proxy/api http://backend:8080; do
  host="$(printf '%s' "$target" | sed -E 's#^[a-z]+://([^/:]+).*#\1#')"
  case "$host" in
    proxy|frontend|backend) ;;
    *) echo "Refusing to run: $target is not a lab service." >&2; exit 2 ;;
  esac
done

# --- Lab-only secrets, generated per run, never written to disk --------------
export LAB_DB_PASSWORD="$(openssl rand -hex 24)"
export LAB_JWT_SECRET="$(openssl rand -base64 48)"
export LAB_EDGE_SECRET="$(openssl rand -hex 32)"
export LAB_ADMIN_EMAIL="lab-admin@iunu-lab.test"
export LAB_ADMIN_PASSWORD="Lab$(openssl rand -hex 12)a1"

COMPOSE=(docker compose -f "$LAB_DIR/docker-compose.lab.yml")
if [ -n "${LAB_COMPOSE_OVERRIDE:-}" ]; then COMPOSE+=(-f "$LAB_COMPOSE_OVERRIDE"); fi

declare -A STATUS DETAIL
record() { STATUS[$1]="$2"; DETAIL[$1]="$3"; }

teardown() {
  log "Collecting backend logs and tearing the lab down"
  "${COMPOSE[@]}" logs --no-color backend > "$REPORT/backend.log" 2>&1 || true
  "${COMPOSE[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
}
trap teardown EXIT

# --- 2. Start the stack --------------------------------------------------------
log "Building and starting the lab (prod profile, tmpfs database)"
if ! "${COMPOSE[@]}" up -d --build --wait --wait-timeout 600; then
  echo "The lab did not come up. See 'docker compose logs'." >&2
  "${COMPOSE[@]}" logs --no-color > "$REPORT/startup.log" 2>&1
  exit 1
fi
for _ in $(seq 1 60); do
  curl -fsS "$API/actuator/health/readiness" | grep -q UP && break
  sleep 2
done

# --- 3. Lab admin token ----------------------------------------------------------
log "Signing in as the lab admin"
ADMIN_TOKEN="$(curl -fsS -H 'Content-Type: application/json' \
  -d "{\"email\":\"$LAB_ADMIN_EMAIL\",\"password\":\"$LAB_ADMIN_PASSWORD\"}" \
  "$API/api/auth/login" | jq -r .accessToken)"
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "Lab admin login failed." >&2
  exit 1
fi

# --- 4. Secrets in git history ---------------------------------------------------
log "gitleaks over the full git history"
docker run --rm --user "$(id -u):$(id -g)" -v "$REPO:/repo" -e GIT_CONFIG_COUNT=1 \
  -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0='*' \
  "$GITLEAKS_IMAGE" git /repo --redact --no-banner \
  --report-format json --report-path "/repo/security/reports/$STAMP/gitleaks.json" \
  > "$REPORT/gitleaks.log" 2>&1
code=$?
leaks="$(jq 'length' "$REPORT/gitleaks.json" 2>/dev/null || echo "?")"
if [ $code -eq 0 ]; then record gitleaks PASS "0 findings"
elif [ "$leaks" != "?" ]; then record gitleaks FAIL "$leaks finding(s)"
else record gitleaks ERROR "did not run (exit $code), see gitleaks.log"; fi

# --- 5. Vulnerable libraries -----------------------------------------------------
if [ "${LAB_SKIP_DEPENDENCY_CHECK:-}" = "1" ]; then
  record dependency-check SKIPPED "LAB_SKIP_DEPENDENCY_CHECK=1"
else
  log "OWASP dependency-check (failBuildOnCVSS=7)"
  [ -z "${NVD_API_KEY:-}" ] && echo "WARNING: NVD_API_KEY is not set; the first run downloads the whole NVD feed and can take hours."
  (cd "$REPO/backend" && mvn -B -q -Psecurity org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 \
    -DoutputDirectory="$REPORT") > "$REPORT/dependency-check.log" 2>&1
  code=$?
  high="$(jq '[.dependencies[]?.vulnerabilities[]? | select((.cvssv3.baseScore // .cvssv2.score // 0) >= 7)] | length' \
    "$REPORT/dependency-check-report.json" 2>/dev/null || echo "?")"
  if [ $code -eq 0 ]; then record dependency-check PASS "0 with CVSS >= 7"
  elif [ "$high" != "?" ]; then record dependency-check FAIL "$high with CVSS >= 7"
  else record dependency-check ERROR "did not run (exit $code), see dependency-check.log"; fi
fi

log "npm audit (production dependencies, high and above)"
(cd "$REPO" && npm audit --omit=dev --audit-level=high --json) > "$REPORT/npm-audit.json" 2> "$REPORT/npm-audit.log"
code=$?
npm_high="$(jq '(.metadata.vulnerabilities.high // 0) + (.metadata.vulnerabilities.critical // 0)' "$REPORT/npm-audit.json" 2>/dev/null || echo "?")"
if [ $code -eq 0 ]; then record npm-audit PASS "0 high/critical"
elif [ "$npm_high" != "?" ]; then record npm-audit FAIL "$npm_high high/critical"
else record npm-audit ERROR "did not run (exit $code)"; fi

# --- 6. k6 on clean data ------------------------------------------------------------
# smoke and abuse run before ZAP: the active scans fill the database with junk
# (properties whose image URL is "ZAP", for instance) that would make a smoke
# run fail for reasons unrelated to the app.
k6_run() { # name script [extra -e args...]
  local name="$1" script="$2"; shift 2
  docker run --rm --network "$NETWORK" -v "$REPO/load-tests:/scripts:ro" -v "$REPORT:/reports" \
    -e BASE_URL=http://proxy/api "$@" "$K6_IMAGE" run --quiet \
    --summary-export "/reports/k6-$name.json" "/scripts/$script" > "$REPORT/k6-$name.log" 2>&1
  local code=$?
  local failed passed
  # .metrics.checks only: other Rate metrics (http_req_failed) also carry
  # passes/fails, and counting those would report requests as checks.
  failed="$(jq '.metrics.checks.fails // 0' "$REPORT/k6-$name.json" 2>/dev/null || echo "?")"
  passed="$(jq '.metrics.checks.passes // 0' "$REPORT/k6-$name.json" 2>/dev/null || echo "?")"
  if [ $code -eq 0 ]; then record "k6 $name" PASS "$passed checks passed"
  elif [ "$failed" != "?" ]; then record "k6 $name" FAIL "$failed of $((passed + failed)) checks failed"
  else record "k6 $name" ERROR "did not run (exit $code), see k6-$name.log"; fi
}

log "k6 smoke.js"
k6_run smoke smoke.js
log "k6 abuse.js (BEHIND_PROXY=yes)"
k6_run abuse abuse.js -e BEHIND_PROXY=yes

# --- 7. ZAP ---------------------------------------------------------------------------
cp "$LAB_DIR/zap-rules.tsv" "$REPORT/zap-rules.tsv"
zap_highs() { jq '[.site[]?.alerts[]? | select(.riskcode == "3")] | length' "$REPORT/$1" 2>/dev/null || echo "?"; }
zap_record() { # name report exitcode
  local highs; highs="$(zap_highs "$2")"
  if [ "$highs" = "?" ]; then record "$1" ERROR "did not run (exit $3), see ${2%.json}.log"
  elif [ "$highs" = "0" ]; then record "$1" PASS "0 High ($(jq '[.site[]?.alerts[]?] | length' "$REPORT/$2") alerts total)"
  else record "$1" FAIL "$highs High"; fi
}

log "ZAP baseline against the frontend"
docker run --rm --network "$NETWORK" -v "$REPORT:/zap/wrk:rw" "$ZAP_IMAGE" \
  zap-baseline.py -t http://frontend:8080 -c zap-rules.tsv -I \
  -J zap-baseline.json -r zap-baseline.html > "$REPORT/zap-baseline.log" 2>&1
zap_record "zap baseline" zap-baseline.json $?

ZAP_ACTIVE_LIMITS="-config scanner.maxScanDurationInMins=15 -config scanner.maxRuleDurationInMins=3"
log "ZAP API scan, anonymous"
docker run --rm --network "$NETWORK" -v "$REPORT:/zap/wrk:rw" "$ZAP_IMAGE" \
  zap-api-scan.py -t http://proxy/v3/api-docs -f openapi -c zap-rules.tsv -I \
  -z "$ZAP_ACTIVE_LIMITS" -J zap-api-anon.json -r zap-api-anon.html > "$REPORT/zap-api-anon.log" 2>&1
zap_record "zap api (anonymous)" zap-api-anon.json $?

# ZAP_AUTH_HEADER_VALUE is ZAP's packaged-scan hook for a static header; it
# adds the header to every request through ZAP's replacer.
log "ZAP API scan, as the lab admin"
docker run --rm --network "$NETWORK" -v "$REPORT:/zap/wrk:rw" \
  -e ZAP_AUTH_HEADER=Authorization -e ZAP_AUTH_HEADER_VALUE="Bearer $ADMIN_TOKEN" -e ZAP_AUTH_HEADER_SITE=proxy \
  "$ZAP_IMAGE" zap-api-scan.py -t http://proxy/v3/api-docs -f openapi -c zap-rules.tsv -I \
  -z "$ZAP_ACTIVE_LIMITS" -J zap-api-admin.json -r zap-api-admin.html > "$REPORT/zap-api-admin.log" 2>&1
zap_record "zap api (admin)" zap-api-admin.json $?

# --- 8. attack.js -----------------------------------------------------------------
# The authenticated scan spends the admin's per-user buckets; let them refill.
log "Waiting 61s for the admin rate-limit buckets to refill, then k6 attack.js"
sleep 61
k6_run attack attack.js -e LAB_ADMIN_EMAIL="$LAB_ADMIN_EMAIL" -e LAB_ADMIN_PASSWORD="$LAB_ADMIN_PASSWORD" \
  -e REPORT_DIR=/reports

# M2 residual, report only: the same 2MB body with no Content-Length.
log "Chunked 2MB body (report only, M2)"
head -c $((2 * 1024 * 1024)) /dev/zero | tr '\0' 'A' | sed 's/^/{"message":"/; s/$/"}/' > "$REPORT/.big.json"
CHUNKED_STATUS="$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Transfer-Encoding: chunked' --data-binary @"$REPORT/.big.json" "$API/api/contact")"
rm -f "$REPORT/.big.json"

# The ../../evil.png upload must not have landed outside the upload root.
EVIL_FILES="$("${COMPOSE[@]}" exec -T backend sh -c 'find / -xdev -name "evil*" 2>/dev/null' | wc -l)"

# --- 9. What the detection side saw -------------------------------------------------
log "Reading iunu.* metrics"
curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" "$API/actuator/prometheus" > "$REPORT/prometheus.txt" || true
{
  printf '%-26s %s\n' "security event" "count"
  for type in LOGIN_FAILED ACCOUNT_LOCKED LOGIN_SUCCEEDED_ADMIN ADMIN_LOGIN_NEW_IP REFRESH_REUSE_DETECTED \
      REFRESH_RACE_LOST PASSWORD_RESET_REQUESTED PASSWORD_RESET_COMPLETED PASSWORD_CHANGED RATE_LIMITED \
      EDGE_SECRET_REJECTED UPLOAD_REJECTED ACCESS_DENIED TOKEN_INVALID; do
    count="$(curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$API/actuator/metrics/iunu.security.events?tag=type:$type" | jq '.measurements[0].value' 2>/dev/null)"
    printf '%-26s %s\n' "$type" "${count:-?}"
  done
  for name in iunu.auth.login.failed iunu.auth.account.locked iunu.translation.budget.exceeded; do
    count="$(curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" "$API/actuator/metrics/$name" | jq '.measurements[0].value' 2>/dev/null)"
    printf '%-26s %s\n' "$name" "${count:-?}"
  done
  for bucket in login write public admin translation; do
    count="$(curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$API/actuator/metrics/iunu.ratelimit.rejected?tag=bucket:$bucket" | jq '.measurements[0].value' 2>/dev/null)"
    printf '%-26s %s\n' "ratelimit.rejected $bucket" "${count:-?}"
  done
} > "$REPORT/detection.txt"

# --- 10. Summary --------------------------------------------------------------------
teardown
trap - EXIT

# Nothing secret may appear in the app's own logs.
LEAKED_LINES="$(grep -cE "Bearer |$LAB_ADMIN_PASSWORD|\"refreshToken\"" "$REPORT/backend.log" || true)"
if [ "$LEAKED_LINES" = "0" ]; then record "log hygiene" PASS "no token/password in backend.log"
else record "log hygiene" FAIL "$LEAKED_LINES suspicious line(s) in backend.log"; fi

{
  echo "IUNU attack lab - $STAMP"
  echo
  printf '%-22s %-8s %s\n' "tool" "result" "detail"
  printf '%-22s %-8s %s\n' "----" "------" "------"
  for tool in gitleaks dependency-check npm-audit "k6 smoke" "k6 abuse" "zap baseline" "zap api (anonymous)" \
      "zap api (admin)" "k6 attack" "log hygiene"; do
    printf '%-22s %-8s %s\n' "$tool" "${STATUS[$tool]:-?}" "${DETAIL[$tool]:-}"
  done
  echo
  echo "Report only:"
  echo "  M2 chunked 2MB body, no Content-Length: HTTP $CHUNKED_STATUS"
  jq -r '.report | to_entries[] | "  \(.key): \(.value)"' "$REPORT/attack-summary.json" 2>/dev/null
  echo "  files named evil* anywhere in the backend container: $EVIL_FILES"
  echo
  cat "$REPORT/detection.txt"
} | tee "$REPORT/summary.txt"

rc=0
for tool in "${!STATUS[@]}"; do
  case "${STATUS[$tool]}" in FAIL|ERROR) rc=1 ;; esac
done
[ "$EVIL_FILES" != "0" ] && rc=1
echo
echo "Reports: $REPORT"
exit $rc
