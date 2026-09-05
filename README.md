# AutoPie

AutoPie is a command hub and workflow runner for Android. It gives shell commands and Python scripts a friendly UI, runs them inside an embedded Termux environment, and lets you trigger them manually, from Android's share sheet, when files appear, on a schedule, from a home-screen shortcut, or from another app.

### [Download the latest APK](https://github.com/cryptrr/AutoPie/releases/)

> AutoPie currently supports aarch64/arm64-v8a devices (most newer Android phones).

<div style="display:flex;flex-direction:row;justify-content:space-between">
<img src="https://github.com/user-attachments/assets/ff9d86db-fc71-45e6-bbe6-9891ab5af35c" alt="AutoPie screenshot" width="47%" height="auto">
<img src="https://github.com/user-attachments/assets/1e996b5f-02e5-46bd-9ff4-78bb886bd410" alt="AutoPie feature demo" width="47%" height="auto">
</div>

[<video src="https://cryptrr.github.io/AutoPie/fastlane/metadata/android/en-US/autopie-feature-demo1.mp4" width="47%" height="auto"></video>](https://github.com/user-attachments/assets/4b51312f-fb4a-4d7c-9318-0ffd1aed2dfb)

## What AutoPie supports

- Bash commands, shell scripts, inline Python, Python packages, and Termux packages.
- Multistage workflows with a persistent shell, per-step working directories and inputs, automatic step-to-step values, and reusable command steps.
- Share-sheet commands for text, URLs, one or many files, and directories.
- File observers with regular-expression filename filters.
- Periodic commands through Android WorkManager.
- Manual runs from the command hub and pinned home-screen shortcuts.
- Calls from other Android apps through an explicit intent, with asynchronous or final result reporting.
- Rich command inputs: strings, booleans, single-select, multi-select, flags, sliders, passwords/secrets, and file pickers.
- Conditional inputs, persistent internal configuration, environment-backed options, and realtime controls.
- A searchable command catalog with command documentation, install scripts, versions, and updates.
- Per-command history, process logs, success/failure notifications, command search, cloning, and editing.
- A full embedded Termux terminal. Install more software with `pkg` or `pip`.
- Config storage in shared external storage or AutoPie's private app-data home.

## Installation

1. Download an APK from [Releases](https://github.com/cryptrr/AutoPie/releases/) or build it from source.
2. Install it and accept any Play Protect prompt.
3. Open AutoPie and wait for the embedded Termux bootstrap to finish installing.
4. Grant the requested storage and notification permissions.
5. If scheduled or background workflows are important, disable battery optimization for AutoPie.

If `pkg install` fails immediately after the first launch, reopen the terminal and give the bootstrap a little more time to finish.

## Quick start

1. Open AutoPie and press the add button.
2. Choose **Share**, **Observer**, or **Cron**.
3. Give the command a name and enter Bash or Python code.
4. Optionally choose a working directory and add inputs under **Extras**.
5. Save the command, then run it from AutoPie or its configured trigger.

You can also browse the command catalog to install ready-made commands and their dependencies.

## Command types and triggers

| Type | When it runs | Type-specific configuration |
| --- | --- | --- |
| `SHARE` | From AutoPie, Android's share sheet, a pinned shortcut, or another app | Receives text, URLs, files, or directories |
| `FILE_OBSERVER` | After a new file in the configured directory has finished being written | `selectors`: a list of regexes matched against the filename |
| `CRON` | Periodically in the background | `cronInterval`: values such as `15m`, `30m`, or `1h` |

Android limits periodic work to a minimum interval of 15 minutes. AutoPie raises shorter cron intervals to that minimum. File observers can be disabled globally from Settings.

The three types live together in `commands.json`. If `type` is omitted, AutoPie treats the entry as a share command.

## Command environment

AutoPie exposes inputs and extras as environment variables. Use normal shell syntax such as `"$INPUT_FILE"` or `"${QUALITY}"`.

| Variable | Value |
| --- | --- |
| `INPUT` | The raw active input. When a folder is selected, this is the selected folder path. In a multistage workflow, the previous step's exported `OUTPUT` becomes the next step's `INPUT`. |
| `INPUT_TEXT` | Shared or manually entered text. |
| `INPUT_FILE` | A single file path or first URL. For folder input, this is the current immediate child being processed. |
| `INPUT_FILES` | Multiple file paths separated by newlines. For folder input, this contains the folder's immediate children. |
| `INPUT_FILES_ARR` | `INPUT_FILES` converted to a Bash array; use `"${INPUT_FILES_ARR[@]}"`. |
| `INPUT_URL` | A single detected HTTP(S) URL. |
| `INPUT_URLS` | All detected URLs in shared text. |
| `FILENAME` | Input filename without its parent path. |
| `FILENAME_NO_EXT` | Filename without its parent path or extension. |
| `FILE_EXT` | File extension without the leading dot. |
| `DIRECTORY` | Parent directory of the input file. |
| `HOST` | Hostname for a URL input. |
| `RAND` | A random four-digit number for collision-resistant output names. |
| `COOKIE_JAR` | Path to the Netscape-format cookie jar populated by logins in AutoPie's browser. URL-based commands can pass it to tools that support Netscape cookie files. |

Availability depends on the kind of input. Extras are exported under their configured uppercase `name` as well.

When a folder is selected, AutoPie runs the command once for each immediate child in that folder. Use `INPUT` when the command needs the selected folder itself, and `INPUT_FILE` when it needs the child currently being processed.

Examples:

| Use | Command |
| --- | --- |
| Extract audio from a video | `ffmpeg -i "$INPUT_FILE" -b:a 192K -vn "$DIRECTORY/$FILENAME_NO_EXT.mp3"` |
| Combine images horizontally | `magick "${INPUT_FILES_ARR[@]}" +append "$DIRECTORY/$FILENAME_NO_EXT-horiz-$RAND.jpeg"` |
| Run inline Python | `#@PYTHON` followed by Python source on the next line |

## `commands.json`

AutoPie stores user and catalog commands in a single JSON object. Each top-level key is the command's display name.

```json
{
  "Convert image to WebP": {
    "id": "local.convert-image-webp",
    "type": "SHARE",
    "path": "Pictures",
    "command": "magick \"$INPUT_FILE\" -quality \"$QUALITY\" \"$DIRECTORY/$FILENAME_NO_EXT.webp\"",
    "extras": [
      {
        "id": "quality",
        "name": "QUALITY",
        "type": "SLIDER",
        "default": "1,85,100",
        "description": "WebP quality",
        "required": true,
        "flags": ["--int"]
      }
    ]
  }
}
```

Common command fields:

| Field | Meaning |
| --- | --- |
| `id` | Stable ID used by command references and Android intents. The top-level name is used as a fallback. |
| `type` | `SHARE`, `FILE_OBSERVER`, or `CRON`. |
| `path` | Working directory, normally relative to shared storage. |
| `command` | Bash command or inline script. |
| `selectors` | Regex list for a file observer. |
| `cronInterval` | Periodic interval for a cron command. |
| `flags` | Command-level behavior flags. |
| `extras` | Inputs exported as environment variables. |
| `multiStage` / `steps` | Enables and defines a multistage workflow. |
| `version` | Installed catalog-command version used for update detection. |

The legacy `exec` field is still accepted for packaged commands, but new shell commands can normally put the complete invocation in `command`.

You can edit `commands.json` directly from **Settings → Edit Config File**. AutoPie skips individual incompatible entries instead of hiding the rest of the valid configuration.

## Multistage workflows

Set `multiStage` to `true` and provide a `steps` array. Steps execute in order in the same shell and with the same process ID, so exported variables and other shell state survive between steps.

Use a `SHARE` command for a multistage workflow. AutoPie's command hub, share sheet, shortcuts, and direct-intent runner drive the step transitions and any per-step UI.

```json
{
  "Prepare and compress audio": {
    "id": "local.prepare-compress-audio",
    "type": "SHARE",
    "multiStage": true,
    "flags": ["--show-loading-screen"],
    "steps": [
      {
        "id": "prepare",
        "path": "Music",
        "command": "ffmpeg -y -i \"$INPUT_FILE\" \"$DIRECTORY/$FILENAME_NO_EXT.wav\"\nexport OUTPUT=\"$DIRECTORY/$FILENAME_NO_EXT.wav\""
      },
      {
        "id": "compress",
        "path": "Music",
        "command": "ffmpeg -y -i \"$INPUT\" -b:a \"$BITRATE\" \"$DIRECTORY/$FILENAME_NO_EXT-compressed.mp3\"",
        "extras": [
          {
            "id": "bitrate",
            "name": "BITRATE",
            "type": "SELECTABLE",
            "default": "192k",
            "selectableOptions": {
              "Small (128 kbps)": "128k",
              "Balanced (192 kbps)": "192k",
              "High (320 kbps)": "320k"
            }
          }
        ]
      }
    ]
  }
}
```

Workflow behavior:

- If a step succeeds, AutoPie advances to the next step. A failed step stops the workflow.
- `export OUTPUT=...` in one step passes that value to the next step as `INPUT`. AutoPie then clears `OUTPUT`, ready for the next handoff.
- Other exported variables remain available because the shell stays alive.
- A step with visible extras pauses the workflow and shows that step's input sheet. A step without visible extras continues automatically.
- Every step may define its own `id`, `path`, `command`, `flags`, and `extras`.
- An omitted step ID falls back to its zero-based position. At runtime, step IDs are namespaced under the parent command.
- Parent command flags apply to the first step; later steps use their own flags.
- Dismissing a workflow input sheet or cancelling its process stops the persistent shell.

### Fully interactive multistage example

This image workflow has three distinct stages:

1. Inspect the shared image and generate valid controls in the live shell.
2. Pause for editing options, render an intermediate file, and pass its path forward.
3. Pause for output settings, safely choose a destination, and move the result there.

“Interactive” here means AutoPie's per-step input UI. Do not add `#@INTERACTIVE`; that header opens the Termux terminal and is only supported for regular single-stage commands.

The workflow requires ImageMagick (`pkg install imagemagick`):

```json
{
  "Interactive image workflow": {
    "id": "local.interactive-image-workflow",
    "type": "SHARE",
    "multiStage": true,
    "flags": ["--show-loading-screen"],
    "steps": [
      {
        "id": "inspect",
        "path": "Pictures",
        "command": "set -e\nexport ORIGINAL_WIDTH=\"$(identify -format '%w' \"$INPUT_FILE\")\"\nexport ORIGINAL_HEIGHT=\"$(identify -format '%h' \"$INPUT_FILE\")\"\nsuggested=$(( ORIGINAL_WIDTH > 1920 ? 1920 : ORIGINAL_WIDTH ))\nexport WIDTH_RANGE=\"1,$suggested,$ORIGINAL_WIDTH\"\nexport EFFECT_OPTIONS='Keep original=none,Rotate 90 degrees=rotate,Grayscale=gray'\nprintf 'Input: %sx%s\\n' \"$ORIGINAL_WIDTH\" \"$ORIGINAL_HEIGHT\""
      },
      {
        "id": "edit",
        "path": "Pictures",
        "command": "set -e\ncase \"$EFFECT\" in\n  rotate) EFFECT_ARGS=(-rotate 90) ;;\n  gray) EFFECT_ARGS=(-colorspace Gray) ;;\n  *) EFFECT_ARGS=() ;;\nesac\nQUALITY_ARGS=()\nif [[ \"$FORMAT\" != png ]]; then QUALITY_ARGS=(-quality \"$QUALITY\"); fi\nintermediate=\"$DIRECTORY/.autopie-$RAND.$FORMAT\"\nmagick \"$INPUT_FILE\" \"${EFFECT_ARGS[@]}\" -resize \"${WIDTH}x>\" \"${QUALITY_ARGS[@]}\" \"$intermediate\"\nexport GENERATED_NAME=\"$FILENAME_NO_EXT-edited.$FORMAT\"\nexport OUTPUT=\"$intermediate\"",
        "extras": [
          {
            "id": "effect",
            "name": "EFFECT",
            "type": "SELECTABLE",
            "default": "none",
            "description": "Options are generated by the inspect step.",
            "selectableOptions": {
              "From inspect step": "$$EFFECT_OPTIONS"
            }
          },
          {
            "id": "width",
            "name": "WIDTH",
            "type": "SLIDER",
            "default": "$$WIDTH_RANGE",
            "description": "Output width, capped at the original image width.",
            "flags": ["--int", "--large"]
          },
          {
            "id": "format",
            "name": "FORMAT",
            "type": "SELECTABLE",
            "default": "webp",
            "selectableOptions": {
              "WebP": "webp",
              "JPEG": "jpg",
              "PNG": "png"
            }
          },
          {
            "id": "quality",
            "name": "QUALITY",
            "type": "SLIDER",
            "default": "1,85,100",
            "description": "Lossy output quality.",
            "flags": ["--int"],
            "visibleWhen": {
              "extraId": "format",
              "notEquals": "png"
            }
          }
        ]
      },
      {
        "id": "save",
        "path": "Pictures",
        "command": "set -e\nmkdir -p \"$OUTPUT_FOLDER\"\ntarget=\"$OUTPUT_FOLDER/$FINAL_NAME\"\nif [[ \"$OVERWRITE\" != true && -e \"$target\" ]]; then\n  stem=\"${FINAL_NAME%.*}\"\n  extension=\"${FINAL_NAME##*.}\"\n  target=\"$OUTPUT_FOLDER/$stem-$RAND.$extension\"\nfi\nmv -- \"$INPUT\" \"$target\"\nexport OUTPUT=\"$target\"\nprintf 'Saved: %s\\n' \"$OUTPUT\"",
        "extras": [
          {
            "id": "output_folder",
            "name": "OUTPUT_FOLDER",
            "type": "STRING",
            "default": "Pictures/AutoPie",
            "description": "Persistent destination folder.",
            "flags": ["--internal-config"]
          },
          {
            "id": "final_name",
            "name": "FINAL_NAME",
            "type": "STRING",
            "default": "$$GENERATED_NAME",
            "description": "Generated by the edit step.",
            "required": true,
            "flags": ["--large"]
          },
          {
            "id": "overwrite",
            "name": "OVERWRITE",
            "type": "BOOLEAN",
            "defaultBoolean": false,
            "description": "Replace an existing file with the same name."
          }
        ]
      }
    ]
  }
}
```

The important handoffs are:

- `inspect` exports `EFFECT_OPTIONS` and `WIDTH_RANGE`; AutoPie resolves the `$$...` defaults when it opens the `edit` step.
- `edit` exports the intermediate path as `OUTPUT`; AutoPie exposes it to `save` as `INPUT`.
- `edit` also exports `GENERATED_NAME`, which becomes the editable `FINAL_NAME` default.
- `QUALITY` is only visible for lossy formats because its rule watches the `format` extra by ID.
- `OUTPUT_FOLDER` is an internal config value. It is available to the command but stays collapsed after setup; users can reveal internal inputs from the step sheet when they need to change it.
- The final step quotes every path and adds `$RAND` instead of overwriting a collision unless `OVERWRITE` is enabled.

### Reuse an installed command as a step

A step can contain `commandId` instead of repeating a command:

```json
{
  "Reuse installed commands": {
    "id": "local.reuse-demo",
    "type": "SHARE",
    "multiStage": true,
    "steps": [
      { "commandId": "namespace.first-command" },
      { "commandId": "namespace.second-command" }
    ]
  }
}
```

`commandId` must exactly match the stable ID of an installed share command. AutoPie resolves that command's `path`, `command`, `flags`, and `extras` before the workflow starts. A missing reference fails the workflow with a clear error instead of running a partial pipeline.

## Extras and custom command UIs

An extra becomes an environment variable. For example, an extra named `QUALITY` is available as `$QUALITY` in the command.

| Extra type | UI and exported value |
| --- | --- |
| `STRING` | Text input; can also become a password, file picker, or folder picker. |
| `BOOLEAN` | `true` or `false`. |
| `SELECTABLE` | One value from a label-to-value map. |
| `SELECTABLE_FLAT` | One value from an always-visible vertical list. |
| `MULTI_SELECTABLE` | Multiple values joined with newlines. |
| `MULTI_SELECTABLE_FLAT` | Multiple values from an always-visible vertical list, joined with newlines. |
| `FLAG` | Exports its `default` value when checked, otherwise an empty string. |
| `SLIDER` | Numeric value. Configure `default` as `minimum,initial,maximum`. |

Useful extra fields:

| Field | Meaning |
| --- | --- |
| `id` | Stable identifier used by visibility rules and saved values. |
| `name` | Environment-variable name. Uppercase shell names are recommended. |
| `default` | Initial/exported value. |
| `defaultBoolean` | Initial value for a boolean extra. |
| `required` | Makes an empty string extra open the input sheet instead of being skipped by a quick run. |
| `description` | Help text shown below the input. |
| `selectableOptions` | Object of display labels to exported values. A legacy string array is also accepted. |
| `flags` | Input behavior such as secrets, pickers, layout, integer sliders, or realtime runs. |
| `visibleWhen` | Condition that controls whether the input is shown. |

Extra flags:

| Flag | Behavior |
| --- | --- |
| `--password` / `--secret` | Masks the input and stores its value in AutoPie's private encrypted preferences instead of writing it into `commands.json`. |
| `--internal-config` | Treats the value as persistent setup rather than an input that must be shown on every run. |
| `--file-picker` | Adds a single-file picker to a string input. Names ending in `FILE` also get this behavior. |
| `--multi-file-picker` | Adds a multi-file picker. Names ending in `FILES` also get this behavior. |
| `--folder-picker` | Adds a folder picker that exports an absolute path. Names ending in `FOLDER` also get this behavior. |
| `--mime-type=audio/*` | Restricts a file picker; the default is `*/*`. |
| `--int` | Makes a slider use integer values. |
| `--large` | Forces the input to use the full available row width. |
| `--realtime` | Re-runs the current command when this extra changes. |

String extras ending in `FILE`, `FILES`, or `FOLDER` are resolved to usable paths when relative values are supplied.

### Conditional extras

Use `visibleWhen` to build dependent forms. A simple rule references another extra by `extraId`:

```json
{
  "id": "custom_path",
  "name": "CUSTOM_PATH",
  "type": "STRING",
  "default": "",
  "required": false,
  "visibleWhen": {
    "extraId": "destination",
    "equals": "custom"
  }
}
```

Supported conditions are `equals`, `notEquals`, `startsWith`, `endsWith`, `contains`, `matches` (regex), `gt`, `gte`, `lt`, `lte`, `oneOf`, `exists`, and `isEmpty`. Compose rules with `all`, `or`/`any`, and `not`.

### Values generated by an earlier step

For `STRING`, `BOOLEAN`, `SELECTABLE`, `SELECTABLE_FLAT`, `MULTI_SELECTABLE`, `MULTI_SELECTABLE_FLAT`, and `SLIDER` inputs, a default or sole selectable value in the form `$$VARIABLE_NAME` is read from the workflow's live shell. This is useful when an earlier step discovers the valid choices or slider range.

For example, a preparation step can run:

```sh
export AUDIO_FORMATS='MP3=mp3,Opus=opus,FLAC=flac'
export VOLUME_RANGE='0,50,100'
```

Then a later selectable can use `{"From previous step": "$$AUDIO_FORMATS"}`, or a slider can set `"default": "$$VOLUME_RANGE"`.

### Realtime commands

Add `--realtime` to a command's top-level `flags` to re-run it whenever its visible inputs change, or add the flag to selected extras to trigger only on those values. Changes are debounced briefly, notifications are suppressed while adjusting, and the same shell/process is kept alive until the input sheet closes.

## Command and script flags

Command-level flags:

| Flag | Behavior |
| --- | --- |
| `--show-loading-screen` | Opens AutoPie's loading screen while the command runs. |
| `--show-loading-screen-small` | Opens a compact loading bottom sheet while the command runs. |
| `--realtime` | Re-runs as inputs change in the extras sheet. |

Script headers must appear at the beginning of `command`; multiple headers can be combined:

| Header | Behavior |
| --- | --- |
| `#@PYTHON` | Treats the remaining command body as inline Python. |
| `#@INTERACTIVE` | Opens a regular single-stage command in the interactive Termux shell. This is also available as **Debug Mode** in command details. |
| `#@OPEN_LOGS` | Opens the live output viewer when execution starts. |
| `#@SHELL` | Explicitly labels the command as shell code for command grouping and display. |
| `//@BROWSER` | Makes the command available in AutoPie's browser and runs the remaining body as JavaScript in the current page. |

Example:

```python
#@PYTHON
#@OPEN_LOGS
import os

print(f"Received: {os.environ.get('INPUT', '')}")
```

Interactive mode is for single-stage commands; multistage workflows use their own persistent background shell.

## Browser commands and cookies

Open **Settings → Browser** to use `BrowserActivity`, AutoPie's built-in browser. Commands whose script begins with `//@BROWSER` appear in the browser's command menu. Their Greasemonkey-style JavaScript runs in the context of the currently loaded page, so it can read and modify the DOM:

```js
//@BROWSER
document.querySelectorAll("video").length
```

Logging in to a service through the AutoPie browser synchronizes its cookies to `COOKIE_JAR`, a Netscape-format cookie file. URL-based shell commands can reuse the authenticated browser session with tools that accept this format, for example:

```sh
curl --cookie "$COOKIE_JAR" "$INPUT_URL"
```

Treat the cookie jar as sensitive because it may contain active login sessions.

## Command catalog and packages

The **Commands** catalog is backed by [autopie-commands](https://github.com/cryptrr/autopie-commands). You can search by name, ID, summary, or tag; read a command's README and changelog; install it; and update it when a newer catalog version is available. A catalog command can include an install script for its Termux or Python dependencies.

The embedded terminal is still the fastest way to install arbitrary tools:

```sh
pkg update
pkg install ffmpeg imagemagick
pip install yt-dlp
```

The bootstrap already contains Python, pip, binutils, OpenSSH, and sshpass.

## Run AutoPie commands from another app

Other apps can open AutoPie's command dialog with an explicit intent. The user is always shown AutoPie's UI before an external app can run a command.

```kt
@Composable
fun RunAutoPieCommandButton() {
    val context = LocalContext.current

    val intent = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(
            "com.autopi",
            "com.autopi.DirectCommandActivity"
        )
        putExtra("commandId", "autopie.yt-dlp-downloader")
        putExtra("input", "https://example.com/video")
        putExtra("async", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Button(onClick = {
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e("AutoPie", "Unable to open AutoPie", it) }
    }) {
        Text("Run with AutoPie")
    }
}
```

Intent extras:

| Extra | Meaning |
| --- | --- |
| `commandId` | Required command ID or display name. |
| `input` | Optional text or URL input. |
| `async` | Defaults to `true`. A regular command can return as soon as it starts; `false` waits for completion. Multistage workflows return after their final step. |
| `processId` | Optional non-negative process ID supplied by the caller. |

When launched for a result, AutoPie returns `status` (`running`, `ok`, or `failed`), `processId`, and a `logFile` content URI. Use Android's Activity Result API and grant/read URI access as appropriate for your app.

You can also open a command's details in AutoPie and choose **Add to Home Screen** to create a launcher shortcut without writing any Android code.

## File observer example

The following kind of observer can automatically convert every new screenshot to WebP. Set the observer directory to your screenshots folder and use a selector such as `^.*\.png$`.

<div style="display:flex; flex-direction:row; width:100%; justify-content:center; align-items:center">
<img src="https://github.com/user-attachments/assets/af5a7cb2-0953-4886-97fb-d64a06289677" alt="AutoPie file observer example command" style="width:55%; height:auto">
</div>

## Configuration, logs, and troubleshooting

AutoPie can store `AutoSec` in either shared external storage or its private app-data home. Change the location in **Settings → AutoPie Config Path**; AutoPie moves the directory when possible. External storage survives an uninstall but is less private. App-data storage is more private but is normally removed with the app.

Important files and directories inside `AutoSec` include:

- `commands.json` — all share, observer, cron, and multistage command definitions.
- `bin/` — packaged command executables.
- `logs/autopie.log` — optional application file log.

Useful Settings actions include opening the Termux terminal, editing `commands.json` in `nano`, enabling file logging, opening logs with `less`, turning file observers on or off, and clearing the packaged-command cache.

Troubleshooting:

- If the command list is empty, open Settings and verify the active config path and `commands.json`.
- If `commands.json` is missing or the initial config is unusable, move the current `AutoSec` directory somewhere safe and reopen AutoPie so it can initialize a fresh one. Restore custom entries after comparing the files.
- AutoPie does not overwrite an existing `AutoSec` directory during app updates, which protects custom commands but can leave bundled package data behind. See [README-updates.md](README-updates.md) before replacing package data.
- Enable **File Logger** and inspect `AutoSec/logs/autopie.log` when a service, observer, or scheduled command does not start.
- Each command also has a **History** view, and process notifications link to command output logs.
- Android battery restrictions can delay cron commands and stop long-running background work; disable battery optimization if necessary.

## Build from source

AutoPie requires JDK 17 and an Android SDK. Open the project in Android Studio for normal development builds, or use the included scripts to prepare the patched Termux modules and bootstrap.

To clone the latest official Termux source, apply AutoPie's patches, prepare the embedded bootstrap, and build a debug APK:

```sh
./build_with_termux.sh
```

Pass Gradle tasks to make another build, for example:

```sh
./build_with_termux.sh :app:assembleRelease
```

Set `TERMUX_REF` to pin an upstream Termux tag or commit.

The build script downloads Termux's pinned bootstrap, injects AutoPie's required packages from the Termux repository, rewrites package paths for `com.autopi`, and writes `app/src/main/assets/bootstrap-aarch64.zip`.

For individual preparation stages:

```sh
./scripts/prepare-termux-app.sh
./scripts/prepare-termux-bootstrap.sh
```

To build the bootstrap entirely from source with the native `termux-generator` pipeline, run the following on a compatible Ubuntu x86_64 host:

```sh
./build_from_source.sh
```

It installs the generated archive at `app/src/main/assets/bootstrap-aarch64.zip`; afterward, build normally in Android Studio or with Gradle.

## Support

- [GitHub issues](https://github.com/cryptrr/AutoPie/issues)
- [Discord](https://discord.gg/rsZ3Sr42Am)

## Thanks

- [Jared Rummler](https://github.com/jaredrummler)
- [Termux](https://github.com/termux)

AutoPie is licensed under the [Apache License 2.0](LICENSE).
