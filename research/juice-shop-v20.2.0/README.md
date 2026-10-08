# ScannerX OWASP Juice Shop benchmark

This directory contains the reproducibility and evaluation scaffold for an
unauthenticated ScannerX experiment against the official OWASP Juice Shop
v20.2.0 image.

## Ground truth status

`challenge_matrix.json` is a version-specific catalogue classification built
from the checked-in official v20.2.0 `challenges.yml` snapshot
(`challenges-v20.2.0.yml`). Its SHA-256 is recorded in the matrix. It is a candidate matrix, not yet a
scoring oracle: the local instance challenge API has not been captured or
reviewed, and a challenge being listed does not by itself prove that an
unauthenticated scanner can detect it. Challenges marked as candidates must be
validated in the pinned instance before they enter `instances` in
`ground_truth.json`.

That original matrix/oracle is retained as the v1 review scaffold. The reviewed
and validated, deliberately scoped v2 artifacts are described below.

The complete challenge catalogue and disabled-environment metadata are retained
in the matrix. Scoring excludes manual/application-logic challenges, challenges
disabled in the actual lab, and instances without an evidence-backed endpoint,
method, and parameter (where applicable).

## Reviewed ground truth v2.0.0

The versioned review artifacts are in `ground-truth/v2.0.0/`. The matrix
classifies every official v20.2.0 challenge as automatically detectable (A),
specialized active (B), manual/application logic (C), or out of scope (D).
Classification is triage only; a challenge name or enabled flag is not proof of
a vulnerability and does not automatically become a false negative.

