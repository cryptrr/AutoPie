# AutoPie security audit

Date: 2026-09-20. Reviewed commit: `f52f1743c7118f36ddc47d663ccc47b817ac4761` (0.19.0-beta, versionCode 70).

**Recommendation: address the two high-priority entry-point flaws before distributing another release.** Shared text can become shell code during environment export, and external applications can bypass the intended command confirmation. Together, these can execute attacker-supplied code with AutoPie's Android UID when a suitable installed single-stage command exists. This grants app-data and granted shared-storage access, not Android root.

## Scope and evidence

Reviewed application Kotlin, source and existing merged manifests, command execution and sharing, browser bridge/cookies, secret storage, config/backup, scheduling, Room queries, dependency declarations, bootstrap preparation, Termux integration boundaries, and release workflow. This is a source audit with focused host-side execution, not a completed device penetration test or exhaustive review of all upstream native code.

- Existing suite: `./gradlew :app:testDebugUnitTest --offline` — **196 tests passed; zero failures, errors, or skips**.
- Host probes invoke the compiled production `toShellExportCommands` and `commandScriptPreamble` helpers, then execute their output with host Bash. Command substitution and secret tracing were reproduced using only synthetic markers. The unquoted-input control stayed safe.
- Android component attacks below are source-validated; they were not executed on a device. Examples are for a disposable test installation with a harmless test recipe.
- Release signing guard: `python3 scripts/check_release_signing.py` — rejected the current release configuration at line 62, as expected.
- A narrow tracked-file scan found no PEM private-key headers, GitHub token patterns, or AWS access-key IDs. This is not a complete secret scan and does not cover Git history.
- The release dependency inventory is recorded separately. Advisory checks were selective, not a complete SCA/SBOM scan. No blanket “dependency clean” conclusion is warranted.
- Application source and configuration were not changed. Only audit artifacts were added under `docs/security`.

Severity reflects actual prerequisites and impact. “Source-confirmed” means the control-flow defect is present; it does not mean a device exploit has been demonstrated.

## Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| SEC-01 | High | Quoted input executes during shell environment export | Production helper reproduced |
| SEC-02 | High | Spoofable caller identity bypasses confirmation | Source-confirmed |
| SEC-03 | High, conditional | Shared-storage config becomes trusted executable code | Requires external config mode and write access |
| SEC-04 | Medium | Shell tracing exposes secret values | Production helper reproduced |
| SEC-05 | Medium | External broadcasts cancel commands and forge notifications | Source-confirmed |
| SEC-06 | Medium | Backup policy includes sensitive shell/browser data | Configuration-confirmed; transport dependent |
| SEC-07 | Medium | Browser bridge accepts continuation data across origins | Requires a pending browser workflow |
| SEC-08 | Medium | Exported viewers read arbitrary paths without size limits | Source-confirmed; device impact untested |
| SEC-09 | Medium | Cookie export retains deleted sessions and widens paths | Source-confirmed |

### SEC-01 — Quoted input executes during environment export (CWE-78)

Evidence: [ProcessManagerService.kt:1430](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:1430), [ProcessManagerService.kt:584](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:584), [RunCommandForText.kt:60](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/use_case/RunCommandForText.kt:60), [ProcessManagerService.kt:836](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:836).

`shellExportValue()` returns a value unchanged if its trimmed form starts and ends with matching quotes. This is not a shell-safety check: double-quoted command substitutions still execute, and internal quote breaks can introduce arbitrary statements. `RunCommandForText` places external text in `INPUT` and `INPUT_TEXT`; `getEnvsFromCommand` passes these values to `toShellExportCommands`, which is executed before the recipe. Interactive mode uses the same unsafe helper.

Harmless payload (the outer double quotes are part of the supplied text):

```text
"$(printf AUTOPIE_AUDIT_MARKER >&2)"
```

The compiled helper emits executable substitution in `export INPUT_TEXT=...`; the host probe observed the marker on stderr even though the recipe itself was only `true`. A user can trigger this by choosing a harmless text recipe from a malicious share. Combined with SEC-02, a malicious app can avoid that confirmation when a qualifying recipe is installed. Browser bridge input can reach the same sink.

**Fix:** always treat environment values as literal data. Remove the “already quoted” shortcut, quote every value exactly once at the final shell boundary, and remove upstream convenience quotes from file/URL values. Validate exported variable names consistently as well. Add regression cases for double quotes, embedded single quotes, substitutions, backticks, newlines, and literal round trips. The existing export test currently expects prequoted strings to pass through; update that expectation.

