# Python-only runtime and modern Android targets

Analysis date: 2026-10-16. This is a source review and architecture proposal, not an implemented or device-tested migration.

## Conclusion

AutoPie can run Python scripts and Python packages without a Termux userspace. The important change is to embed an Android-built CPython runtime and stop launching interpreters from writable app storage. Reducing the bootstrap to just `python` and `pip` does not fix the current execution path.

Current app configuration is `compileSdk = 35`, `targetSdk = 28`, `minSdk = 27` (`app/build.gradle.kts:13–24`). Google's Android 17 SDK setup currently documents compile/target API 37. Raising the target requires other Android behavior migrations as well as replacing the execution backend.

## Why the existing path fails

Android 10's target-29 behavior blocks direct `execve()` of files in the writable app home directory. `chmod +x` does not remove that restriction. Reading a `.py` file as input to an already loaded interpreter is different from executing an ELF binary.

`ProcessManagerService.kt` currently:

- Defines `usr/bin/bash` as the shell at line 77 and resolves it beneath `filesDir` at lines 271–329.
- Exports a Termux prefix and puts writable directories on PATH at lines 355–380.
- Writes a shell wrapper even for Python jobs at lines 532–548; the wrapper launches `python` at lines 1456–1464, then is itself run through Bash at lines 1516–1520.
- Opens interactive execution and restore sessions using the same writable Bash executable at lines 859–870 and 1067–1072.
- Uses pip console scripts, dpkg, wcurl, shell utilities and PID files for ancillary functions.

`scripts/prepare-termux-bootstrap.sh:14` injects Python, pip, binutils, OpenSSH and sshpass into an existing Termux bootstrap. Changing this list alone retains both the base userspace and the prohibited launch locations. This project already uses an Android/Termux userspace rather than a full conventional Linux distribution; the useful simplification is removing its shell/package-manager dependency.

## Proposed runtime

Use Chaquopy for the first integration because AutoPie is an existing Kotlin/Gradle application. It provides an Android-built interpreter, JNI bridge, and build-time Python dependency packaging. The documented minimum API 24 fits AutoPie's minimum 27. Select Python version and ABI only after checking the required native package matrix. Alternatively, CPython's official Android embeddable artifacts plus a custom JNI bootstrap give more control but require implementing packaging and bridge behavior.

For custom embedding, package `libpython`, Android-built native extensions, and their linked dependencies as application native libraries. Put the standard library and Python modules in assets or private data as appropriate for the chosen runtime. Include TLS libraries and a working certificate strategy. Do not copy the Termux interpreter or expect manylinux/glibc wheels to work against Android's Bionic runtime.

Suggested execution flow:

```text
share / widget / schedule / manual trigger
  -> typed Python execution request
  -> job coordinator
  -> Android-managed worker process containing embedded CPython
  -> script / module / registered Python entry point
  -> streamed output and structured events
  -> existing output, notification, progress and widget UI
```

Use an ordinary private Android service process for the worker, with Binder/Messenger IPC, rather than invoking Bash or forking the JVM. A separate process has its own cwd, environment, sys.argv, imports and streams. One job or one multi-stage session owns a worker slot. Parallel jobs require separate process slots; multiple service instances with the same process name still share that process. A bounded pool of declared process slots is an implementable first design.

Keep the UI and job ownership outside the worker. Attempt cooperative cancellation, then terminate only the owned worker when required, handle Binder death, and create a fresh worker. Plain Python threads cannot reliably stop an arbitrary loop or a blocking native extension. A normal secondary process shares the app UID and is not a security sandbox for untrusted code; `isolatedProcess=true` introduces different access and IPC constraints and is not a drop-in replacement.

## What “Python commands only” should mean

Replace shell strings with a request containing execution kind, script/module/entry point, `argv: List<String>`, environment, cwd and package environment identity. Execute a script with `runpy.run_path`, a module with `runpy.run_module`, or a registered console entry point through `importlib.metadata`. A console script such as `httpx` is a Python callable; its generated executable wrapper is unnecessary. Not every console script has an equivalent `python -m package` implementation.

Reject shell scripts and generic executable recipes explicitly. Do not infer Python support solely from a ZIP signature: arbitrary zip files are not necessarily runnable zipapps, and a Python zipapp may depend on extracted native code or external tools. Existing shiv archives need inspection before migration.

This controls the supported recipe interface. It does not prevent arbitrary Python code from importing `subprocess`, calling `os.system`, or invoking native APIs. Removing those names or filtering strings does not create a sandbox. If “only Python” is intended as a hostile-code security guarantee, that is a separate isolation design.

