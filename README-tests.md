### Shell integration tests

Run the host integration suite with:

```sh
./gradlew :app:testDebugUnitTest --tests com.autopi.ProcessManagerServiceIntegrationTest
```

It requires `/bin/bash` and standard Unix tools (`sort`, `tr`, and `cat`), plus the
normal Android build prerequisites. No emulator, Termux bootstrap, or network
access from the commands under test is needed. Android storage and notification
boundaries are mocked; command use cases, the process manager, generated scripts,
Bash processes, log files, and output files are real. Each test uses temporary
storage and shuts down its shells during cleanup.

Coverage includes simple standalone execution, pipelines and redirection, working
directories containing spaces and quotes, literal input and extra values,
three-stage state and output transfer, failed stages and subsequent fresh runs,
stdin EOF, structured output, lifecycle events, and cancellation. Multi-stage
tests explicitly advance with `firstStepOrSelf()` / `nextStepOrNull()`, matching
the app's staged execution (which may pause for user input). They do not exercise
the Android UI or foreground-service lifecycle. Device process-group termination
is covered separately by `ShellProcessGroupTest` in `androidTest`.

For the actual Android/Termux runtime, use the device integration suite:

```sh
./gradlew :app:installDebug
adb shell am start -n com.autopi/com.termux.app.TermuxActivity
# Wait for the embedded terminal to finish installing the bootstrap and show a prompt.
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.autopi.CommandShellDeviceIntegrationTest
```

Use an unlocked device or emulator supported by the bundled bootstrap. Setup must
be completed in the tested app package (currently **com.autopi**). If you enable
the `.debug` application ID suffix, use `com.autopi.debug` in the launch command
and install its bootstrap separately.
Missing or non-executable bootstrap Bash fails the tests with setup instructions,
rather than skipping them or substituting `/system/bin/sh`.

These tests run the real standalone use case, process manager, generated scripts,
and installed Termux Bash/tools under the Android app's UID. They check simple
commands, both output streams, pipelines, quoted working directories, three-stage
state/output transfer, failure recovery, stdin EOF, and stale-output cleanup.
Only notification delivery and the unrelated internal-config service are mocked.
The suite uses unique command IDs and app-owned temporary storage and cleans up
its shells and files. Stage advancement and final shell cleanup are explicit;
this suite does not automate the UI or test the foreground-service lifecycle.