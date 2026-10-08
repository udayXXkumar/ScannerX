import importlib.util
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).parents[1] / "tools" / "evaluate.py"
spec = importlib.util.spec_from_file_location("evaluate", SCRIPT)
evaluate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluate)


class EvaluatorTests(unittest.TestCase):
    def setUp(self):
        self.instance = {
            "instanceId": "xss-search-q",
            "vulnerabilityClass": "Reflected XSS",
            "endpoint": "http://127.0.0.1:3001/rest/products/search",
            "method": "GET",
            "parameter": "q",
            "evidenceContains": ["payload", "reflected"],
            "enabled": True,
            "validated": True,
        }
        self.finding = {
            "findingId": 1,
            "vulnerabilityClass": "reflected xss",
            "endpoint": "http://127.0.0.1:3001/rest/products/search?q=abc",
            "method": "GET",
            "parameter": "q",
            "evidence": "Payload reflected in response",
        }

    def test_match_requires_class_endpoint_method_parameter_and_evidence(self):
        self.assertTrue(evaluate.matches(self.finding, self.instance))
        mutated = dict(self.finding, endpoint="http://127.0.0.1:3001/other?q=abc")
        self.assertFalse(evaluate.matches(mutated, self.instance))
        mutated = dict(self.finding, method="POST")
        self.assertFalse(evaluate.matches(mutated, self.instance))
        mutated = dict(self.finding, parameter="sid")
        self.assertFalse(evaluate.matches(mutated, self.instance))
        mutated = dict(self.finding, evidence="No payload result")
        self.assertFalse(evaluate.matches(mutated, self.instance))

    def test_parameterless_instance_matches_using_its_evidence_discriminator(self):
        instance = {
            "instanceId": "missing-csp-root",
            "vulnerabilityClass": "missing security header",
            "endpoint": "http://127.0.0.1:3001/",
            "method": "GET",
            "parameter": None,
            "evidenceContains": ["content-security-policy", "response headers do not include"],
        }
        finding = {
            "vulnerabilityClass": "Missing Security Header",
            "endpoint": "http://127.0.0.1:3001/",
            "method": "GET",
            "parameter": None,
            "evidence": "Response headers do not include Content-Security-Policy.",
        }
        self.assertTrue(evaluate.matches(finding, instance))

        # A scanner may attach an unrelated parameter to its evidence; it is
        # ignored when this ground-truth weakness has no meaningful parameter.
        with_extra_parameter = dict(finding, parameter="q")
        self.assertTrue(evaluate.matches(with_extra_parameter, instance))
        wrong_header = dict(finding, evidence="Response headers do not include X-Frame-Options.")
        self.assertFalse(evaluate.matches(wrong_header, instance))

    def test_ground_truth_requires_concrete_evidence_rule(self):
        instance_without_evidence = {key: value for key, value in self.instance.items()
                                     if key != "evidenceContains"}
        self.assertFalse(evaluate.matches(self.finding, instance_without_evidence))

    def test_distinct_parameterless_instances_do_not_collapse_as_duplicates(self):
        csp = {
            "instanceId": "missing-csp", "vulnerabilityClass": "missing security header",
            "endpoint": "http://127.0.0.1:3001/", "method": "GET", "parameter": None,
            "evidenceContains": ["content-security-policy"],
        }
        hsts = {
            "instanceId": "missing-hsts", "vulnerabilityClass": "missing security header",
            "endpoint": "http://127.0.0.1:3001/", "method": "GET", "parameter": None,
            "evidenceContains": ["strict-transport-security"],
        }
        findings = [
            {"findingId": 21, "vulnerabilityClass": "missing security header", "endpoint": csp["endpoint"],
             "method": "GET", "parameter": None, "evidence": "Missing Content-Security-Policy response header"},
            {"findingId": 22, "vulnerabilityClass": "missing security header", "endpoint": hsts["endpoint"],
             "method": "GET", "parameter": None, "evidence": "Missing Strict-Transport-Security response header"},
        ]
        run = {"scanStatus": "COMPLETED", "scanId": 12, "scanTier": "fast",
               "scanStartedAt": "2026-01-01T00:00:00", "scanCompletedAt": "2026-01-01T00:00:10",
               "scanDurationSeconds": 10, "findings": findings}
        row = evaluate.metric_row(run, [csp, hsts])
        self.assertEqual(2, row["tp"])
        self.assertEqual(0, row["duplicateCount"])

    def test_unfrozen_metadata_does_not_emit_metrics(self):
        row = evaluate.metric_row({"runName": "fast", "findings": [self.finding]}, [self.instance])
        self.assertIsNone(row["tp"])
        self.assertFalse(row["metricsComplete"])

    def test_unavailable_tool_versions_are_not_marked_complete(self):
        self.assertFalse(evaluate.toolchain_complete({"nuclei": "unavailable: not captured"}))
        self.assertTrue(evaluate.toolchain_complete({
            "nuclei": {"path": "/usr/bin/nuclei", "probe": {"exitCode": 0, "output": "3.4.0"}}
        }))

    def test_unfrozen_ground_truth_labels_findings_unscored(self):
        self.assertEqual("UNSCORED", evaluate.match_status(self.finding, None, set(), False))
        self.assertEqual("FP", evaluate.match_status(self.finding, None, set(), True))

    def test_duplicate_findings_count_once_for_detection(self):
        duplicate = dict(self.finding, findingId=2)
        run = {
            "runName": "deep",
            "scanStatus": "COMPLETED",
            "metadata": {
                "scanId": 10,
                "scanTier": "deep",
                "scanStartedAt": "2026-01-01T00:00:00",
                "scanCompletedAt": "2026-01-01T00:00:10",
                "scanDurationSeconds": 10,
            },
            "findings": [self.finding, duplicate],
        }
        row = evaluate.metric_row(run, [self.instance])
        self.assertEqual(1, row["tp"])
        self.assertEqual(1, row["duplicateCount"])
        self.assertEqual(0, row["fn"])

    def test_unmatched_vulnerability_claim_is_false_positive(self):
        finding = dict(self.finding, endpoint="http://127.0.0.1:3001/other", evidence="payload reflected")
        run = {
            "scanStatus": "COMPLETED",
            "metadata": {
                "scanId": 11,
                "scanTier": "fast",
                "scanStartedAt": "2026-01-01T00:00:00",
                "scanCompletedAt": "2026-01-01T00:00:10",
                "scanDurationSeconds": 10,
            },
            "findings": [finding],
        }
        row = evaluate.metric_row(run, [self.instance])
        self.assertEqual(1, row["fp"])
        self.assertEqual(1, row["fn"])


if __name__ == "__main__":
    unittest.main()
