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
