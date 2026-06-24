# AutoPie patches for Termux

These patches contain the AutoPie-specific changes that previously lived as
commits in the `termux-app` submodule fork. They are applied in filename order
by `scripts/prepare-termux-app.sh`.

The series was rebased and verified against official Termux commit
`401bbe54b8f4e68302b1ff70678015a24628fb1d` on 2026-06-22.

The preparation script uses the latest official `master` by default. Pin a
specific revision or use another repository when needed:

```sh
TERMUX_REF=401bbe54b8f4e68302b1ff70678015a24628fb1d \
  ./build_with_termux.sh :app:assembleRelease

TERMUX_REPO=https://github.com/example/termux-app.git TERMUX_REF=my-branch \
  ./scripts/prepare-termux-app.sh
```

If upstream changes conflict with a patch, preparation stops before replacing
the existing generated `termux-app` directory.

Patch 18 embeds the Termux app module as a library and disables its upstream
bootstrap download and native bootstrap build. AutoPie supplies its own
`app/src/main/assets/bootstrap-aarch64.zip`. The native builds in the
`terminal-emulator` and `termux-shared` modules remain enabled because those
libraries are runtime dependencies, not bootstrap packaging scripts.

Patch 19 restores a standalone Gradle task named `downloadAutoPieBootstrap`.
The task downloads Termux's pinned bootstrap archive into the prepared Termux
source without wiring it back into the Termux library build. AutoPie then
patches and copies that archive with `scripts/prepare-termux-bootstrap.sh`.
