#!/usr/bin/env python3
"""Build a version-specific Juice Shop challenge classification matrix.

Requires PyYAML (`python3 -m pip install -r requirements.txt`). The generated
matrix is a review queue, not ground truth until actual instance state and
challenge behavior are validated.
"""

import argparse
import csv
import hashlib
import json
import pathlib
import urllib.request

import yaml


VERSION = "v20.2.0"
SOURCE = f"https://raw.githubusercontent.com/juice-shop/juice-shop/{VERSION}/data/static/challenges.yml"

# Challenge keys are classified from the official task description. These are
# candidates for manual review; only a validated endpoint-level instance can be
# promoted into ground_truth.json.
CLASS_BY_KEY = {
    # Clear externally testable injection / XSS / file / endpoint candidates.
    "reflectedXssChallenge": ("A", "Reflected XSS", False),
    "directoryListingChallenge": ("B", "Directory listing / sensitive file exposure", False),
    "forgottenDevBackupChallenge": ("B", "Sensitive backup exposure", False),
    "forgottenBackupChallenge": ("B", "Sensitive backup exposure", False),
    "accessLogDisclosureChallenge": ("B", "Sensitive file exposure", False),
    "misplacedSignatureFileChallenge": ("B", "Sensitive file exposure", False),
    "retrieveBlueprintChallenge": ("B", "Sensitive file exposure", False),
    "extraLanguageChallenge": ("B", "Unexpected static resource exposure", False),
    "misplacedIacFiles": ("B", "Configuration/source file exposure", False),
    "exposedMetricsChallenge": ("B", "Exposed monitoring endpoint", False),
    "leakedApiKeyChallenge": ("B", "Client-side secret disclosure", False),
    "exposedCredentialsChallenge": ("B", "Client-side credential disclosure", False),
    "knownVulnerableComponentChallenge": ("B", "Known vulnerable component fingerprint", False),
    "restfulXssChallenge": ("B", "API/persisted XSS", False),
    "localXssChallenge": ("B", "DOM XSS", False),
    "usernameXssChallenge": ("B", "Stored/header XSS and CSP behavior", False),
    "persistedXssUserChallenge": ("B", "Persisted XSS", False),
    "persistedXssFeedbackChallenge": ("B", "Persisted XSS", False),
    "httpHeaderXssChallenge": ("B", "Header-based XSS", False),
    "videoXssChallenge": ("B", "Stored XSS", False),
    "svgInjectionChallenge": ("B", "HTML/SVG injection", False),
    "dbSchemaChallenge": ("B", "SQL injection", False),
    "loginAdminChallenge": ("B", "SQL injection / authentication bypass", False),
    "loginBenderChallenge": ("B", "SQL injection / authentication bypass", False),
    "loginJimChallenge": ("B", "SQL injection / authentication bypass", False),
    "loginRapperChallenge": ("B", "SQL injection / authentication bypass", True),
    "unionSqlInjectionChallenge": ("B", "SQL injection", True),
    "noSqlCommandChallenge": ("B", "NoSQL injection", False),
    "noSqlOrdersChallenge": ("B", "NoSQL injection / data exposure", True),
    "noSqlReviewsChallenge": ("B", "NoSQL injection / unauthorized modification", True),
    "ssrfChallenge": ("B", "SSRF", False),
    "redirectChallenge": ("B", "Open redirect", False),
    "redirectCryptoCurrencyChallenge": ("B", "Open redirect", False),
    "xxeFileDisclosureChallenge": ("B", "XXE / local file disclosure", False),
    "xxeDosChallenge": ("B", "XXE / resource exhaustion", False),
    "lfrChallenge": ("B", "Local file read / path traversal", False),
    "nullByteChallenge": ("B", "Path traversal / input validation", False),
    "sstiChallenge": ("B", "Server-side template/command injection", False),
    "rceChallenge": ("B", "Insecure deserialization / RCE", False),
    "rceOccupyChallenge": ("B", "Insecure deserialization / RCE", False),
    "yamlBombChallenge": ("B", "Unsafe deserialization / resource exhaustion", False),
    "errorHandlingChallenge": ("B", "Error disclosure", False),
    "deprecatedInterfaceChallenge": ("B", "Exposed deprecated interface", False),
    "weakPasswordChallenge": ("B", "Weak authentication controls", True),
    "captchaBypassChallenge": ("B", "Anti-automation control", False),
    "registerAdminChallenge": ("B", "Privilege assignment / mass assignment", False),
    "csrfChallenge": ("B", "CSRF", True),
    "jwtUnsignedChallenge": ("B", "JWT validation / authentication bypass", False),
    "jwtForgedChallenge": ("B", "JWT signature validation / authentication bypass", False),
    "basketAccessChallenge": ("B", "IDOR / authorization", True),
    "basketManipulateChallenge": ("B", "IDOR / authorization", True),
    "dataExportChallenge": ("B", "IDOR / sensitive data exposure", True),
    "adminSectionChallenge": ("B", "Broken access control", False),
    "emailLeakChallenge": ("B", "Cross-domain data disclosure", True),
    "passwordHashLeakChallenge": ("B", "Authenticated API data exposure", True),
    "uploadTypeChallenge": ("B", "File upload validation", True),
    "uploadSizeChallenge": ("B", "File upload validation", True),
    "freeDeluxeChallenge": ("B", "Business logic / authorization", True),
    "ghostLoginChallenge": ("B", "Authentication state handling", False),
    "twoFactorAuthUnsafeSecretStorageChallenge": ("B", "2FA/session security", True),
    "exposedMetricsChallenge": ("B", "Exposed monitoring endpoint", False),
}

