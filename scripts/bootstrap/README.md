# Bootstrap rewriting

`prepare-termux-bootstrap.sh` resolves pinned smali/baksmali host tools through
an isolated Gradle project, then disassembles, rewrites and reassembles the DEX
inside `libexec/termux-am/am.apk`. This rebuilds string tables, references, offsets,
and checksums for package names of different lengths. The upstream `bin/am`
launcher is retained, with its package/root paths rewritten and Android 14
read-only DEX handling intact. It does not require the TermuxAm socket server.

Run the regression tests from the repository root:

```sh
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew -p scripts/bootstrap/dex-tools writeClasspath
AUTOPIE_SMALI_CLASSPATH="$(cat scripts/bootstrap/dex-tools/build/classpath.txt)" \
  python3 -m unittest discover -s scripts/bootstrap -p 'test_*.py' -v
```

Build normally, including secondary-user builds:

```sh
./build_with_termux.sh --new-package com.pie --new-root-dir /data/user/10 :app:assembleRelease
```

## Existing installations

An APK update does not replace an already installed Termux prefix. To repair an
existing installation without deleting its packages or app data, extract only
`bin/am` and `libexec/termux-am/am.apk` from the **newly built**
`app/src/main/assets/bootstrap-aarch64.zip`, and copy them to the corresponding
paths under the installation's `$PREFIX`. Both files must come from the same
package/root build. Stop any running `am` invocation first, replace the files,
and set `bin/am` to mode `0700` and `libexec/termux-am/am.apk` to mode `0400`.
The old APK may already be read-only, so replace it rather than writing into it.

Check `am --help` and `am to-uri -a android.intent.action.VIEW`; then test an
actual activity start on the secondary user. The local DEX checks cannot verify
device-specific Android permission or activity-manager behavior.

## Existing keyring links

The package wrapper relocates absolute symlink targets as well as archive
member names. Relative symlink targets stay unchanged; hardlink targets follow
the archive member paths. This keeps `termux-keyring` upgrades from leaving
trusted-key links pointing into the original Termux app's private directory.
The bootstrap's `SYMLINKS.txt` is already relocated by the text rewriter.

For an existing installation with the incorrect keyring links, run this inside
AutoPie. It only retargets existing symlinks into the old Termux keyring directory
when the corresponding key file exists locally; it does not add trusted keys:

```sh
(
  set -eu
  : "${PREFIX:?Run this inside AutoPie}"
  for link in "$PREFIX"/etc/apt/trusted.gpg.d/*.gpg; do
    [ -L "$link" ] || continue
    target=$(readlink "$link")
    case "$target" in
      /data/data/com.termux/files/usr/share/termux-keyring/*)
        key=${target##*/}
        [ -f "$PREFIX/share/termux-keyring/$key" ] || continue
        ln -sfn "../../../share/termux-keyring/$key" "$link"
        ;;
    esac
  done
  apt update
)
```

Also deploy the updated `dpkg.py` wrapper (with its shebang and defaults set for
the installation's package/root) as `$PREFIX/bin/dpkg` to prevent recurrence on
future package installs. Preserve the native `$PREFIX/bin/dpkg.real`. An APK
update alone does not replace the wrapper in an existing prefix.

## Shell home directory

Shell launchers and the environment assets use the current Android app's files
directory plus `/home`, matching the prefix's sibling home directory. Both
bootstrap build paths install `etc/profile.d/00-autopie-home.sh`. This repairs
an inherited `HOME` pointing to the original Termux app, while preserving custom
homes. Its paths are derived from `PREFIX` so secondary Android users work too.
Shell command setup also maps the compiled home path and its subdirectories to
the runtime home spelling, keeping initial `PWD` consistent with `HOME`. The
startup guard corrects an existing shell's logical path only when it is already
in the same directory as `HOME`; other working directories are preserved.

For an existing prefix, copy `scripts/bootstrap/autopie-home.sh` to
`$PREFIX/etc/profile.d/00-autopie-home.sh` and source that file once in the
current shell. New login shells source it through `etc/profile`. An APK update
does not re-extract profile files in an existing prefix.
