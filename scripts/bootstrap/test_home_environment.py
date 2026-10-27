"""Exercise the actual shell assets with stale and customized environments."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class HomeEnvironmentTest(unittest.TestCase):
    def test_home_alias_uses_home_spelling_without_moving_other_directories(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            home = root / "files/home"
            home.mkdir(parents=True)
            alias = root / "legacy-home"
            alias.symlink_to(home, target_is_directory=True)
            other = root / "work"
            other.mkdir()
            for cwd, expected in ((alias, home), (other, other)):
                with self.subTest(cwd=cwd):
                    result = subprocess.run(
                        ["bash", "--noprofile", "--norc", "-c",
                         '. "$1"; . "$1"; printf "%s" "$PWD"', "test",
                         str(ROOT / "scripts/bootstrap/autopie-home.sh")],
                        cwd=cwd, env=dict(os.environ, HOME=str(home), PWD=str(cwd)),
                        text=True, capture_output=True, check=True,
                    )
                    self.assertEqual(result.stdout, str(expected))

    def source(self, script, home, prefix, files=None):
        command = '. "$1" >/dev/null; . "$1" >/dev/null; printf "%s" "$HOME"'
        args = ["bash", "--noprofile", "--norc", "-c", command, "test", str(script)]
        if files is not None:
            args[4] = '. "$1" "$2" com.autopi >/dev/null; printf "%s" "$HOME"'
            args.append(files)
        return subprocess.run(args, env=dict(os.environ, HOME=home, PREFIX=prefix),
                              text=True, capture_output=True, check=True).stdout

    def test_legacy_home_is_repaired_for_each_android_user(self):
        for files in ("/data/user/0/com.autopi/files", "/data/user/10/com.pie/files"):
            for home in ("/data/data/com.termux/files/home", "/data/user/0/com.termux/files/home", ""):
                with self.subTest(files=files, home=home):
                    self.assertEqual(self.source(ROOT / "scripts/bootstrap/autopie-home.sh", home, files + "/usr"), files + "/home")

    def test_custom_home_is_preserved(self):
        self.assertEqual(self.source(ROOT / "scripts/bootstrap/autopie-home.sh", "/custom/home", "/data/user/0/com.autopi/files/usr"), "/custom/home")

    def test_environment_assets_set_the_same_home(self):
        for asset in ("env.sh", "env-shell.sh"):
            for files in ("/data/user/0/com.autopi/files", "/data/user/10/com.pie/files", "/tmp/app files"):
                with self.subTest(asset=asset, files=files):
                    self.assertEqual(self.source(ROOT / "app/src/main/assets" / asset,
                        "/data/data/com.termux/files/home", files + "/usr", files), files + "/home")


if __name__ == "__main__":
    unittest.main()
