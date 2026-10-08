#!/usr/bin/env python3
"""Capture pinned Juice Shop image and runtime challenge API metadata."""

import argparse
import datetime as dt
import json
import pathlib
import subprocess
import urllib.request
from urllib.parse import urlsplit


PINNED_IMAGE = "bkimminich/juice-shop@sha256:8739101ade29358abb5469ee66ae78e582c97ed0a5543a4ad102e5fa5193526b"
PLATFORM_MANIFEST_DIGESTS = {
    "amd64": "sha256:7f7539921b046863f2fc48b84061f957e50b3aa4652ae0a62014a2fc25654d0b",
    "arm64": "sha256:f2b4f8284af700c0e8a31d5c2b7f0cc84a027ec410f99d26fb763fba7a7f469b",
}


def docker_json(command, *args):
    result = subprocess.run(command + list(args), check=True, capture_output=True, text=True)
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True, help="Instance base URL, e.g. http://127.0.0.1:3001")
    parser.add_argument("--name", required=True, choices=["fast", "medium", "deep", "deep-repeat"])
    parser.add_argument("--out", required=True, help="Run artifact directory")
    parser.add_argument("--docker-sudo", action="store_true", help="Run Docker commands through sudo (prompts in the terminal if needed)")
    args = parser.parse_args()

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    api_url = args.url.rstrip("/") + "/api/Challenges"
    request = urllib.request.Request(api_url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        api = json.loads(response.read().decode("utf-8"))
        api_status = response.status

    docker = ["sudo", "docker"] if args.docker_sudo else ["docker"]
    parsed_url = urlsplit(args.url)
    if not parsed_url.port:
        raise SystemExit("The instance URL must include its published host port, such as :3001.")
    container_ids = docker_json(docker, "ps", "--filter", f"publish={parsed_url.port}", "--format", "{{.ID}}").splitlines()
    matching_container = None
    image_id = None
    container_config_image = None
    for container_id in container_ids:
        details = json.loads(docker_json(docker, "inspect", container_id))[0]
        host_ports = {
            binding.get("HostPort")
            for bindings in (details.get("NetworkSettings", {}).get("Ports") or {}).values()
            if bindings
            for binding in bindings
        }
        if str(parsed_url.port) in host_ports:
            matching_container = container_id
            image_id = details.get("Image")
            container_config_image = details.get("Config", {}).get("Image")
            break
    if not matching_container:
        raise SystemExit(f"No running Docker container is publishing host port {parsed_url.port}.")
    if container_config_image != PINNED_IMAGE:
        raise SystemExit(
            f"Container {matching_container} was started from {container_config_image!r}, "
            f"not the pinned image reference {PINNED_IMAGE!r}."
        )
    inspect_json = json.loads(docker_json(docker, "image", "inspect", image_id))[0]
    manifest_digests = inspect_json.get("RepoDigests", [])
    expected_digest = PINNED_IMAGE.split("@", 1)[1]
    architecture = inspect_json.get("Architecture", "")
    platform_digest = PLATFORM_MANIFEST_DIGESTS.get(architecture)
    if not any(expected_digest in item or (platform_digest and platform_digest in item) for item in manifest_digests):
        raise SystemExit(
            "The running container image does not report the pinned OCI index or its known "
            f"{architecture or 'unknown-platform'} manifest digest. Reported digests: {manifest_digests}"
        )

    api_path = out / "challenge_api.json"
    api_path.write_text(json.dumps(api, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    manifest = {
        "schemaVersion": "1.0",
        "runName": args.name,
        "capturedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "baseUrl": args.url.rstrip("/"),
        "challengeApiUrl": api_url,
        "challengeApiStatus": api_status,
        "challengeCount": len(api.get("data", [])) if isinstance(api, dict) else len(api),
        "image": PINNED_IMAGE,
        "repoDigests": manifest_digests,
        "imageId": inspect_json.get("Id"),
        "containerId": matching_container,
        "containerImageReference": container_config_image,
        "architecture": architecture,
        "apiSnapshot": api_path.name,
        "scanId": None,
        "scanTier": None,
        "scanStatus": None,
        "scanStartedAt": None,
        "scanCompletedAt": None,
        "scanDurationSeconds": None,
        "toolVersions": None,
        "scanConfiguration": None,
    }
    (out / "instance_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Captured {manifest['challengeCount']} challenge records for {args.name} in {out}")


if __name__ == "__main__":
    main()
