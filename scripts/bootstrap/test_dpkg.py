#!/usr/bin/env python3
"""Regression coverage for links in relocated package archives."""
import contextlib
import importlib.util
import io
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("dpkg_wrapper", Path(__file__).with_name("dpkg.py"))
wrapper = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(wrapper)


class PackageLinksTest(unittest.TestCase):
    def test_keyring_archive_links_resolve_after_relocation(self):
        old_root = "/data/data/com.termux"
        key_names = (
            "2096779623", "agnostic-apollo", "grimler", "kcubeterm",
            "landfillbaby", "mradityaalok", "termux-autobuilds", "thunder-coding",
        )
        for new_root in ("/data/data/com.autopi", "/data/user/10/com.pie"):
            with self.subTest(root=new_root), tempfile.TemporaryDirectory() as temp:
                source = Path(temp) / "data.tar.xz"
                output = Path(temp) / "rewritten.tar.xz"
                with tarfile.open(source, "w:xz") as archive:
                    for name in key_names:
                        target = f"{old_root}/files/usr/share/termux-keyring/{name}.gpg"
                        key = tarfile.TarInfo("." + target)
                        payload = b"\x00synthetic key bytes\xff"
                        key.size = len(payload)
                        archive.addfile(key, io.BytesIO(payload))
                        link = tarfile.TarInfo(f".{old_root}/files/usr/etc/apt/trusted.gpg.d/{name}.gpg")
                        link.type = tarfile.SYMTYPE
                        link.linkname = target
                        archive.addfile(link)

                    relative = tarfile.TarInfo(f".{old_root}/files/usr/etc/apt/trusted.gpg.d/relative.gpg")
                    relative.type = tarfile.SYMTYPE
                    relative.linkname = "../../../share/termux-keyring/grimler.gpg"
                    archive.addfile(relative)
                    # Hardlinks refer to members, rather than the link's parent.
                    for index, spelling in enumerate(("./data/", "data/", "/data/")):
                        hardlink = tarfile.TarInfo(f".{old_root}/files/usr/share/termux-keyring/hard-{index}.gpg")
                        hardlink.type = tarfile.LNKTYPE
                        hardlink.linkname = spelling + "data/com.termux/files/usr/share/termux-keyring/grimler.gpg"
                        archive.addfile(hardlink)

                with patch.object(wrapper, "OLD_ROOT_DIR", old_root), patch.object(wrapper, "NEW_ROOT_DIR", new_root):
                    with contextlib.redirect_stderr(io.StringIO()):
                        wrapper.rewrite_tar_paths_xz(str(source), str(output), f".{old_root}/files/usr", f".{new_root}/files/usr")

                with tarfile.open(output, "r:xz") as archive:
                    for name in key_names:
                        link = archive.getmember(f".{new_root}/files/usr/etc/apt/trusted.gpg.d/{name}.gpg")
                        self.assertTrue(link.issym())
                        self.assertEqual(link.linkname, f"{new_root}/files/usr/share/termux-keyring/{name}.gpg")
                        self.assertEqual(archive.extractfile("." + link.linkname).read(), payload)
                    self.assertEqual(archive.getmember(relative.name.replace(old_root, new_root)).linkname, relative.linkname)
                    for index, spelling in enumerate((".", "", "/")):
                        expected = ("." + new_root if spelling == "." else new_root.lstrip("/") if spelling == "" else new_root)
                        hardlink = archive.getmember(f".{new_root}/files/usr/share/termux-keyring/hard-{index}.gpg")
                        self.assertTrue(hardlink.islnk())
                        self.assertEqual(hardlink.linkname, expected + "/files/usr/share/termux-keyring/grimler.gpg")

    def test_relative_and_unrelated_symlink_targets_are_unchanged(self):
        for target in ("../share/key.gpg", "data/data/com.termux/key", "/system/bin/sh", "/data/data/com.termux-other/key"):
            with self.subTest(target=target):
                self.assertEqual(wrapper.rewrite_linkname(target, "/data/data/com.termux", "/data/data/com.autopi"), target)


if __name__ == "__main__":
    unittest.main()
