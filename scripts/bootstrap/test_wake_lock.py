"""Exercise shell service components through bootstrap, upgrades and repair."""
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


fs = load("wake_fs", "fs-rewriter.py")
dpkg = load("wake_dpkg", "dpkg.py")
setup = load("wake_setup", "dpkg_setup.py")


def script(package, action, service_package="com.termux"):
    return f'''#!/bin/sh
case "${{TERMUX__USER_ID:-}}" in ''|*[!0-9]*|0[0-9]*) TERMUX__USER_ID=0;; esac
am startservice --user "$TERMUX__USER_ID" -a "{package}.service_wake_{action}" \\
    "{package}/{service_package}.app.TermuxService" > /dev/null
'''.encode()


class WakeLockTest(unittest.TestCase):
    def assert_command(self, content, package, action):
        # Resolve against the app's declared service rather than assuming that
        # Java class names change with the Android application ID.
        manifest = ET.parse(Path(__file__).resolve().parents[2] / "app/src/main/AndroidManifest.xml")
        android = "{http://schemas.android.com/apk/res/android}"
        services = {node.get(android + "name") for node in manifest.findall("application/service")}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            command = root / "wake"
            command.write_bytes(content)
            am = root / "am"
            am.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$AM_ARGS"\n')
            am.chmod(0o700)
            for user, expected in (("10", "10"), ("", "0"), ("invalid", "0"), ("01", "0")):
                env = dict(os.environ, PATH=f"{root}:/usr/bin:/bin", AM_ARGS=str(root / "args"), TERMUX__USER_ID=user)
                subprocess.run(["/bin/sh", str(command)], env=env, check=True)
                args = (root / "args").read_text().splitlines()
                self.assertEqual(args[:5], ["startservice", "--user", expected, "-a", f"{package}.service_wake_{action}"])
                owner, service = args[5].split("/")
                self.assertEqual(owner, package)
                self.assertIn(service, services)

    def test_bootstrap_scripts_start_declared_service(self):
        for package in ("com.autopi", "com.pie", "com.example.longer.package"):
            rewrites = fs.build_rewrites("com.termux", package, "/data/user/10")
            for action in ("lock", "unlock"):
                with self.subTest(package=package, action=action):
                    content = fs.rewrite_text_bytes(script("com.termux", action), rewrites)
                    self.assert_command(content, package, action)
                    self.assertEqual(fs.rewrite_text_bytes(content, rewrites), content)

    def test_bootstrap_preserves_class_in_encoded_resources(self):
        rewrites = fs.build_rewrites("com.termux", "com.pie", "/data/user/10")
        for encoding, service in (("utf-8", "com.termux.app.TermuxService"),
                                  ("utf-16le", "com.termux.app.TermuxService"),
                                  ("utf-8", "com/termux/app/TermuxService")):
            with self.subTest(encoding=encoding, service=service):
                original = f"/data/data/com.termux/files/usr {service} com.termux.service_wake_lock"
                expected = f"/data/user/10/com.pie/files/usr {service} com.pie.service_wake_lock"
                self.assertEqual(fs.rewrite_text_bytes(original.encode(encoding), rewrites), expected.encode(encoding))

    def test_termux_tools_package_upgrade(self):
        for root_dir in ("/data/data/com.autopi", "/data/user/10/com.pie"):
            package = root_dir.rsplit("/", 1)[-1]
            with self.subTest(root=root_dir), tempfile.TemporaryDirectory() as temp:
                source, output = Path(temp) / "in.tar.xz", Path(temp) / "out.tar.xz"
                with tarfile.open(source, "w:xz") as archive:
                    for action in ("lock", "unlock"):
                        data = script("com.termux", action)
                        member = tarfile.TarInfo(f"./data/data/com.termux/files/usr/bin/termux-wake-{action}")
                        member.size, member.mode = len(data), 0o700
                        archive.addfile(member, io.BytesIO(data))
                with patch.object(dpkg, "NEW_ROOT_DIR", root_dir), patch.object(dpkg, "NEW_PREFIX", root_dir + "/files/usr"):
                    dpkg.rewrite_tar_paths_xz(str(source), str(output), "./data/data/com.termux/files/usr", "." + root_dir + "/files/usr")
                with tarfile.open(output) as archive:
                    for action in ("lock", "unlock"):
                        self.assert_command(archive.extractfile(f".{root_dir}/files/usr/bin/termux-wake-{action}").read(), package, action)

    def test_apk_update_repairs_existing_scripts_and_preserves_permissions(self):
        for package in ("com.autopi", "com.pie"):
            for old_package, old_service in ((package, package), ("com.termux", "com.termux"), (package, "com.termux")):
                with self.subTest(package=package, old=old_package, service=old_service), tempfile.TemporaryDirectory() as temp:
                    root = Path(temp)
                    (root / "bin").mkdir()
                    (root / "var/lib/dpkg").mkdir(parents=True)
                    (root / "var/lib/dpkg/status").write_text("")
                    native = root / "bin/dpkg"
                    native.write_bytes(b"\x7fELFfixture")
                    native.chmod(0o700)
                    for action in ("lock", "unlock"):
                        path = root / f"bin/termux-wake-{action}"
                        path.write_bytes(script(old_package, action, old_service))
                        path.chmod(0o750)
                    prefix = f"/data/user/10/{package}/files/usr"
                    for _ in range(2):
                        setup.install_wrapper(root, prefix, Path(__file__).with_name("dpkg.py"))
                        for action in ("lock", "unlock"):
                            path = root / f"bin/termux-wake-{action}"
                            self.assert_command(path.read_bytes(), package, action)
                            self.assertEqual(path.stat().st_mode & 0o777, 0o750)


if __name__ == "__main__":
    unittest.main()
