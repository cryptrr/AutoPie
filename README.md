# AutoPie

### [Get the APK from here](https://github.com/cryptrr/AutoPie/releases/)

### AutoPie includes a Termux-based environment, so you can now install packages
## from the terminal with **pkg install package-name**.

### Commands hub where you can create, automate and run commands without using the terminal.

**AutoPie is your own power tool-kit for Android.**


<div style="display:flex;flex-direction:row;justify-content:space-between">
<img src="https://github.com/user-attachments/assets/ff9d86db-fc71-45e6-bbe6-9891ab5af35c" alt="AutoPie screenshot" width="47%" height="auto">
<img src="https://github.com/user-attachments/assets/1e996b5f-02e5-46bd-9ff4-78bb886bd410" alt="AutoPie feature demo" width="47%" height="auto">
</div>

[<video src="https://cryptrr.github.io/AutoPie/fastlane/metadata/android/en-US/autopie-feature-demo1.mp4" width="47%" height="auto"></video>
](https://github.com/user-attachments/assets/4b51312f-fb4a-4d7c-9318-0ffd1aed2dfb)



## Installation

1) Build from source yourself or get the prebuilt APK from the releases section.
2) Install the APK and accept Play Protect Dialogs if any.
3) Open AutoPie once and wait for the embedded Termux bootstrap to finish installing.
4) Grant necessary permissions.
5) Optional: Disable Battery Optimization for AutoPie.


## Usage

1) Open AutoPie App
2) There are currently three types of commands. `Share Sheet Commands`, `Folder Observer Commands` and `Cron Commands`.
3) Add your desired Commands in the AutoPie App by clicking on Add Button or Edit an already existing command.

## Easiest way to add new packages
- Open the Terminal inside AutoPie
- Install Termux packages with `pkg install package-name`
- Install Python packages with `pip install package-name`



## Troubleshooting
* If `pkg install` fails immediately after installation, open the AutoPie terminal once more and let the bootstrap finish before retrying.
* Check that the `AutoSec` folder contains `observers.json` for Folder Observation Automation, `shares.json` for Share Sheet Configuration and `cron.json` for Cron Configuration. If not or if your commands list is empty, delete the `AutoSec` folder and reopen the application.

## Command Format

| PLACEHOLDER        | DESCRIPTION                                                                                     |
|--------------------|-------------------------------------------------------------------------------------------------|
| ${INPUT_FILE}      | Use it to pass input file path or url in the command                                            |
| ${INPUT_FILES}     | If multiple files are needed as input to the command<br/> Example : `magick combine two images` |
| ${INPUT_URL}       | If the program takes a single URL                                                               |
| ${INPUT_URLS}      | If the program takes multiple URLs                                                              |
| ${INPUT_TEXT}      | If program takes raw TEXT as value                                                              |
| ${FILENAME}        | Filename without path                                                                           |
| ${DIRECTORY}       | Parent Directory of file                                                                        |
| ${FILENAME_NO_EXT} | Filename without path and extension                                                             |
| ${FILE_EXT}        | File extension                                                                                  |
| ${RAND}            | Random 4 digit number                                                                           |
| ${HOST}            | URL host (Only available if the input is a URL)                                                 |

## Example Commands

| USE                             | COMMAND                                                          |
|---------------------------------|------------------------------------------------------------------|
| Ffmpeg Extract Audio from Video | `ffmpeg -i ${INPUT_FILE} -b:a 192K -vn ${INPUT_FILE}.mp3`        |
| ImageMagick combine horizontal  | `magick ${INPUT_FILES} +append ${INPUT_FILE}-horiz-${RAND}.jpeg` |


## Start AutoPie Commands from your app.

AutoPie supports starting **command dialogs** from other apps through intents. Other apps **cannot** directly run commands without user prompt.

Extend the functionality of your apps by adding `RunCommandButton()`.