The scoped v2 scoring oracle contains three independently validated,
unauthenticated instances: missing CSP on `GET /`, missing Referrer-Policy on
`GET /`, and SQL injection in `GET /rest/products/search?q`. SQL injection was
independently checked with SQLMap boolean/error techniques on all four lab
instances. No rows were dumped, and no stacked or time-based probes were used.
The header checks were observed directly on all four root responses. The
official v20.2.0 search-route source marks this query sink for the
`unionSqlInjectionChallenge` and `dbSchemaChallenge` challenges:
[routes/search.ts at v20.2.0](https://github.com/juice-shop/juice-shop/blob/v20.2.0/routes/search.ts).

The v2 oracle is frozen **only for those three declared checks**. Other A/B
challenge rows remain unvalidated candidates. Its precision, recall, and F1
describe the scoped checks, not whole-application Juice Shop accuracy. Scanner
findings outside the declared endpoints/classes are labeled `OUT_OF_SCOPE`,
not false positives.

### Vulnerability-class coverage by scan mode

`ground-truth/v2.0.0/vulnerability-class-coverage-v1.0.0.csv` and its JSON
companion map the 32 requested vulnerability classes to current Fast, Medium,
and Deep checks. They are
a capability and limitation map, not a claim that all 32 weaknesses are
present in this Juice Shop build or that a listed tool always detects them.
Several classes require authenticated state, a browser, an out-of-band
callback, or a safe application-specific interaction. Such cases are marked
conditional or excluded from unauthenticated scoring. Only independently
validated instances belong in the frozen ground truth.

The light crawler now follows same-origin JavaScript bundles and extracts
explicit `/api/`, `/rest/`, `/graphql`, and `/.well-known/` route literals. This
adds candidate endpoints to the existing discovery scope; it does not treat a
discovered route as a vulnerability. Medium now runs bounded Dalfox and SQLMap
checks after its ZAP active scan (up to five query URLs for Dalfox and two for
SQLMap, with per-step timeouts). Fast remains quick and non-invasive. Deep
retains the broader crawl, active ZAP, Dalfox, and SQLMap work.

The Medium active probes can increase scan time and generate requests that may
change application state. Use them only on systems where active testing is
authorized; use Fast for low-impact discovery.

Repeat the validation and matrix generation against the four running pinned
instances with:

```sh
python3 research/juice-shop-v20.2.0/tools/validate_ground_truth.py
python3 research/juice-shop-v20.2.0/tools/build_ground_truth_v2.py
```

This requires lab ports 3001–3004 and SQLMap. The artifacts preserve challenge
API snapshots, root response headers, image references, and raw SQLMap output.
The original completed experiment was evaluated against v2 using:

```sh
python3 research/juice-shop-v20.2.0/tools/evaluate.py \
  --ground-truth research/juice-shop-v20.2.0/ground-truth/v2.0.0/ground_truth-v2.0.0.json \
  --matrix research/juice-shop-v20.2.0/ground-truth/v2.0.0/challenge_matrix-v2.0.0.json \
  --runs research/juice-shop-v20.2.0/runs/experiment-20261007T191847Z/fast/scannerx-research.json \
        research/juice-shop-v20.2.0/runs/experiment-20261007T191847Z/medium/scannerx-research.json \
        research/juice-shop-v20.2.0/runs/experiment-20261007T191847Z/deep/scannerx-research.json \
        research/juice-shop-v20.2.0/runs/experiment-20261007T191847Z/deep-repeat/scannerx-research.json \
  --out research/juice-shop-v20.2.0/ground-truth/v2.0.0/evaluation/experiment-20261007T191847Z
```

## Lab

`lab/compose.yaml` pins the multi-architecture OCI manifest by digest. It starts
four isolated instances on ports 3001–3004 so Fast, Medium, Deep, and repeated
Deep can begin from independent databases. Capture `/api/Challenges` and image
metadata with `lab/capture_instance.py` before scanning. Do not substitute a
public demo instance.

```sh
sudo docker compose -f lab/compose.yaml up -d
python3 lab/capture_instance.py --url http://127.0.0.1:3001 --name fast --out runs/fast --docker-sudo
python3 lab/capture_instance.py --url http://127.0.0.1:3002 --name medium --out runs/medium --docker-sudo
python3 lab/capture_instance.py --url http://127.0.0.1:3003 --name deep --out runs/deep --docker-sudo
python3 lab/capture_instance.py --url http://127.0.0.1:3004 --name deep-repeat --out runs/deep-repeat --docker-sudo
python3 tools/capture_toolchain.py --out runs/toolchain.json
```

The API endpoint emits the raw per-finding export at
`GET /api/reports/scans/{scanId}/research.json` for the authenticated scan owner.
It preserves raw evidence, tool name, AI fields separately, and the effective
tier plan. Tool versions not known to the application are marked unavailable;
capture `toolchain.json` on the scanner host and keep it with the run artifacts.
Record each API export as `runs/<name>/scannerx-research.json`.
Use `--docker-sudo` when your account needs `sudo docker`; sudo prompts for its
password interactively. The capture script verifies the running container's
configured digest-pinned reference and its local platform image digest.

## Automated experiment

After rebuilding and restarting ScannerX with the current backend, run the full
experiment from the repository root:

```sh
./research/juice-shop-v20.2.0/run_research.sh
```

The script prompts for ScannerX credentials without echoing the password. It
starts the pinned lab, captures each instance, creates a tier-specific ScannerX
target, runs the four scans sequentially, waits for terminal status, and
downloads the raw research export, normalized report, scan record, and activity
log for each run. It also captures challenge snapshots, toolchain information,
and evaluator output. The default output is a timestamped directory under
`runs/experiment-*`. Targets and scan history are retained.

Optional environment settings:

```sh
SCANNERX_API_URL=http://127.0.0.1:8080
SCANNERX_EMAIL=you@example.com
SCANNERX_TARGET_HOST=127.0.0.1
SCANNERX_POLL_SECONDS=10
SCANNERX_SCAN_TIMEOUT_SECONDS=5400
SCANNERX_BACKEND_LOG=/path/to/backend/scannerx.log
SCANNERX_RESEARCH_OUT=/path/to/output
```

Leave `SCANNERX_PASSWORD` unset to enter it at the prompt. If ScannerX runs in a
container, set `SCANNERX_TARGET_HOST` to an address reachable from that
container. The script does not cancel a scan when its wait deadline expires; it
preserves the run as timed out and reports an error. It does not claim
precision, recall, or F1 until ground truth is validated and frozen.

### Baseline reachability troubleshooting

The benchmark Compose file publishes the four lab instances on ports `3001`
through `3004`, bound to `127.0.0.1`. When ScannerX's backend runs directly on
the same host, use `http://127.0.0.1:3001/` for Fast, `:3002/` for Medium,
`:3003/` for Deep, and `:3004/` for repeated Deep. Do not use
`http://juice-shop.com:3000/` for this lab unless that hostname and port are
actually routed to a running instance. A browser or host can resolve a name
that the backend cannot reach; reachability is checked from the backend's
network namespace. If the backend runs in a container, loopback refers to that
container, so configure the lab and backend on a shared Docker network or set
`SCANNERX_TARGET_HOST` to an address reachable from the backend. Confirm the
chosen URL from the backend environment before starting a scan.

## Evaluator

`tools/evaluate.py` reads a frozen `ground_truth.json` and one or more
ScannerX research exports. Each scored instance requires a vulnerability class,
endpoint, and concrete evidence discriminator (`evidenceContains`,
`evidenceAny`, or `evidenceRegex`). Method and parameter are matched when the
ground-truth instance specifies them. Leave `parameter` null for issues such as
missing headers or exposed static resources; the evaluator will not require an
invented parameter or reject a finding because it carries an irrelevant one.
Use distinct ground-truth instance IDs and evidence discriminators to separate
multiple findings at the same endpoint (for example, missing CSP versus missing
HSTS). Findings that match a single instance are deduplicated by that instance
ID, so parameterless issues remain distinct. Ground truth without an evidence
rule cannot produce a match. If ground truth is not validated or run metadata is
absent, metrics are emitted as `null` with an explanation; they are never
inferred from finding counts. Per-finding rows are marked `UNSCORED` until the
ground-truth set is frozen. Version 2 also supports class aliases for known
normalizer taxonomy mistakes and an explicit `scoringScope`; claims outside that
scope are labeled `OUT_OF_SCOPE` and excluded from scoped FP counts.

Example:

```sh
python3 tools/evaluate.py \
  --ground-truth ground_truth.json \
  --matrix challenge_matrix.json \
  --toolchain runs/toolchain.json \
  --runs runs/fast/scannerx-research.json runs/medium/scannerx-research.json runs/deep/scannerx-research.json runs/deep-repeat/scannerx-research.json \
  --out runs/evaluation
```

The evaluator writes `ground_truth.json`, `challenge_matrix.json` (when
provided), `scan_configuration.json`, `metrics.json`, `metrics.csv`, and
`matched_findings.csv`.
Retain original ScannerX exports, logs, and run manifests alongside those
derived files.

## Historical v1 scaffold limitation

The four instance API snapshots and image manifests have been captured and are
identical in challenge count and pinned image reference. In the v1 candidate
matrix, the reflected-XSS challenge was disabled in Docker, so the original
`ground_truth.json` stayed empty and its metrics remained unavailable. The v2
artifacts add a separate frozen, three-check scoring scope as described above;
they do not promote every challenge candidate or claim exhaustive whole-app
ground truth.
