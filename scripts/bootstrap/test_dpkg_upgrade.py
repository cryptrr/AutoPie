"""Real Linux dpkg regression tests; run only in a disposable root container.

AUTOPIE_DPKG_INTEGRATION=1 python3 -B -m unittest discover \
    -s scripts/bootstrap -p test_dpkg_upgrade.py -v
"""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("dpkg_setup", HERE / "dpkg_setup.py")
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)
ENABLED = os.environ.get("AUTOPIE_DPKG_INTEGRATION") == "1" and sys.platform == "linux" and os.geteuid() == 0


@unittest.skipUnless(ENABLED, "requires a disposable Linux root container; see module docstring")
class DpkgUpgradeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        package = "com.autopi-test-" + uuid.uuid4().hex
        Path("/data/data").mkdir(parents=True, exist_ok=True)
        Path("/data/user").mkdir(parents=True, exist_ok=True)
        if not Path("/data/user/0").exists():
            Path("/data/user/0").symlink_to("/data/data", target_is_directory=True)
        self.app = Path("/data/data") / package
        self.addCleanup(lambda: shutil.rmtree(self.app, ignore_errors=True))
        self.prefix = self.app / "files/usr"
        self.alias = Path("/data/user/0") / package / "files/usr"
        self.db = self.prefix / "var/lib/dpkg"
        self.db.mkdir(parents=True)
        (self.db / "status").write_text("")
        self.arch = self.run_command("/usr/bin/dpkg", "--print-architecture").strip()
        self.native = Path("/usr/bin/dpkg").read_bytes()

    def run_command(self, *argv):
        result = subprocess.run([str(a) for a in argv], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result.stdout

    def package(self, version, prefix=None, package="dpkg"):
        prefix = prefix or self.prefix
        build = self.work / (package + "-" + version + "-" + uuid.uuid4().hex)
        (build / "DEBIAN").mkdir(parents=True)
        (build / "DEBIAN/control").write_text(
            f"Package: {package}\nVersion: {version}\nArchitecture: {self.arch}\n"
            "Maintainer: AutoPie Test <test@example.invalid>\nDescription: Disposable upgrade regression fixture\n"
        )
        destination = build / str(prefix).lstrip("/") / "bin"
        destination.mkdir(parents=True)
        if package == "dpkg":
            # A suffix changes the ELF's hash without changing its executable code.
            payload = self.native + ("\nAUTOPIE_TEST_VERSION_" + version + "\n").encode()
            path = destination / "dpkg"
        else:
            payload = b"#!/bin/sh\nprintf 'ordinary package works\\n'\n"
            path = destination / "autopie-fixture"
        path.write_bytes(payload)
        path.chmod(0o755)
        archive = build.with_suffix(".deb")
        self.run_command("dpkg-deb", "--build", build, archive)
        return archive, payload

    def native_install(self, archive, backend="/usr/bin/dpkg"):
        self.run_command(backend, "--admindir=" + str(self.db), "--install", archive)

    def assert_wrapper_works(self, expected_native):
        self.assertEqual((self.prefix / "bin/dpkg").read_bytes(), setup.render_wrapper(HERE / "dpkg.py", str(self.prefix)))
        self.assertEqual((self.prefix / "bin/dpkg.real").read_bytes(), expected_native)
        self.run_command(sys.executable, self.prefix / "bin/dpkg", "--admindir=" + str(self.db), "--version")
        self.run_command(sys.executable, self.prefix / "bin/dpkg", "--admindir=" + str(self.db), "--configure", "--pending")
        for prefix in (self.prefix, self.alias):
            target = self.run_command("dpkg-divert", "--admindir=" + str(self.db), "--truename", prefix / "bin/dpkg").strip()
            self.assertEqual(target, str(prefix / "bin/dpkg.real"))

    def test_upgrade_reinstall_downgrade_and_later_install_keep_wrapper(self):
        first, old_native = self.package("1.0")
        self.native_install(first)
        setup.install_wrapper(self.prefix, str(self.prefix), HERE / "dpkg.py")
        backend = self.prefix / "bin/dpkg.real"
        second, new_native = self.package("2.0")
        for archive, payload in ((second, new_native), (second, new_native), (first, old_native)):
            self.native_install(archive, backend)
            self.assert_wrapper_works(payload)
        ordinary, _ = self.package("1.0", package="autopie-fixture")
        self.native_install(ordinary, backend)
        self.assertEqual(self.run_command(self.prefix / "bin/autopie-fixture"), "ordinary package works\n")
        self.assert_wrapper_works(old_native)

    def test_legacy_alias_metadata_survives_canonical_upgrades(self):
        first, old_native = self.package("1.0", prefix=self.alias)
        self.native_install(first)
        self.assertIn(str(self.alias / "bin/dpkg"), (self.db / "info/dpkg.list").read_text())
        shutil.copy2(self.prefix / "bin/dpkg", self.prefix / "bin/dpkg.real")
        (self.prefix / "bin/dpkg").write_bytes(setup.render_wrapper(HERE / "dpkg.py", str(self.prefix)))
        setup.install_wrapper(self.alias, str(self.alias), HERE / "dpkg.py")
        self.assert_wrapper_works(old_native)
        for version in ("2.0", "3.0"):
            # The new wrapper normalizes packages to this spelling even when
            # launched by an older APK with PIE_ROOT_DIR=/data/user/0/...
            setup.install_wrapper(self.alias, str(self.alias), HERE / "dpkg.py")
            archive, payload = self.package(version)
            self.native_install(archive, self.prefix / "bin/dpkg.real")
            self.assert_wrapper_works(payload)

    def test_already_overwritten_wrapper_keeps_upgraded_backend(self):
        first, old_native = self.package("1.0")
        self.native_install(first)
        shutil.copy2(self.prefix / "bin/dpkg", self.prefix / "bin/dpkg.real")
        second, new_native = self.package("2.0")
        self.native_install(second)
        setup.install_wrapper(self.prefix, str(self.prefix), HERE / "dpkg.py")
        self.assert_wrapper_works(new_native)
        self.assertEqual((self.prefix / "bin/dpkg.real.autopie-backup").read_bytes(), old_native)
        third, newest_native = self.package("3.0")
        self.native_install(third, self.prefix / "bin/dpkg.real")
        self.assert_wrapper_works(newest_native)

    def test_secondary_user_custom_package_upgrade_keeps_wrapper(self):
        self.app = Path("/data/user/10") / ("com.pie-test-" + uuid.uuid4().hex)
        self.prefix = self.app / "files/usr"
        self.alias = self.prefix
        self.db = self.prefix / "var/lib/dpkg"
        self.db.mkdir(parents=True)
        (self.db / "status").write_text("")
        first, _ = self.package("1.0")
        self.native_install(first)
        setup.install_wrapper(self.prefix, str(self.prefix), HERE / "dpkg.py")
        second, payload = self.package("2.0")
        self.native_install(second, self.prefix / "bin/dpkg.real")
        self.assert_wrapper_works(payload)
        self.assertEqual(len(setup.read_diversions(self.db / "diversions")), 1)


if __name__ == "__main__":
    unittest.main()
