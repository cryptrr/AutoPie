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

## Durable dpkg wrapper protection

Both bootstrap builders use `dpkg_setup.py` to install the wrapper and register
local diversions from `bin/dpkg` to `bin/dpkg.real`. Native dpkg upgrades,
reinstalls and downgrades then replace the backend while preserving the wrapper.
The helper writes dpkg's standard three-line diversion records under the same
POSIX record locks used by apt/dpkg. It does not execute target binaries, so the
same code works for cross-architecture bootstraps and older dpkg versions.

The APK includes the helper and wrapper as separate assets. The embedded Termux
shell environment runs the helper before starting shells, when writing its
environment on app startup, and again after bootstrap installation. This also
repairs prefixes installed by older APKs, even when the bundled bootstrap itself
has not been rebuilt. Busy package-manager locks defer repair until a later
shell/environment setup; errors are logged under `AutoPieDpkgInstaller`.

Migration recognizes an intact legacy wrapper with `dpkg.real`, `dpkg.orig`, or
`dpkg.bin`, a missing wrapper with a surviving backend, and a native dpkg that
has overwritten the wrapper. In the last case the native binary at `bin/dpkg`
is retained as the backend, since it may be newer than the old `dpkg.real`.
An existing backend is backed up as `bin/dpkg.real.autopie-backup` before it is
replaced. Repeated repair is safe. Unknown executables, recursive wrappers and
conflicting diversions are rejected instead of guessed at.

For Android user 0, both `/data/data` and `/data/user/0` diversion names are
registered, and new package paths use `/data/data` consistently. Only dpkg's own
legacy file lists (`info/dpkg.list` or `info/dpkg:*.list`) are normalized to that
spelling, with `.autopie-backup` copies. This prevents an obsolete alias entry
from deleting the newly installed backend during an upgrade. Other packages'
metadata and existing unrelated diversions are preserved. Secondary-user paths
and custom app packages keep their actual root.

### Repair without updating an older APK

Copy the current `dpkg_setup.py` and `dpkg.py` from this directory into a temporary
directory inside AutoPie, then run:

```sh
python dpkg_setup.py --prefix "$PREFIX" --wrapper dpkg.py
dpkg-divert --admindir "$PREFIX/var/lib/dpkg" --truename "$PREFIX/bin/dpkg"
```

The second command should print `$PREFIX/bin/dpkg.real`. Finish any pending
operations with `apt --fix-broken install`. If `dpkg` was held specifically for
the earlier workaround, remove that hold with `apt-mark unhold dpkg` after repair.
The migration deliberately preserves package holds and user data.

### Upgrade regression tests

The normal Python suite covers migration states, lock contention, path aliases
and repeat repair. Optional real dpkg self-upgrade tests run in a **disposable
Linux root environment**. Docker is only a host test option, never an AutoPie
build or runtime dependency:

```sh
docker run --rm --user 0 --network none \
  --mount "type=bind,src=$PWD,dst=/work,readonly" --workdir /work \
  --entrypoint /bin/sh python:3.11-slim-bookworm -c \
  'AUTOPIE_DPKG_INTEGRATION=1 python3 -B -m unittest discover -s scripts/bootstrap -p test_dpkg_upgrade.py -v'
```

The image must already be available locally. Use a newer Debian Python image
to exercise newer dpkg versions as well. Tests install fixture packages only
inside the disposable container, including upgrades from old alias metadata.

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

The new APK's dpkg migration deploys the updated wrapper when the shell
environment is set up. For an older APK, use the standalone repair procedure
above to install the wrapper with the correct package/root and preserve the
native `$PREFIX/bin/dpkg.real`. This prevents recurrence on future installs.

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