| Dependency or command | Proposed support |
| --- | --- |
| Python script / pure Python zipapp | Yes, with compatible imports and explicit argv |
| Pure Python wheel and pure Python dependency closure | Build-time; runtime installation subject to installer validation |
| Python console entry point | Invoke its callable in the interpreter |
| Android native Python extension | Package a compatible build and linked libraries with the app |
| manylinux / desktop native wheel | Requires an Android build |
| Source package needing compiler or build subprocesses | Build off-device and distribute compatible artifacts |
| Bash pipeline, pkg/apt/dpkg, executable shell recipe | Requires rewriting or removal |
| Python package calling ffmpeg, ImageMagick, ssh, git, node, etc. | Requires replacement of that functionality; Python packaging alone does not supply it |
| Termux API command wrappers | Replace with explicit Android bridge functions as needed |

For example, a downloader may remain usable for direct downloads while losing merge/transcode operations that launch ffmpeg. Replacing OpenSSH with a Python SSH implementation may be possible but requires checking native cryptography dependencies and feature parity.

## Pip: build-time and runtime are different

Chaquopy's Gradle `pip` block installs dependencies into the application at build time. It does not document a turnkey on-device pip environment. Bundle fixed dependencies this way first, including AutoPie's patched httpx wheel if still required (`AutoPieCoreService.kt:519–522` currently installs it after bootstrap).

Runtime package installation is feasible as a separate engineering task for a restricted pure-Python wheel set. A conservative initial contract is:

1. Accept only compatible universal pure-Python wheels for runtime installation, including all transitive dependencies. `--only-binary=:all:` alone does not enforce pure Python: it also permits native wheels. Inspect wheel metadata and contents, and reject native payloads.
2. Disable sdists, build hooks, VCS/source installs and compiler invocation. Validate the installer does not launch helper interpreters for the supported wheel path.
3. Keep the interpreter and bundled dependencies immutable; install additions into a separate, versioned environment directory. Serialize installation, publish a completed environment atomically, and launch new jobs against it rather than modifying files under running jobs.
4. Dispatch console entry points through metadata rather than executing pip-generated shebang scripts.
5. If including actual pip on-device, run its module as a one-shot command in a dedicated disposable Python host process. Do not call `pip._internal` from the UI or a reusable job interpreter. Pip documents global-state assumptions and expects its process to terminate after use. An Android embedded host must provide and test the module invocation itself; `subprocess([sys.executable, '-m', 'pip', ...])` cannot be assumed to work.

The exact pip/runtime combination needs an Android proof of concept. A purpose-built wheel installer is an alternative, but dependency resolution and full wheel installation semantics must be implemented; simply unzipping packages is insufficient. Dynamically loaded native extensions have additional runtime, ABI and distribution constraints. Bundling curated native dependencies is the recommended initial product contract, not a claim that Android categorically blocks every `dlopen` from app data.

## ProcessManagerService review

Despite its name, this class is not an Android `Service`: it is a Kotlin coordinator with an `Application` and `MainViewModel`. It combines runtime execution, environment/secrets handling, downloads, installation, terminal UI, MCP lifecycle and widget events. Extract a runtime interface, package manager, and event/output handling while retaining the existing UI integration.

Findings from static inspection:

| Priority | Location | Finding / required change |
| --- | --- | --- |
| High | 77, 271–329, 1438–1520 | Python execution still depends on writable Bash and Python binaries. Replace the backend rather than just the bootstrap package list. |
| High | 1458–1464 | `python $exec $command` interpolates shell syntax. Structured argv removes shell parsing and makes Python-only dispatch explicit. |
| High | 835–836, 1506–1513, 612–613, 672 | Interactive scripts enable tracing before exporting extras, including secrets; noninteractive tracing can log expanded command arguments. Remove tracing of secrets and avoid writing secret exports into generated script files. |
| High | 891–911 | Terminal execution reports `CommandCompleted` immediately after service launch. No child exit status is awaited here. Represent launch and completion separately and deliver the actual worker result. |
| Medium | 529, 690, 734–744 | Log closure and local shell cleanup do not run in `finally`; exceptions before normal completion can leave resources open. Coordinate with caller cleanup, but make local ownership exception-safe. |
| Medium | 588–595 | A failed cwd change is logged and execution continues in another directory. Treat invalid cwd as a job setup failure. |
| Medium | 109–140, 702–720 | Cancellation removes and interrupts a shell and emits stopped, while the executing path can still emit failed/completed. Add a single terminal-state transition per job. Cancellation currently bypasses the widget lifecycle dispatcher, which only accepts started/completed/failed. |
| Medium | 967–995, 1136–1146 | Downloader and pip installer interpolate caller input into shell/Python source. Use HTTP/file APIs and typed installer requests. `build_opener()` returns an `OpenerDirector`, which also does not provide the `retrieve` method used at line 973. |
| Medium | 1178–1222 | MCP is backgrounded with a PID file; stop uses an unowned/reusable PID and creates another shell without shutdown. Give MCP its own managed runtime/service lifetime and await actual readiness/exit. |