NOT_DAST = {
    "tokenSaleChallenge", "easterEggLevelOneChallenge", "easterEggLevelTwoChallenge",
    "continueCodeChallenge", "privacyPolicyProofChallenge", "hiddenImageChallenge",
    "dlpPasswordSprayingChallenge", "dlpPastebinDataLeakChallenge",
    "typosquattingAngularChallenge", "typosquattingNpmChallenge",
    "supplyChainAttackChallenge", "csafChallenge", "weirdCryptoChallenge",
    "privacyPolicyChallenge", "securityPolicyChallenge", "closeNotificationsChallenge",
    "chatbotPromptInjectionChallenge", "chatbotGreedyInjectionChallenge",
    "systemPromptExtractionChallenge", "changeProductChallenge", "nftMintChallenge",
    "nftUnlockChallenge", "web3WalletChallenge", "web3SandboxChallenge",
    "forgedCouponChallenge", "premiumPaywallChallenge", "negativeOrderChallenge",
    "manipulateClockChallenge", "christmasSpecialChallenge", "zeroStarsChallenge",
    "feedbackChallenge", "forgedFeedbackChallenge", "forgedReviewChallenge",
    "changePasswordBenderChallenge", "resetPasswordBjoernOwaspChallenge",
    "resetPasswordBenderChallenge", "resetPasswordBjoernChallenge",
    "resetPasswordJimChallenge", "resetPasswordMortyChallenge",
    "resetPasswordUvoginChallenge", "geoStalkingMetaChallenge",
    "geoStalkingVisualChallenge", "loginAmyChallenge", "loginSupportChallenge",
    "oauthUserPasswordChallenge", "loginJimChallenge", "loginBenderChallenge",
    "ephemeralAccountantChallenge", "timingAttackChallenge", "passwordRepeatChallenge",
    "missingEncodingChallenge", "aiDebuggingChallenge", "nftUnlockChallenge",
}

CLASS_DESCRIPTIONS = {
    "A": "Conventional DAST can plausibly detect the externally observable issue without credentials; validate endpoint and behavior before scoring.",
    "B": "A specialized active check, endpoint-specific interaction, or stateful validation may be required; not automatically in the score set.",
    "C": "Primarily manual, authenticated, multi-step, business-logic, or human-interaction challenge; exclude from unauthenticated DAST false negatives.",
    "D": "Not an externally testable vulnerability instance suitable for this DAST ground truth (for example OSINT, CTF meta, reporting, or source/cryptographic reasoning).",
}


def load_catalogue(source_path=None):
    if source_path:
        raw = pathlib.Path(source_path).read_bytes()
    else:
        with urllib.request.urlopen(SOURCE, timeout=30) as response:
            raw = response.read()
    return raw, yaml.safe_load(raw)


