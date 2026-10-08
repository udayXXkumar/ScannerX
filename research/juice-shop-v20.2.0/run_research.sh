#!/usr/bin/env bash
set -Eeuo pipefail

# Sequentially run Fast, Medium, Deep, and repeated Deep on isolated pinned labs.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
API_ROOT="${SCANNERX_API_URL:-http://127.0.0.1:8080}"
API_ROOT="${API_ROOT%/}"
TARGET_HOST="${SCANNERX_TARGET_HOST:-127.0.0.1}"
POLL_SECONDS="${SCANNERX_POLL_SECONDS:-10}"
SCAN_TIMEOUT_SECONDS="${SCANNERX_SCAN_TIMEOUT_SECONDS:-5400}"
BACKEND_LOG="${SCANNERX_BACKEND_LOG:-}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_ROOT="${SCANNERX_RESEARCH_OUT:-$SCRIPT_DIR/runs/experiment-$STAMP}"
AUTH_CONFIG=""
ANY_FAILED=0

mkdir -p "$RUN_ROOT"
chmod 700 "$RUN_ROOT"
umask 077
exec > >(tee -a "$RUN_ROOT/runner.log") 2>&1
cleanup() { [[ -z "$AUTH_CONFIG" || ! -f "$AUTH_CONFIG" ]] || rm -f -- "$AUTH_CONFIG"; }
trap cleanup EXIT
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