Keep the structured output/notification/progress event schema at lines 1348–1417. It already reduces reliance on shell state. Deliver it over the bridge or a dedicated IPC stream, with stdout/stderr remaining logs. Preserve legacy line parsing where compatibility is needed.

Multi-stage jobs need explicit session state. The current shell preamble copies `OUTPUT` to `INPUT`, and shell getters/setters expose session variables. Python child changes to `os.environ` cannot update the parent shell. Use coordinator-owned stage input/output and an explicit context object, keeping one worker session where Python module state must persist. Define whether cwd/environment changes persist. Preserve exit/error behavior and ensure exactly one final job result.

`Shell.kt:333–409` already has process-group-aware termination for isolated shells. Preserve that cancellation intent; replacing it with an in-process thread would weaken existing behavior. Do not retain its external `setsid` dependency for the embedded worker model.

## Other modern-target work

- `app/src/main/AndroidManifest.xml:216–218` declares the foreground service without a type, while `ForegroundService.kt:57` calls `startForeground`. Modern targets require selecting a valid service type and its permissions. Runtime services and scheduling must follow background-start rules; Python embedding does not exempt them. Do not label every script dataSync merely to obtain a foreground service; eligible types depend on the actual work. Some types have time limits.
- `requestLegacyExternalStorage` is ignored for apps targeting Android 11+ on Android 11+. Review shared config directories, raw storage paths and file observers. Retained all-files access, if applicable, is a separate permission path; otherwise use SAF/MediaStore and bridge content URIs or staged files into Python.
- Verify every shipped native library, including Python extensions and unrelated dependencies such as MMKV, for 16 KB page compatibility. Test the complete APK/AAB, not only libpython.
- Remove bootstrap startup assumptions in `AutoPieCoreService`, Termux terminal/restore flows, `pkg` recipe metadata and dependency checks. Remove Termux modules only after auditing their Android API integrations and UI callers.

## Migration and validation

1. Inventory existing recipes by scripts, console entry points, native extensions, shell syntax and subprocess requirements. Mark unsupported recipes with a reason.
2. Build a modern-target embedded-runtime spike: script, module, entry point, TLS request, native extension, streamed output, exception, SystemExit and cancellation of an infinite loop. Run on an actual target-29+ configuration to establish the W^X fix.
3. Add worker ownership and bounded parallelism; verify separate cwd/env/imports and cancellation that does not affect another job or UI.
4. Migrate ProcessManagerService to typed requests and explicit stage context while keeping structured event behavior, notifications and widgets.
5. Validate runtime pip separately: pure wheel with dependencies, rejection of sdist/native wheels, failed/offline installation rollback, package-version conflicts and repeated installs in fresh hosts. Ensure no helper interpreter or shell exec occurs on the supported path.
6. Migrate storage, scheduler and foreground-service behavior, remove the bootstrap and obsolete modules, then exercise shares, cron, observer jobs, interactive input and MCP on modern devices including a 16 KB environment.

No app code, SDK setting or dependency was changed for this analysis. No builds or Android runtime tests were run; package compatibility, runtime pip, APK size and performance remain unmeasured.

## Primary references

- [Android 10: execution from app home](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
- [CPython on Android: embedding and packaging](https://docs.python.org/3.15/using/android.html)
- [Chaquopy Gradle integration, dependencies and limitations](https://chaquo.com/chaquopy/doc/current/android.html)
- [Chaquopy: cancellation and separate processes](https://chaquo.com/chaquopy/doc/current/faq.html#interrupt-a-python-call)
- [pip: use from programs and process assumptions](https://pip.pypa.io/en/stable/user_guide/#using-pip-from-your-program)
- [Android 17 SDK setup: API 37](https://developer.android.com/about/versions/17/setup-sdk)
- [Foreground service declaration requirements](https://developer.android.com/develop/background-work/services/fgs/declare)
- [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Android 11 storage changes](https://developer.android.com/about/versions/11/privacy/storage)
- [16 KB native page support](https://developer.android.com/guide/practices/page-sizes)
