#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("fs-rewriter.py")
SPEC = importlib.util.spec_from_file_location("fs_rewriter", MODULE_PATH)
assert SPEC and SPEC.loader
fs_rewriter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fs_rewriter)


class FsRewriterTest(unittest.TestCase):
    def test_secondary_user_rewrites(self) -> None:
        rewrites = dict(
            fs_rewriter.build_rewrites("com.termux", "com.pie", "/data/user/10")
        )

        self.assertEqual(
            rewrites[b"/data/data/com.termux/files/usr"],
            b"/data/user/10/com.pie/files/usr",
        )
        self.assertEqual(
            rewrites[b"/data/user_de/0/com.termux"],
            b"/data/user_de/10/com.pie",
        )

    def test_termux_am_apk_is_not_corrupted(self) -> None:
        dex = b"dex\n035\0" + b"\0" * 24 + b"Lcom/termux/termuxam/Am;\0"
        rewrites = fs_rewriter.build_rewrites(
            "com.termux", "com.pie", "/data/user/10"
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            apk = Path(temp_dir) / "libexec" / "termux-am" / "am.apk"
            apk.parent.mkdir(parents=True)
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", dex)
            original = apk.read_bytes()

            changed = fs_rewriter.rewrite_file(apk, rewrites)

            self.assertFalse(changed)
            self.assertEqual(apk.read_bytes(), original)

    def test_loose_dex_is_not_rewritten_in_place(self) -> None:
        rewrites = fs_rewriter.build_rewrites("com.termux", "com.pie")

        with tempfile.TemporaryDirectory() as temp_dir:
            dex = Path(temp_dir) / "classes.dex"
            original = b"dex\n035\0" + b"\0" * 24 + b"com.termux\0"
            dex.write_bytes(original)

            self.assertFalse(fs_rewriter.rewrite_file(dex, rewrites))
            self.assertEqual(dex.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
