"""Fetch the pinned official extractor for bundling, verifying its release digest."""
from pathlib import Path
import hashlib
import urllib.request

VERSION = "2026.10.21"
SHA256 = "b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14"
TARGET = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/justwatch_ytdlp"


def main():
    data = urllib.request.urlopen(
        f"https://github.com/yt-dlp/yt-dlp/releases/download/{VERSION}/yt-dlp", timeout=60
    ).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise RuntimeError("Extractor release SHA-256 mismatch")
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f"Bundled official yt-dlp {VERSION}, SHA-256 verified")


if __name__ == "__main__":
    main()
