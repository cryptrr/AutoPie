"""Migration tests for legacy, broken and already protected dpkg installations."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("dpkg_setup.py")
WRAPPER = SCRIPT.with_name("dpkg.py")
SPEC = importlib.util.spec_from_file_location("dpkg_setup", SCRIPT)
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)
NATIVE_OLD = b"\x7fELF" + b"old native dpkg" * 10
NATIVE_NEW = b"\x7fELF" + b"new native dpkg" * 10
LEGACY_WRAPPER = b"#!/old/prefix/bin/python\ndef which_real_dpkg(): pass\ndef rewrite_deb_inplace(): pass\n"


class DpkgSetupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "files/usr"
        (self.root / "bin").mkdir(parents=True)
        self.db = self.root / "var/lib/dpkg"
        self.db.mkdir(parents=True)
        (self.db / "status").write_text("Package: dpkg\nStatus: install ok installed\nVersion: 1.20.0\n")
        self.dpkg = self.root / "bin/dpkg"
        self.real = self.root / "bin/dpkg.real"
        self.prefix = "/data/data/com.autopi/files/usr"

    def executable(self, path, payload):
        path.write_bytes(payload)
        path.chmod(0o700)

    def install(self, prefix=None):
        setup.install_wrapper(self.root, prefix or self.prefix, WRAPPER)

    def assert_protected(self, native=NATIVE_OLD):
        self.assertEqual(self.real.read_bytes(), native)
        self.assertEqual(self.dpkg.read_bytes(), setup.render_wrapper(WRAPPER, self.prefix))
        self.assertEqual(setup.read_diversions(self.db / "diversions"), setup.diversion_pairs(self.prefix))

    def test_fresh_bootstrap_registers_both_primary_user_aliases(self):
        self.executable(self.dpkg, NATIVE_OLD)
        self.install("/data/user/0/com.autopi/files/usr")
        self.assert_protected()

    def test_legacy_wrapper_keeps_native_and_migrates_only_dpkg_ownership(self):
        self.executable(self.dpkg, LEGACY_WRAPPER)
        self.executable(self.real, NATIVE_OLD)
        (self.db / "info").mkdir()
        metadata = self.db / "info/dpkg.list"
        metadata.write_text("/data/user/0/com.autopi/files/usr/bin/dpkg\n")
        other = self.db / "info/another-package.list"
        other.write_text("/data/user/0/com.autopi/files/usr/bin/tool\n")
        self.install()
        self.assert_protected()
        self.assertEqual(metadata.read_text(), "/data/data/com.autopi/files/usr/bin/dpkg\n")
        self.assertEqual(metadata.with_name("dpkg.list.autopie-backup").read_text(), "/data/user/0/com.autopi/files/usr/bin/dpkg\n")
        self.assertEqual(other.read_text(), "/data/user/0/com.autopi/files/usr/bin/tool\n")

    def test_overwritten_wrapper_preserves_newer_native_binary(self):
        self.executable(self.dpkg, NATIVE_NEW)
        self.executable(self.real, NATIVE_OLD)
        self.install()
        self.assert_protected(NATIVE_NEW)
        self.assertEqual((self.root / "bin/dpkg.real.autopie-backup").read_bytes(), NATIVE_OLD)

    def test_missing_wrapper_recovers_without_losing_backend(self):
        self.executable(self.real, NATIVE_OLD)
        self.install()
        self.assert_protected()

    def test_legacy_orig_and_bin_backend_names_are_supported(self):
        for name in ("dpkg.orig", "dpkg.bin"):
            with self.subTest(name=name):
                self.executable(self.dpkg, LEGACY_WRAPPER)
                self.real.unlink(missing_ok=True)
                candidate = self.root / "bin" / name
                self.executable(candidate, NATIVE_OLD)
                self.install()
                self.assert_protected()
                self.assertEqual(candidate.read_bytes(), NATIVE_OLD)
                candidate.unlink()

    def test_repeat_migration_does_not_rewrite_or_duplicate_files(self):
        self.executable(self.dpkg, NATIVE_OLD)
        self.install()
        paths = (self.dpkg, self.real, self.db / "diversions")
        timestamps = [p.stat().st_mtime_ns for p in paths]
        self.install()
        self.assert_protected()
        self.assertEqual([p.stat().st_mtime_ns for p in paths], timestamps)

    def test_secondary_user_and_custom_package_rendering(self):
        self.executable(self.dpkg, NATIVE_OLD)
        prefix = "/data/user/10/com.pie/files/usr"
        self.install(prefix)
        self.assertEqual(setup.read_diversions(self.db / "diversions"), [(prefix + "/bin/dpkg", prefix + "/bin/dpkg.real", ":")])
        source = self.dpkg.read_text()
        self.assertTrue(source.startswith("#!" + prefix + "/bin/env python\n"))
        self.assertIn('"/data/user/10/com.pie"', source)

    def test_unrelated_diversions_are_preserved(self):
        self.executable(self.dpkg, NATIVE_OLD)
        foreign = ("/some/tool", "/some/tool.real", "another-package")
        (self.db / "diversions").write_text("\n".join(foreign) + "\n")
        self.install()
        self.assertEqual(setup.read_diversions(self.db / "diversions"), [foreign] + setup.diversion_pairs(self.prefix))
        self.assertEqual((self.db / "diversions-old").read_text(), "\n".join(foreign) + "\n")

    def test_conflicting_or_malformed_database_is_left_untouched(self):
        self.executable(self.dpkg, NATIVE_NEW)
        self.executable(self.real, NATIVE_OLD)
        for contents in (self.prefix + "/bin/dpkg\n/other/dpkg\nforeign-package\n", "incomplete\n"):
            with self.subTest(contents=contents):
                (self.db / "diversions").write_text(contents)
                with self.assertRaises(setup.SetupError):
                    self.install()
                self.assertEqual(self.dpkg.read_bytes(), NATIVE_NEW)
                self.assertEqual(self.real.read_bytes(), NATIVE_OLD)
                self.assertEqual((self.db / "diversions").read_text(), contents)

    def test_unknown_wrapper_and_recursive_backend_are_rejected(self):
        for contents in (b"#!/bin/sh\necho custom wrapper\n", LEGACY_WRAPPER):
            self.executable(self.dpkg, contents)
            self.executable(self.real, LEGACY_WRAPPER)
            with self.assertRaises(setup.SetupError):
                self.install()
            self.assertEqual(self.dpkg.read_bytes(), contents)
            self.assertFalse((self.db / "diversions").exists())

    def test_busy_apt_or_dpkg_defers_without_replacing_files(self):
        self.executable(self.dpkg, NATIVE_OLD)
        for lockname in ("lock-frontend", "lock"):
            with self.subTest(lock=lockname):
                holder = subprocess.Popen([sys.executable, "-c",
                    "import fcntl,sys; f=open(sys.argv[1],'a+b'); "
                    "fcntl.lockf(f,fcntl.LOCK_EX); print('ready',flush=True); sys.stdin.read()",
                    str(self.db / lockname)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
                try:
                    self.assertEqual(holder.stdout.readline().strip(), "ready")
                    with self.assertRaises(setup.PackageManagerBusy):
                        self.install()
                    result = subprocess.run([sys.executable, str(SCRIPT), "--prefix", str(self.root), "--wrapper", str(WRAPPER)], capture_output=True)
                    self.assertEqual(result.returncode, 75, result.stderr)
                    self.assertEqual(self.dpkg.read_bytes(), NATIVE_OLD)
                    self.assertFalse(self.real.exists())
                finally:
                    holder.communicate(timeout=5)

    def test_new_wrapper_normalizes_root_from_older_launchers(self):
        result = subprocess.run([sys.executable, "-c",
            "import runpy,sys; m=runpy.run_path(sys.argv[1]); print(m['NEW_PREFIX']); print(m['NEW_ROOT_DIR'])",
            str(WRAPPER)], env=dict(os.environ, PIE_PREFIX="/data/user/0/com.autopi/files/usr", PIE_ROOT_DIR="/data/user/0/com.autopi"),
            capture_output=True, text=True, check=True)
        self.assertEqual(result.stdout.splitlines(), [self.prefix, "/data/data/com.autopi"])


if __name__ == "__main__":
    unittest.main()