### SEC-02 — Caller spoofing bypasses command confirmation (CWE-807)

Evidence: [DirectCommandActivity.kt:78](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/DirectCommandActivity.kt:78) and [ShareReceiverViewModel.kt:464](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/presentation/viewModels/ShareReceiverViewModel.kt:464); exported component at [AndroidManifest.xml:171](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/AndroidManifest.xml:171).

The caller is classified from `callingPackage ?: referrer?.authority`, and any string containing `launcher` or equal to `com.autopi` becomes `DIRECT_ICON`. A normal `startActivity` caller can supply `Intent.EXTRA_REFERRER` / `EXTRA_REFERRER_NAME`. Alternatively, an attacker can choose its own package name containing `launcher`. For single-stage commands with no user-facing extras, `selectCommandFromDirectActivity` immediately calls `onCommandClick` instead of showing the external-app confirmation.

Android explicitly documents that [referrer information can be spoofed](https://developer.android.com/reference/android/app/Activity#getReferrer()). Package-name substring checks do not establish launcher authorization either.

A test app can start `com.autopi.DirectCommandActivity`, set `commandId` to a harmless installed recipe, and set `Intent.EXTRA_REFERRER` to `android-app://com.autopi`. The expected secure behavior is a confirmation; the current branch skips it. This source path has not been exercised on an Android device during this audit.

**Fix:** require confirmation for all untrusted external launches. Use an explicit, scoped immutable PendingIntent or another authenticated capability for trusted shortcut execution. Do not use referrer metadata or naming conventions as authentication. Scope caller-supplied `processId` to a new invocation rather than allowing it to select an existing shell, and correlate result events with the invocation instead of every non-cron event.

### SEC-03 — External config can introduce unattended code execution (executable integrity)

Evidence: [AutoPieConfigPathProvider.kt:84](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/preferences/AutoPieConfigPathProvider.kt:84), [JSONService.kt:28](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/JSONService.kt:28), [CronService.kt:77](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/CronService.kt:77).

When users choose external config storage, executable definitions and package files live below shared-storage `AutoSec`. `commands.json` is parsed directly and cron definitions are scheduled on startup/reconciliation without a private trusted copy or approval of modified code. Another app able to write that directory can replace a recipe or add a cron command. On a subsequent startup/config reload, it can execute with AutoPie's permissions and read its secrets and cookie jar.

This is conditional: private app-data storage is the default, and ordinary scoped-storage apps do not automatically have access. The threat applies to legacy/shared-storage writers and apps with broad storage access. The README warns that external storage is less private, but executable integrity and unattended execution deserve separate protection.

**Fix:** keep executable config and binaries private. Treat external files as import/export artifacts; review changes and copy approved definitions into private storage. If external editing is retained, compare against a private approved digest and suspend changed cron/observer jobs until approval.

### SEC-04 — Execution tracing defeats secret confidentiality (CWE-532)

Evidence: [ProcessManagerService.kt:1509](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:1509), [ProcessManagerService.kt:674](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:674), [ProcessManagerService.kt:836](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:836), [ProcessManagerService.kt:403](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:403), [AutoPieApp.kt:74](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/AutoPieApp.kt:74).

Every normal command gets `set -x`; stderr is copied to its cache log. Shell tracing expands variables before printing commands, so a recipe using a password/token in a CLI argument leaks it even if the command never prints the value. Interactive scripts enable tracing before exporting decrypted secrets and also persist their exports in a cache `.sh` file. The host probe used `printf '%s' "$API_TOKEN" >/dev/null` and still observed the synthetic secret in the trace.

Cache files are private by default, so this is not independently an unrestricted cross-app read. Exposure increases through log viewing/sharing, exported viewers, device access, and support bundles. Unconditional `Timber.DebugTree` and optional file logging also retain raw intent/browser data; the legacy HTTP helper logs authorization tokens.

**Fix:** disable xtrace by default, particularly around secret export and secret-consuming commands. Make diagnostics explicit and redact sensitive inputs. Avoid storing decrypted exports in long-lived script files; clean temporary scripts and define log retention. Add a test that runs a secret-consuming command and asserts the secret is absent from stderr/logs.

### SEC-05 — Unprotected broadcast control plane (CWE-862)

Evidence: [AndroidManifest.xml:229](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/AndroidManifest.xml:229), [ProcessBroadcastReceiver.kt:33](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessBroadcastReceiver.kt:33), [AutoPieApp.kt:132](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/AutoPieApp.kt:132), [ProcessManagerService.kt:131](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:131).

`ProcessBroadcastReceiver` is exported without a permission or sender check. It accepts cancellation by ID, cancellation of all processes, stop events, and media notification content. Explicit intents can reach actions absent from its intent filters, including `CANCEL_ALL_PROCESSES`. The process manager handles that event by terminating running shells. The dynamically registered copy also lacks a sender permission/non-exported registration.

Device validation on disposable work can use an explicit broadcast to `com.autopi/.autopieapp.data.services.ProcessBroadcastReceiver` with action `com.autopi.CANCEL_ALL_PROCESSES`. No process ID is needed. `PLAY_MEDIA` separately permits notification spoofing under the app's identity when notification permission is granted. The exported status provider leaks guessed process statuses but not full logs; it is a lower-impact adjacent exposure.

**Fix:** make the manifest receiver non-exported and register the runtime receiver using `ContextCompat.RECEIVER_NOT_EXPORTED`, or enforce a signature permission where a trusted companion requires access. Existing immutable notification PendingIntents can target internal components. Make process-status access capability-scoped or internal.

### SEC-06 — Backup policy fails to exclude sensitive state (CWE-530)

Evidence: [AndroidManifest.xml:46](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/AndroidManifest.xml:46), [backup_rules.xml:8](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/res/xml/backup_rules.xml:8), [data_extraction_rules.xml:7](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/res/xml/data_extraction_rules.xml:7), [ProcessManagerService.kt:89](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/ProcessManagerService.kt:89), [SecretsService.kt:21](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/data/services/SecretsService.kt:21).

Backup is enabled with empty exclusion rules. The private files tree contains the plaintext Netscape cookie jar and a general-purpose Termux home/prefix that can hold SSH keys, history, or application credentials. These remain eligible for applicable backup/device-transfer mechanisms, along with databases/preferences. Keystore-encrypted secret preferences are also eligible, but their key is not portable; restoring ciphertext without its original key can cause decryption failures. `SecretsService.get` does not handle that failure locally.

[Android's backup documentation](https://developer.android.com/identity/data/autobackup) describes default file inclusion, exclusions, and cloud quota behavior. Large bootstrap trees may prevent cloud backup; this does not provide a dependable exclusion policy. Actual backup/transfer behavior depends on Android version, transport, device, and data size. This finding does not assert that backups are publicly readable or always uploaded unencrypted.

**Fix:** allowlist non-sensitive settings instead of backing up the shell tree. Exclude cookie/session files, shell credentials/history, and Keystore-bound secret preferences in both applicable rule formats, or disable backup if unnecessary. Handle missing/invalid keys by clearing unusable ciphertext and requesting secret re-entry. Test cloud and device-transfer paths separately.

### SEC-07 — Browser continuation bridge lacks origin binding (CWE-346)

Evidence: [BrowserActivity.kt:265](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/BrowserActivity.kt:265) and [BrowserActivity.kt:240](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/BrowserActivity.kt:240).

`AutoPieBridge` is exposed to `*`. The listener checks `isMainFrame`, but ignores `sourceOrigin`; a pending next command is neither bound to its originating navigation nor cleared on navigation. After a user starts an asynchronous bridge workflow, the current page can race its message or a new top-level page can supply continuation input. Because `BrowserActivity` is exported and accepts new shared URLs, another app can also navigate it while a continuation is pending.

This is not an always-on arbitrary-command API: a user must first start a qualifying multistage browser command, and the next recipe is already chosen. It nevertheless lets a different page supply privileged workflow input, and SEC-01 can turn a crafted message into shell execution. [WebViewCompat documentation](https://developer.android.com/reference/androidx/webkit/WebViewCompat) explicitly requires treating wildcard-origin messages as untrusted.

**Fix:** bind each pending continuation to its exact HTTPS origin and navigation generation, clear it on navigation/background/cancel/timeout, and check `sourceOrigin`. If the page itself is untrusted, an origin check or a nonce visible to page scripts cannot authenticate which script sent the message; require user review of the result before shell continuation and always preserve literal input at the shell boundary.

### SEC-08 — Exported viewers are unrestricted file-reading deputies (CWE-73 / CWE-400)

Evidence: [AndroidManifest.xml:182](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/AndroidManifest.xml:182), [OutputViewerViewModel.kt:102](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/autopieapp/presentation/viewModels/OutputViewerViewModel.kt:102), [OutputPresentationActivity.kt:82](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/OutputPresentationActivity.kt:82).

External callers control `logFile` / `outputFile`. AutoPie reads these using its own filesystem/content-provider access without confining paths to generated outputs or imposing a byte limit. This can display private app files under a caller-chosen title. It is a UI disclosure, not proof of a direct file-return/exfiltration channel. An attacker can also request a large file or an endless stream such as `/dev/zero`; `readText()` and the log accumulator have no limit and can exhaust app memory, disrupting active work.

**Fix:** make internal viewers non-exported; embedded Termux commands share the app UID and do not need general external access. If external viewing is required, accept only explicitly granted content URIs and verify caller access. For internal file IDs, resolve a server-selected file under an allowed canonical directory, reject symlinks/non-regular files as appropriate, and enforce byte/time/display limits. Avoid full-file string concatenation for streaming logs.

### SEC-09 — Cookie export retains deleted cookies and expands scope

Evidence: [BrowserActivity.kt:330](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/BrowserActivity.kt:330), [BrowserActivity.kt:418](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/BrowserActivity.kt:418), [BrowserActivity.kt:445](/Users/amal/AndroidStudioProjects/AutoPie/app/src/main/java/com/autosec/pie/BrowserActivity.kt:445).

Cookie synchronization returns early for an empty header and preserves all old entries not overwritten by a new entry. Cookies deleted or expired in WebView therefore remain in `cookies.txt`. Every exported cookie is assigned path `/` and expiration `0`; `CookieManager.getCookie` provides name/value pairs rather than sufficient metadata to preserve the original constraints. A cookie from a restricted path can subsequently be sent by shell clients to other paths on the same host.

Impact is conditional on using the jar and on server-side session validity. Server revocation still invalidates a token; this does not resurrect a revoked session. The defect does retain credential material after browser-side deletion and can continue using sessions that were only deleted locally. The jar is in private storage, not external storage.

**Fix:** use an explicit export/clear lifecycle, propagate deletion, preserve metadata via supported WebView APIs where available, and avoid claiming faithful synchronization from a Cookie header. Do not widen path/expiry constraints silently. Test logout, expiration, duplicate names at different paths, and exports across origins.

### SEC-10 — Release signing configuration contradicts release safeguards

Evidence: [build.gradle.kts:62](/Users/amal/AndroidStudioProjects/AutoPie/app/build.gradle.kts:62), [check_release_signing.py:143](/Users/amal/AndroidStudioProjects/AutoPie/scripts/check_release_signing.py:143), [build-release.yml:70](/Users/amal/AndroidStudioProjects/AutoPie/.github/workflows/build-release.yml:70).

`buildTypes.release` explicitly selects the debug signing configuration. The repository's own signing guard detects and rejects this. Ordinary Gradle release builds therefore use the local development certificate rather than the intended release identity. A standard generated debug key is not a universal publicly known private key; takeover requires access to the actual signing key. Nevertheless, development-key handling is unsuitable for distributed releases and can break update identity and release automation.

The GitHub workflow separately expects an unsigned APK and signs it with repository secrets. This audit did not inspect a published APK certificate and does not claim every published release is debug-signed. The source/configuration mismatch should be resolved before relying on that workflow.

**Fix:** remove the release debug-signing assignment, produce the unsigned artifact expected by CI, and fail CI on this configuration before compiling. Verify the final APK certificate digest against the approved release certificate and test the clean tag-release path. An optional local pre-push hook is not a release gate.

## Other risks and hardening work

These are separate from the ten findings above; reachability or deployment evidence is insufficient to classify them as additional confirmed exploits.

- **Archive extraction:** `AutoPieCoreService.extractAutoSecFiles` joins untrusted tar entry names to the destination without canonical containment checks. The bootstrap extractor has similar code. Current top-level invocations appear dormant/commented for these legacy flows, so this is a latent traversal risk, not a proven active remote exploit. Reject traversal, links escaping the root, non-regular special entries, excessive expansion, and oversized archives before reactivating them. Config ZIP restore is better constrained: it reads only `commands.json`, caps it at 20 MiB, and replaces it atomically.
- **Bootstrap authenticity:** the binary-bootstrap extension path downloads unsigned `Release` metadata and can fall back to an unverified package index; package checksums are then trusted from those same downloads. Checksums detect corruption but do not authenticate a compromised mirror. Verify Termux repository signatures against pinned trusted keys and fail closed on verification failures. The release workflow's source-build path is distinct; do not attribute this binary-path weakness to every release.
- **Dependency and CI integrity:** Gradle dependencies have versions but no checked-in verification metadata/locks were found. Actions use mutable major-version tags. Pin action commits, add dependency verification and automated SBOM/advisory scanning, and inventory the embedded native/bootstrap packages as well as JVM dependencies. The wrapper distribution checksum and pinned release container/upstream generator revision are positive controls. Local Termux preparation defaults to `master`; the release workflow explicitly pins its Termux revision.
- **Legacy network code:** `MAIN_API` points to a private-LAN HTTP endpoint and the HTTP helper logs tokens/responses. The cloud-packages view model still references it, but the main UI reachability was not established sufficiently to call it an active production credential leak. Remove it or use authenticated HTTPS and redacted logs before enabling it. The active recipe catalog uses GitHub HTTPS. No permissive TrustManager/hostname-verifier or SSL-error proceed override was found in the reviewed app code.
- **Target SDK 28:** this is a material compatibility/security-maintenance constraint for a Termux-style executable runtime. Plan an architecture-aware upgrade; blindly raising it can break execution from writable app directories. Do not describe this setting alone as a remote exploit. Keep the system WebView maintained.
- **Trust of recipes:** installed recipes intentionally execute arbitrary code under the app UID and can access other recipes' secrets/browser state. The secret provider is non-exported but does not isolate mutually untrusted scripts inside that UID. Explain this trust model during installation, review catalog changes, and consider process/UID isolation if untrusted recipes must be supported. Generic “dangerous command” regexes cannot provide a security sandbox.
- **Resource limits:** enforce byte limits on network manifest/docs fetches, bounds on output rendering and JSON nesting, and cancellation/timeouts. The config ZIP cap does not bound decompression work while skipping arbitrary preceding ZIP entries.

## Positive controls verified

- Secrets use Android Keystore AES-GCM with generated IVs; the secret provider is non-exported.
- Termux RunCommandService and foreground execution services are non-exported; removed Termux file providers are absent from the inspected merged app manifest.
- Browser file/content access is disabled; external URL normalization excludes direct `javascript:`/`file:` loading.
- Notification PendingIntents reviewed use immutable flags.
- Room queries bind parameters rather than concatenating caller strings.
- Cloud install scripts are explicitly disabled; dependencies still require the normal installation trust decision.
- Private config location is the default; ZIP config restore limits the selected entry and uses atomic replacement.

## Dependency assessment limits

Declared versions include Ktor 2.3.7, SnakeYAML 2.2, Commons Compress 1.27.1, Gson 2.13.1, and AndroidX WebKit 1.12.1. The resolved release graph is in the accompanying dependency file. Selective primary-source checks included [Apache's Commons Compress security page](https://commons.apache.org/proper/commons-compress/security.html), [Ktor's advisory page](https://github.com/ktorio/ktor/security/advisories), and [Google security research's SnakeYAML fix confirmation](https://github.com/google/security-research/issues/22). In particular, the old SnakeYAML CVE-2022-1471 was fixed in 2.0 and should not be mechanically reported against this declared 2.2 version.

No complete transitive or native CVE inventory was performed. WebView version, installed Termux packages, runtime pip dependencies, release signing certificate, actual device permissions, OEM backup behavior, and server configuration remain unverified.

## Remediation order and acceptance tests

1. Fix SEC-01 and SEC-02 together. In a disposable device app, send a crafted shared text to a harmless recipe; verify no marker runs. Repeat with forged referrers, a caller package containing `launcher`, shortcuts, and new intents. Confirm legitimate shortcut behavior still works.
2. Disable xtrace around secrets and close exported control/viewer components. Test from a separate UID, including explicit broadcasts, arbitrary file paths, huge streams, and notification PendingIntents.
3. Keep executable configuration private; ensure externally modified scheduled commands cannot start without approval. Audit existing shared configs before migration.
4. Bind browser workflows to a navigation/origin and clear pending state; test hostile navigation while a bridge result is pending and confirm quoted browser data remains literal.
5. Add backup exclusions and cookie lifecycle handling; test restore on another device with no original Keystore key, local cookie deletion, and path restrictions.
6. Repair the release signing pipeline, verify the published certificate, and add supply-chain/SCA gates. Re-audit the resulting release artifact, not only source.

## Reproducing the host probes

Run the unit-test build first. With JDK 17+ and Bash installed, launch `SecurityProbe.java` using the generated app classes JAR and the matching cached Kotlin stdlib JAR:

```text
java --class-path <app-classes.jar>:<kotlin-stdlib.jar> <absolute-path-to-SecurityProbe.java>
```

The probe uses synthetic markers only, sends no network traffic, and calls compiled production helpers rather than a rewritten approximation. It intentionally demonstrates the current insecure behavior; it is audit evidence, not a passing security regression test. Exact observed output is in `probe-results.txt`. Replace it with negative regression assertions when implementing the fixes.
