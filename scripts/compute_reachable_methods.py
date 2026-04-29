#!/usr/bin/env python3
"""
Analyse Android APKs in a folder. Two modes can be used independently or together:

  Analysis mode  — run cg.jar (SPARK call graph) and write reachable methods per APK.
  SDK mode       — extract minSdk/targetSdk/maxSdk from each APK and write a CSV.

Usage (analysis only):
    python3 compute_reachable_methods.py \
        --apks      /path/to/apks \
        --platforms /path/to/android-sdk/platforms \
        --jar       /path/to/cg.jar \
        --output    /path/to/results

Usage (SDK versions only):
    python3 compute_reachable_methods.py \
        --apks        /path/to/apks \
        --sdk-versions sdk_versions.csv

Usage (both):
    python3 compute_reachable_methods.py \
        --apks        /path/to/apks \
        --platforms   /path/to/android-sdk/platforms \
        --jar         /path/to/cg.jar \
        --output      /path/to/results \
        --sdk-versions sdk_versions.csv

SDK mode requires the venv to be active:
    source scripts/.venv/bin/activate   (after running scripts/setup.sh)
"""

import argparse
import csv
import logging
import subprocess
import sys
from pathlib import Path

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    level=logging.INFO,
)
log = logging.getLogger(__name__)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--apks",         required=True, type=Path, help="Folder containing .apk files")

    analysis = parser.add_argument_group("analysis mode")
    analysis.add_argument("--platforms",  type=Path, help="Android SDK platforms directory")
    analysis.add_argument("--jar",        type=Path, help="Path to cg.jar")
    analysis.add_argument("--output",     type=Path, help="Folder where reachable-methods .txt files are written")
    analysis.add_argument("--java",       default="java", help="Java executable (default: java)")
    analysis.add_argument("--heap",       default="32g",  help="JVM max heap size (default: 32g)")
    analysis.add_argument("--timeout",    default=1800, type=int, help="Per-APK timeout in seconds (default: 1800)")

    sdk = parser.add_argument_group("SDK versions mode (requires androguard venv)")
    sdk.add_argument("--sdk-versions",   type=Path, metavar="OUTPUT_CSV",
                     help="Extract minSdk/targetSdk/maxSdk for each APK and write to this CSV file")

    args = parser.parse_args()

    doing_analysis = any([args.platforms, args.jar, args.output])
    if doing_analysis and not all([args.platforms, args.jar, args.output]):
        parser.error("--platforms, --jar, and --output must all be provided together")

    if not doing_analysis and not args.sdk_versions:
        parser.error("at least one mode must be requested: provide "
                     "(--platforms + --jar + --output) and/or --sdk-versions")

    return args


# --- Analysis mode -----------------------------------------------------------

def analyze(apk: Path, args: argparse.Namespace) -> bool:
    """Run cg.jar on a single APK. Returns True on success."""
    output_file = args.output / f"{apk.stem}.txt"

    cmd = [
        args.java,
        f"-Xmx{args.heap}",
        "-jar", str(args.jar),
        "-a",   str(apk),
        "-p",   str(args.platforms),
        "-o",   str(output_file),
    ]

    log.info("Analyzing %s", apk.name)
    try:
        result = subprocess.run(cmd, timeout=args.timeout, capture_output=True, text=True)
        if result.returncode != 0:
            last_err = result.stderr.strip().splitlines()[-1] if result.stderr else "no stderr"
            log.error("Failed  %s: %s", apk.name, last_err)
            return False
        log.info("Done    %s -> %s", apk.name, output_file.name)
        return True
    except subprocess.TimeoutExpired:
        log.error("Timeout %s after %ds", apk.name, args.timeout)
        return False
    except Exception as exc:
        log.error("Error   %s: %s", apk.name, exc)
        return False


def run_analysis(apks: list, args: argparse.Namespace) -> bool:
    args.output.mkdir(parents=True, exist_ok=True)
    succeeded, failed = 0, 0
    for apk in apks:
        if analyze(apk, args):
            succeeded += 1
        else:
            failed += 1
    log.info("Analysis done — %d succeeded, %d failed", succeeded, failed)
    return failed == 0


# --- SDK versions mode -------------------------------------------------------

def _load_androguard():
    try:
        from androguard.core.apk import APK
        return APK
    except ImportError:
        log.error(
            "androguard is not installed. "
            "Run scripts/setup.sh and activate the venv before using --sdk-versions."
        )
        sys.exit(1)


def sdk_info(apk_path: Path, APK):
    """Return (min_sdk, target_sdk, max_sdk) strings, or None on failure."""
    try:
        apk = APK(str(apk_path))
        return (
            apk.get_min_sdk_version() or "",
            apk.get_target_sdk_version() or "",
            apk.get_max_sdk_version() or "",
        )
    except Exception as exc:
        log.error("Could not parse %s: %s", apk_path.name, exc)
        return None


def run_sdk_versions(apks: list, output_csv: Path) -> bool:
    APK = _load_androguard()

    log.info("Extracting SDK versions for %d APK(s)", len(apks))
    rows = []
    failed = 0
    for apk_path in apks:
        info = sdk_info(apk_path, APK)
        if info is None:
            failed += 1
            continue
        min_sdk, target_sdk, max_sdk = info
        rows.append({"apk": apk_path.name, "min_sdk": min_sdk,
                     "target_sdk": target_sdk, "max_sdk": max_sdk})
        log.info("%-40s  min=%-4s  target=%-4s  max=%s",
                 apk_path.name, min_sdk or "?", target_sdk or "?", max_sdk or "?")

    with open(output_csv, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["apk", "min_sdk", "target_sdk", "max_sdk"])
        writer.writeheader()
        writer.writerows(rows)

    log.info("SDK versions written to %s (%d entries, %d failed)", output_csv, len(rows), failed)
    return failed == 0


# --- Entry point -------------------------------------------------------------

def main() -> None:
    args = parse_args()

    if not args.apks.exists():
        log.error("APKs folder not found: %s", args.apks)
        sys.exit(1)

    apks = sorted(args.apks.glob("*.apk"))
    if not apks:
        log.error("No .apk files found in %s", args.apks)
        sys.exit(1)

    log.info("Found %d APK(s) in %s", len(apks), args.apks)

    ok = True

    if args.sdk_versions:
        ok = run_sdk_versions(apks, args.sdk_versions) and ok

    if args.jar:
        for path, label in [(args.platforms, "platforms"), (args.jar, "jar")]:
            if not path.exists():
                log.error("%s not found: %s", label, path)
                sys.exit(1)
        ok = run_analysis(apks, args) and ok

    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
