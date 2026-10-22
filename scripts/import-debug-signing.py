#!/usr/bin/env python3
"""Import the existing shared Debug key without generating or replacing its identity."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("keystore", type=Path, help="Shared debug.keystore transferred from the configured Mac")
    args = parser.parse_args()
    source = args.keystore.expanduser().resolve()
    if not source.is_file():
        parser.error("Keystore file does not exist: " + str(source))

    candidates = [ROOT / ".tooling/jdk/bin/keytool"]
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]) / "bin/keytool")
    system_keytool = shutil.which("keytool")
    if system_keytool:
        candidates.append(Path(system_keytool))
    keytool = next((p for p in candidates if p.is_file()), None)
    if keytool is None:
        parser.error("JDK required. Configure JAVA_HOME or install this project's .tooling/jdk first.")

    # Read once: the bytes checked here are exactly those written to the project.
    payload = source.read_bytes()
    with tempfile.TemporaryDirectory(prefix="ridediary-signing-") as temporary:
        staged = Path(temporary) / "debug.keystore"
        staged.write_bytes(payload)
        staged.chmod(0o600)
        result = subprocess.run([
            str(keytool), "-exportcert", "-keystore", str(staged),
            "-alias", "androiddebugkey", "-storepass", "android",
        ], capture_output=True)
    if result.returncode != 0:
        parser.error("Cannot read androiddebugkey with the standard Debug password. Import the original shared Debug keystore; configure a working JDK if needed.")
    actual = hashlib.sha256(result.stdout).hexdigest()
    expected = (ROOT / "signing/debug-certificate.sha256").read_text().strip().lower()
    if actual != expected:
        parser.error("Wrong signing certificate. Obtain the existing shared key from the configured Mac. No project key was changed.")

    destination = ROOT / ".signing/debug.keystore"
    destination.parent.mkdir(mode=0o700, exist_ok=True)
    destination.parent.chmod(0o700)
    fd, temporary = tempfile.mkstemp(prefix=".import-", dir=str(destination.parent))
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(payload)
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print("Imported shared Debug key: " + str(destination))
    print("Certificate SHA-256: " + actual)


if __name__ == "__main__":
    try:
        main()
    except OSError as error:
        sys.exit(str(error))
