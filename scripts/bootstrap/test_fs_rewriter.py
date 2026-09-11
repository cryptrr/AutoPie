#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import os
import struct
import subprocess
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("fs-rewriter.py")
SPEC = importlib.util.spec_from_file_location("fs_rewriter", MODULE_PATH)
assert SPEC and SPEC.loader
fs_rewriter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fs_rewriter)


class FsRewriterTest(unittest.TestCase):
    def test_secondary_user_rewrites(self) -> None:
        rewrites = dict(fs_rewriter.build_rewrites("com.termux", "com.pie", "/data/user/10"))
        self.assertEqual(rewrites[b"/data/data/com.termux/files/usr"], b"/data/user/10/com.pie/files/usr")
        self.assertEqual(rewrites[b"/data/user_de/0/com.termux"], b"/data/user_de/10/com.pie")

    def test_missing_tools_fails_without_modifying_apk(self) -> None:
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {}, clear=True):
            apk = Path(temp) / "libexec/termux-am/am.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"original")
            with self.assertRaisesRegex(RuntimeError, "AUTOPIE_SMALI_CLASSPATH"):
                fs_rewriter.rewrite_file(apk, [])
            self.assertEqual(apk.read_bytes(), b"original")

    def test_loose_dex_is_not_rewritten_in_place(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            dex = Path(temp) / "classes.dex"
            original = b"dex\n035\0" + b"\0" * 24 + b"com.termux\0"
            dex.write_bytes(original)
            self.assertFalse(fs_rewriter.rewrite_file(dex, fs_rewriter.build_rewrites("com.termux", "com.pie")))
            self.assertEqual(dex.read_bytes(), original)

    @unittest.skipUnless(os.environ.get("AUTOPIE_SMALI_CLASSPATH"), "requires pinned smali tools")
    def test_reassembled_dex_with_variable_length_packages(self) -> None:
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin/java") if java_home else "java"
        command = [java, "-cp", os.environ["AUTOPIE_SMALI_CLASSPATH"]]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            smali = root / "input"
            smali.mkdir()
            (smali / "Am.smali").write_text('''\
.class public Lcom/termux/termuxam/Am;
.super Ljava/lang/Object;
.field public static final PACKAGE:Ljava/lang/String; = "com.termux"
.method public static main([Ljava/lang/String;)V
    .registers 2
    const-string v0, "com.termux"
    const-string v0, "/data/data/com.termux/files/usr"
    const-string v0, "unicode \\u0000 \\ud83d\\ude00"
    return-void
.end method
''')
            dex = root / "classes.dex"
            subprocess.run(command + ["org.jf.smali.Main", "assemble", str(smali), "-o", str(dex)], check=True)
            original_apk = root / "original.apk"
            with zipfile.ZipFile(original_apk, "w") as archive:
                archive.writestr("classes.dex", dex.read_bytes())
                archive.writestr("AndroidManifest.xml", b"unchanged manifest")
            for package in ("com.autopi", "com.pie", "com.example.longer.package"):
                with self.subTest(package=package):
                    apk = root / package / "libexec/termux-am/am.apk"
                    apk.parent.mkdir(parents=True)
                    apk.write_bytes(original_apk.read_bytes())
                    apk.chmod(0o400)
                    rewrites = fs_rewriter.build_rewrites("com.termux", package, "/data/user/10")
                    self.assertTrue(fs_rewriter.rewrite_file(apk, rewrites, backup=True))
                    self.assertEqual(apk.stat().st_mode & 0o777, 0o400)
                    self.assertEqual(apk.with_name("am.apk.bak").read_bytes(), original_apk.read_bytes())
                    with zipfile.ZipFile(apk) as archive:
                        self.assertEqual(archive.read("AndroidManifest.xml"), b"unchanged manifest")
                        rebuilt = archive.read("classes.dex")
                    self.assertEqual(rebuilt[12:32], hashlib.sha1(rebuilt[32:]).digest())
                    self.assertEqual(struct.unpack_from("<I", rebuilt, 8)[0], zlib.adler32(rebuilt[12:]) & 0xffffffff)
                    # Parse the generated DEX again, including code and references.
                    output = root / package / "decoded"
                    subprocess.run(command + ["org.jf.baksmali.Main", "disassemble", str(apk), "-o", str(output)], check=True)
                    text = next(output.rglob("Am.smali")).read_text()
                    self.assertIn(f'L{package.replace(".", "/")}/termuxam/Am;', text)
                    self.assertIn(f'"{package}"', text)
                    self.assertIn(f'"/data/user/10/{package}/files/usr"', text)
                    self.assertIn(r'"unicode \u0000 \ud83d\ude00"', text)
                    self.assertNotIn("com.termux", text)
                    launcher = fs_rewriter.rewrite_text_bytes(b'exec app_process / com.termux.termuxam.Am "$@"', rewrites)
                    self.assertIn(f"{package}.termuxam.Am".encode(), launcher)


if __name__ == "__main__":
    unittest.main()