```kt
@Composable
fun RunCommandButton(){
    val context = LocalContext.current
    val intent = Intent(Intent.ACTION_MAIN).apply {
        setClassName(context, "com.autopi" + ".DirectCommandActivity")
        component = ComponentName(
            "com.autopi", // target app package
            "com.autopi.DirectCommandActivity" // full class name
        )
        putExtra("commandId", "YT-DLP Generic Downloader")
        putExtra("input", "https://www.youtube.com/watch?v=7N74-JBHk3g")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Button(onClick = {
        try {
            context.startActivity(intent)
        }catch (e: Exception){
            Log.e("ERROR",e.toString())
        }
    }) {
        Text("Run Command")
    }
}
```

###  This configuration enables you to automatically convert each screenshot you take into webp.

<div style="display:flex; flex-direction:row; width:100%; justify-content:center; align-items:center">
    <img src="https://github.com/user-attachments/assets/af5a7cb2-0953-4886-97fb-d64a06289677" alt="AutoPie file observer example command" style="width:55%; height:auto">
</div>

## Custom Defined Command Arguments for Customization.

With AutoPie, you can define custom arguments called (extras) for commands.

This will show a card where you can input details to customize the behaviour of the command.

For Example,

Defining extra items such as options for codecs etc will look like this.


<div style="display:flex;flex-direction:row;justify-content:space-between">
<img src="https://github.com/user-attachments/assets/cb7c3010-5270-49a8-9854-1f19b17a6a27" alt="AutoPie extras config" width="47%" height="auto">
<img src="https://github.com/user-attachments/assets/25e2d5a9-bf40-4c23-85ae-8c4dfebe917a" alt="AutoPie extras how to" width="47%" height="auto">

</div>

## Commands and Package Repository

- A package manager powered by the embedded Termux environment.

- A repository where users can search and add pre-made AutoPie command snippets is in the works.

## New MCP Server
AutoPie now comes with your own MCP server that you can use to automate your phone with AI Tools.

The server is easily extensible by adding your scripts to the /ExternalStorage/AutoSec/mcp_modules folder.

The scripts inside this folder will be included as tools in the MCP server.

You can add any kind of functionality on your phone with this by just writing a Python script.

The MCP tool scripts should be in this format.

```py
import os
from typing import Dict, Any
from pydantic import BaseModel

class CreateFileInput(BaseModel):
    filepath: str
    content: str


class MCPTool:
    path = "/create_file"
    name = "create_text_file"
    methods=["POST"]
    
    async def run(self, input: CreateFileInput) -> Dict[str, Any]:
        """Creates a text file with the specified content."""
        try:
            # Create directory if it doesn't exist
            os.makedirs(os.path.dirname(os.path.abspath(input.filepath)), exist_ok=True)
            
            # Write content to file
            with open(input.filepath, "w") as f:
                f.write(input.content)
            
            return {
                "status": "success",
                "message": f"File '{input.filepath}' created successfully"
            }
        except Exception as e:
            return {
                "status": "error",
                "message": f"Failed to create file: {str(e)}"
            }

```



## Build Instructions

You can build the app by opening the project with Android Studio and running the
normal Gradle build tasks. For a clean command-line build that prepares the
embedded Termux source and bootstrap, use `build_with_termux.sh`.

The Termux modules are generated rather than stored as a git submodule. To
clone the latest official Termux source, apply the AutoPie patch series, and
build a debug APK, run:

```sh
./build_with_termux.sh
```

Pass Gradle tasks as arguments for another build, for example
`./build_with_termux.sh :app:assembleRelease`. Set `TERMUX_REF` to pin a
specific upstream tag or commit.

The build script also downloads Termux's pinned bootstrap, injects AutoPie's
required packages from termux repo, patches package paths for `com.autopi`, and writes the final
bootstrap archive to `app/src/main/assets/bootstrap-aarch64.zip`.

Run `./scripts/prepare-termux-app.sh` when you only want to refresh the patched
Termux checkout for Android Studio. Run `./scripts/prepare-termux-bootstrap.sh`
when you only want to regenerate the bootstrap asset.

The current bootstrap includes `python`, `pip`, `binutils`, `openssh`, and
`sshpass` so package installation works from a fresh app install without Docker
or the old Python/busybox build scripts.


## Support
* Supports only aarch64/arm-v8 as of now. It should run on most newer phones.


## Thanks To

[Jared Rummler](https://github.com/jaredrummler)

[Termux](https://github.com/termux)