def classify(row):
    key = row.get("key", "")
    if key in NOT_DAST:
        return "D", "Not suitable for external vulnerability scoring", False, False
    if key in CLASS_BY_KEY:
        group, vulnerability_class, auth_required = CLASS_BY_KEY[key]
        eligible_candidate = group == "A" and not auth_required
        return group, vulnerability_class, auth_required, eligible_candidate
    return "C", row.get("category", "Manual/application-specific challenge"), None, False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", help="Use a local challenges.yml instead of downloading the tag")
    parser.add_argument("--api-snapshot", help="Captured /api/Challenges JSON for the actual lab")
    parser.add_argument("--output", default="challenge_matrix.json")
    parser.add_argument("--csv-output", help="CSV path (defaults to the JSON path with .csv)")
    args = parser.parse_args()
    raw, rows = load_catalogue(args.source)
    api_challenges = None
    if args.api_snapshot:
        api_payload = json.loads(pathlib.Path(args.api_snapshot).read_text(encoding="utf-8"))
        api_challenges = api_payload.get("data", api_payload) if isinstance(api_payload, dict) else api_payload
        if not isinstance(api_challenges, list):
            raise SystemExit("Challenge API snapshot must contain a list or a data list.")
        api_by_key = {item.get("key"): item for item in api_challenges if isinstance(item, dict) and item.get("key")}
    else:
        api_by_key = {}
    matrix = []
    for row in rows:
        group, vuln_class, auth_required, candidate = classify(row)
        disabled = row.get("disabledEnv") or []
        if isinstance(disabled, str):
            disabled = [disabled]
        runtime_row = api_by_key.get(row.get("key"))
        runtime_disabled = (runtime_row or {}).get("disabledEnv") or disabled
        if isinstance(runtime_disabled, str):
            runtime_disabled = [runtime_disabled]
        instance_enabled = None
        if runtime_row is not None:
            instance_enabled = not any(str(item).lower() in ("docker", "all") for item in runtime_disabled)
        matrix.append({
            "challengeId": row.get("key"),
            "challengeName": row.get("name"),
            "category": row.get("category"),
            "vulnerabilityClass": vuln_class,
            "detectionType": group,
            "detectionTypeDescription": CLASS_DESCRIPTIONS[group],
            "blackBoxDetectable": group in ("A", "B"),
            "expectedScannerEvidence": "Requires endpoint-level review from the official v20.2.0 challenge behavior; not inferred from challenge name alone.",
            "requiredHttpInteraction": "Not established from catalogue metadata; fill from controlled endpoint validation.",
            "authenticationRequired": auth_required,
            "specialPrerequisites": row.get("description"),
            "disabledEnvironments": disabled,
            "instanceEnabled": instance_enabled,
            "scoringCandidate": candidate and instance_enabled is True,
            "suitableForTpFpFn": False,
            "scannerXCurrentCapability": "Unassessed pending endpoint validation",
            "scannerXStatus": "Not run against pinned lab",
            "notes": "Challenge-level classification only. Promote to a scored vulnerability instance only after checking the captured instance API and observing reproducible external evidence.",
            "officialDescription": row.get("description"),
            "difficulty": row.get("difficulty"),
            "tags": row.get("tags") or [],
        })
    result = {
        "schemaVersion": "1.0",
        "juiceShopVersion": VERSION,
        "sourceUrl": SOURCE,
        "sourceSha256": hashlib.sha256(raw).hexdigest(),
        "classificationPolicy": "unauthenticated external DAST; A is conventional, B specialized, C manual/application logic, D unsuitable",
        "instanceSnapshot": args.api_snapshot,
        "groundTruthFrozen": False,
        "challengeCount": len(matrix),
        "challenges": matrix,
        "vulnerabilityInstances": [],
        "limitations": [
            "The catalogue does not establish runtime challenge enablement or exact HTTP behavior.",
            "A/B classifications are review candidates, not scanner true-positive labels.",
            "Only validated endpoint-level instances with captured HTTP evidence may enter the scoring oracle."
        ]
    }
    output_path = pathlib.Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    csv_path = pathlib.Path(args.csv_output) if args.csv_output else output_path.with_suffix(".csv")
    with csv_path.open("w", newline="", encoding="utf-8") as stream:
        fields = list(matrix[0].keys()) if matrix else []
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in matrix:
            writer.writerow({key: json.dumps(value, ensure_ascii=False) if isinstance(value, (list, dict)) else value for key, value in row.items()})
    print(f"Wrote {args.output}: {len(matrix)} challenges; sha256={result['sourceSha256']}")


if __name__ == "__main__":
    main()