for tool in curl jq python3 sudo; do command -v "$tool" >/dev/null 2>&1 || fail "Required command missing: $tool"; done
[[ "$POLL_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail 'SCANNERX_POLL_SECONDS must be a positive integer.'
[[ "$SCAN_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail 'SCANNERX_SCAN_TIMEOUT_SECONDS must be a positive integer.'
if [[ -z "${SCANNERX_EMAIL:-}" ]]; then read -r -p 'ScannerX email: ' SCANNERX_EMAIL; fi
if [[ -z "${SCANNERX_PASSWORD:-}" ]]; then read -r -s -p 'ScannerX password: ' SCANNERX_PASSWORD; printf '\n'; fi
[[ -n "$SCANNERX_EMAIL" && -n "$SCANNERX_PASSWORD" ]] || fail 'ScannerX credentials are required.'

printf 'Artifacts: %s\nScannerX API: %s\n' "$RUN_ROOT" "$API_ROOT"
printf 'Starting pinned Juice Shop lab...\n'
sudo docker compose -f "$SCRIPT_DIR/lab/compose.yaml" up -d

for port in 3001 3002 3003 3004; do
  ready=0
  for _ in $(seq 1 60); do
    if curl -fsS --max-time 3 "http://127.0.0.1:$port/api/Challenges" -o /dev/null; then ready=1; break; fi
    sleep 2
  done
  [[ "$ready" == 1 ]] || fail "Juice Shop on port $port did not become ready."
done

# Feed login credentials over stdin so they do not appear in process arguments.
login_json="$(printf '%s\0%s' "$SCANNERX_EMAIL" "$SCANNERX_PASSWORD" | python3 -c 'import json,sys; e,p=sys.stdin.buffer.read().split(b"\0",1); print(json.dumps({"email":e.decode(),"password":p.decode()}))')"
login_file="$RUN_ROOT/.login-response.json"
login_code="$(curl -sS --connect-timeout 5 --max-time 30 -o "$login_file" -w '%{http_code}' -H 'Content-Type: application/json' --data-binary @- "$API_ROOT/api/auth/login" <<<"$login_json")" || fail 'Cannot reach ScannerX login endpoint.'
unset login_json SCANNERX_PASSWORD
[[ "$login_code" == 200 ]] || { rm -f "$login_file"; fail "ScannerX login returned HTTP $login_code."; }
token="$(jq -er '.token' "$login_file")" || fail 'Login response has no token.'
rm -f "$login_file"
AUTH_CONFIG="$(mktemp "$RUN_ROOT/.curl-auth.XXXXXX")"
printf 'header = "Authorization: Bearer %s"\n' "$token" > "$AUTH_CONFIG"
unset token

api_request() {
  local method="$1" url="$2" output="$3" data="${4:-}" code
  local args=(--silent --show-error --connect-timeout 5 --max-time 60 --config "$AUTH_CONFIG" -X "$method" -o "$output" -w '%{http_code}')
  if [[ -n "$data" ]]; then args+=(-H 'Content-Type: application/json' --data-binary "$data"); fi
  code="$(curl "${args[@]}" "$url")" || return 1
  [[ "$code" =~ ^2[0-9][0-9]$ ]] || { printf 'HTTP %s from %s\n' "$code" "$url" >&2; return 1; }
}
capture_optional() {
  if ! api_request GET "$1" "$2"; then printf 'Capture failed: %s (response body retained)\n' "$1"; return 1; fi
}
capture_backend_log() {
  local destination="$1" start_line="$2" current
  [[ -n "$BACKEND_LOG" && -r "$BACKEND_LOG" ]] || return 0
  current="$(wc -l < "$BACKEND_LOG")"
  if (( current >= start_line )); then tail -n "+$((start_line + 1))" "$BACKEND_LOG" > "$destination"; else cp "$BACKEND_LOG" "$destination"; fi
}
if [[ -n "$BACKEND_LOG" && ! -r "$BACKEND_LOG" ]]; then printf 'WARNING: backend log is unreadable: %s\n' "$BACKEND_LOG"; BACKEND_LOG=""; fi

if ! python3 "$SCRIPT_DIR/tools/capture_toolchain.py" --out "$RUN_ROOT/toolchain.json" --docker-sudo; then
  printf 'WARNING: toolchain capture failed; continuing.\n'
fi

tiers=(fast medium deep deep-repeat)
ports=(3001 3002 3003 3004)
exports=()
for i in "${!tiers[@]}"; do
  name="${tiers[$i]}"; port="${ports[$i]}"; dir="$RUN_ROOT/$name"
  target="http://$TARGET_HOST:$port/"
  mkdir -p "$dir"; chmod 700 "$dir"
  printf '\n=== %s: %s ===\n' "$name" "$target"
  python3 "$SCRIPT_DIR/lab/capture_instance.py" --url "$target" --name "$name" --out "$dir" --docker-sudo
  log_start=0
  [[ -z "$BACKEND_LOG" ]] || log_start="$(wc -l < "$BACKEND_LOG")"

  target_json="$dir/target.json"
  target_name="scannerx-juice-$name-$STAMP"
  target_tier="$name"
  [[ "$name" != deep-repeat ]] || target_tier=deep
  target_payload="$(jq -cn --arg n "$target_name" --arg u "$target" --arg t "$target_tier" '{name:$n,baseUrl:$u,defaultTier:$t,timeoutsEnabled:true}')"
  if ! api_request POST "$API_ROOT/api/targets" "$target_json" "$target_payload"; then
    printf 'Target creation failed for %s.\n' "$name" >&2; ANY_FAILED=1; continue
  fi
  target_id="$(jq -er '.id' "$target_json")" || { ANY_FAILED=1; continue; }
  scan_payload="$(jq -cn --argjson id "$target_id" '{target:{id:$id}}')"
  create_json="$dir/scan-create-response.json"
  if ! api_request POST "$API_ROOT/api/scans" "$create_json" "$scan_payload"; then
    printf 'Scan start failed for %s. Check tier availability and active scans.\n' "$name" >&2; ANY_FAILED=1; continue
  fi
  scan_id="$(jq -er '.id' "$create_json")" || { ANY_FAILED=1; continue; }
  printf 'Started scan %s (%s).\n' "$scan_id" "$name"

  started="$(date +%s)"; status='UNKNOWN'; scan_json="$dir/scan.json"
  while true; do
    if ! api_request GET "$API_ROOT/api/scans/$scan_id" "$scan_json"; then status='POLL_FAILED'; break; fi
    status="$(jq -r '.status // "UNKNOWN" | ascii_upcase' "$scan_json")"
    progress="$(jq -r '.progress // 0' "$scan_json")"
    printf '%s status=%s progress=%s%%\n' "$(date -Is)" "$status" "$progress"
    case "$status" in COMPLETED|FAILED|CANCELLED) break;; esac
    if (( $(date +%s) - started >= SCAN_TIMEOUT_SECONDS )); then
      status='AUTOMATION_TIMEOUT'; printf 'Wait deadline reached; scan left running.\n' >&2; break
    fi
    sleep "$POLL_SECONDS"
  done
  printf '%s\n' "$status" > "$dir/runner-status.txt"

  capture_optional "$API_ROOT/api/reports/scans/$scan_id/research.json" "$dir/scannerx-research.json" || ANY_FAILED=1
  capture_optional "$API_ROOT/api/reports/scans/$scan_id/json" "$dir/normalized-report.json" || ANY_FAILED=1
  capture_optional "$API_ROOT/api/scans/$scan_id/activity" "$dir/activity.json" || ANY_FAILED=1
  if [[ -s "$dir/scannerx-research.json" ]]; then
    jq --slurpfile report "$dir/scannerx-research.json" \
      '.scanId=$report[0].scanId | .scanTier=$report[0].scanTier | .scanStatus=$report[0].scanStatus | .scanStartedAt=$report[0].scanStartedAt | .scanCompletedAt=$report[0].scanCompletedAt | .scanDurationSeconds=$report[0].scanDurationSeconds' \
      "$dir/instance_manifest.json" > "$dir/instance_manifest.tmp.json"
    mv "$dir/instance_manifest.tmp.json" "$dir/instance_manifest.json"
  fi
  capture_backend_log "$dir/backend.log" "$log_start"
  if [[ -s "$dir/scannerx-research.json" ]] && jq -e '.findings | type == "array"' "$dir/scannerx-research.json" >/dev/null; then
    exports+=("$dir/scannerx-research.json")
  else
    printf 'Research export is missing or malformed for scan %s.\n' "$scan_id" >&2
    ANY_FAILED=1
  fi
  [[ "$status" == COMPLETED ]] || { printf 'Scan %s ended with %s.\n' "$scan_id" "$status" >&2; ANY_FAILED=1; }
  if [[ "$status" == AUTOMATION_TIMEOUT || "$status" == POLL_FAILED ]]; then
    printf 'Stopping the tier sequence because this scan may still be active.\n' >&2
    break
  fi
done

if [[ -s "$RUN_ROOT/fast/challenge_api.json" ]]; then
  python3 "$SCRIPT_DIR/challenge_matrix.py" --source "$SCRIPT_DIR/challenges-v20.2.0.yml" \
    --api-snapshot "$RUN_ROOT/fast/challenge_api.json" --output "$RUN_ROOT/challenge_matrix.json"
  cp "$SCRIPT_DIR/ground_truth.json" "$RUN_ROOT/ground_truth.json"
  jq --arg snap 'fast/challenge_api.json' \
    --slurpfile fast "$RUN_ROOT/fast/instance_manifest.json" \
    --slurpfile medium "$RUN_ROOT/medium/instance_manifest.json" \
    --slurpfile deep "$RUN_ROOT/deep/instance_manifest.json" \
    --slurpfile repeat "$RUN_ROOT/deep-repeat/instance_manifest.json" \
    '.instanceApiSnapshot=$snap
    | .instanceApiSnapshots=[
        {runName:"fast",apiSnapshot:"fast/challenge_api.json",image:$fast[0].image,imageId:$fast[0].imageId,architecture:$fast[0].architecture,repoDigests:$fast[0].repoDigests,challengeCount:$fast[0].challengeCount},
        {runName:"medium",apiSnapshot:"medium/challenge_api.json",image:$medium[0].image,imageId:$medium[0].imageId,architecture:$medium[0].architecture,repoDigests:$medium[0].repoDigests,challengeCount:$medium[0].challengeCount},
        {runName:"deep",apiSnapshot:"deep/challenge_api.json",image:$deep[0].image,imageId:$deep[0].imageId,architecture:$deep[0].architecture,repoDigests:$deep[0].repoDigests,challengeCount:$deep[0].challengeCount},
        {runName:"deep-repeat",apiSnapshot:"deep-repeat/challenge_api.json",image:$repeat[0].image,imageId:$repeat[0].imageId,architecture:$repeat[0].architecture,repoDigests:$repeat[0].repoDigests,challengeCount:$repeat[0].challengeCount}
      ]
    | .groundTruthFrozen=false' \
    "$RUN_ROOT/ground_truth.json" > "$RUN_ROOT/ground_truth.tmp.json"
  mv "$RUN_ROOT/ground_truth.tmp.json" "$RUN_ROOT/ground_truth.json"
else
  cp "$SCRIPT_DIR/ground_truth.json" "$RUN_ROOT/ground_truth.json"
fi

SCORING_GROUND_TRUTH="$RUN_ROOT/ground_truth.json"
SCORING_MATRIX="$RUN_ROOT/challenge_matrix.json"
if [[ -s "$RUN_ROOT/fast/instance_manifest.json" \
   && -s "$RUN_ROOT/medium/instance_manifest.json" \
   && -s "$RUN_ROOT/deep/instance_manifest.json" \
   && -s "$RUN_ROOT/deep-repeat/instance_manifest.json" ]]; then
  validation_dir="$RUN_ROOT/ground-truth-validation"
  versioned_gt_dir="$RUN_ROOT/ground-truth-v2.0.0"
  if python3 "$SCRIPT_DIR/tools/validate_ground_truth.py" \
      --lab-root "$RUN_ROOT" --out "$validation_dir"; then
    if python3 "$SCRIPT_DIR/tools/build_ground_truth_v2.py" \
        --source "$SCRIPT_DIR/challenges-v20.2.0.yml" \
        --validation "$validation_dir/validation.json" \
        --out-dir "$versioned_gt_dir"; then
      SCORING_GROUND_TRUTH="$versioned_gt_dir/ground_truth-v2.0.0.json"
      SCORING_MATRIX="$versioned_gt_dir/challenge_matrix-v2.0.0.json"
    else
      printf 'Versioned ground-truth build failed; keeping the candidate v1 oracle, which will not yield metrics.\n' >&2
      ANY_FAILED=1
    fi
  else
    printf 'Independent ground-truth validation failed; keeping the candidate v1 oracle, which will not yield metrics.\n' >&2
    ANY_FAILED=1
  fi
else
  printf 'A complete four-instance run is unavailable; scoped ground-truth validation was skipped.\n'
fi

if ((${#exports[@]})); then
  eval_args=(--ground-truth "$SCORING_GROUND_TRUTH" --runs "${exports[@]}" --out "$RUN_ROOT/evaluation")
  [[ ! -s "$SCORING_MATRIX" ]] || eval_args+=(--matrix "$SCORING_MATRIX")
  [[ ! -s "$RUN_ROOT/toolchain.json" ]] || eval_args+=(--toolchain "$RUN_ROOT/toolchain.json")
  python3 "$SCRIPT_DIR/tools/evaluate.py" "${eval_args[@]}" || ANY_FAILED=1
fi

printf '\nRaw research artifacts: %s\n' "$RUN_ROOT"
printf 'Includes ScannerX research/normalized JSON, scan records, activity logs, challenge snapshots, toolchain, evaluation, and runner.log.\n'
[[ -z "$BACKEND_LOG" ]] || printf 'Backend log deltas are saved as each run directory/backend.log.\n'
if (( ANY_FAILED )); then printf 'At least one scan/capture failed; inspect artifacts and runner.log.\n' >&2; exit 1; fi
printf 'All four scans and captures completed.\n'
