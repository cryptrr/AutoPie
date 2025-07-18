# AutoPie

### Commands hub where you can create, automate and run commands without using the terminal.

**AutoPie is your own power tool-kit for android.**

[Get the APK from here](https://github.com/cryptrr/AutoPie/releases/)


<div style="display:flex;flex-direction:row;justify-content:space-between">
<img src="https://github.com/user-attachments/assets/ee74f114-2d02-4c6f-8429-68398764c006" alt="AutoPie screenshot" width="47%" height="auto">
<img src="https://github.com/user-attachments/assets/1e996b5f-02e5-46bd-9ff4-78bb886bd410" alt="AutoPie feature demo" width="47%" height="auto">
</div>

<video src="https://github.com/user-attachments/assets/4b51312f-fb4a-4d7c-9318-0ffd1aed2dfb" width="47%" height="auto"></video>


## Installation

1) Build from source yourself or get the prebuilt APK from the releases section.
2) Install the APK and accept Play Protect Dialogs if any.
3) Wait for the Python Binaries to get installed.
4) Grant necessary permissions.
5) AutoPie will try to download an init binary & configuration archive and extract it into the `AutoSec` directory. If it fails, you can download the latest `autosec-init.tar.xz` file from the [releases](https://github.com/cryptrr/AutoPie/releases/) and extract to the `AutoSec` folder.
6) Optional: Disable Battery Optimization for AutoPie.


## Usage

1) Open AutoPie App
2) There are currently three types of commands. `Share Sheet Commands`, `Folder Observer Commands` and `Cron Commands`.
3) Add your desired Commands in the AutoPie App by clicking on Add Button or Edit an already existing command.

## Easiest way to add new packages
- Open the Terminal inside AutoPie
- Pip should already be installed in the AutoPie environment. Otherwise, Run `python3.10 -m ensurepip`
- Run `pip3 install package`



## Troubleshooting
* Check that the `AutoSec` folder contains `observers.json` for Folder Observation Automation, `shares.json` for Share Sheet Configuration and `cron.json` for Cron Configuration. And a `bin` folder with the binaries like `ffmpeg`.

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

| USE                             | COMMAND                                                   |
|---------------------------------|-----------------------------------------------------------|
| Ffmpeg Extract Audio from Video | `-i ${INPUT_FILE} -b:a 192K -vn ${INPUT_FILE}.mp3`        |
| ImageMagick combine horizontal  | `${INPUT_FILES} +append ${INPUT_FILE}-horiz-${RAND}.jpeg` |

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

- A package manager.

- A repository where users can search and add pre-made AutoPie command snippets and install packages is in the works.

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


### How do I create binaries for AutoPie.

AutoPie binaries are just thin python wrappers around binaries like `ffmpeg` `magick` etc.

Using **Shiv** to package the binaries, dependencies and python files is strongly recommended.

This is an example for how to include the `ffmpeg` binaries and libraries in a single file with shiv.

A more detailed docs is in WIP.


```py

import subprocess
import sys
import os
import shlex

script_dir = os.path.dirname(__file__)

# Set the path to the ImageMagick binary and library directories
bin_path = os.path.join(script_dir, 'usr', 'bin')
lib_path = os.path.join(script_dir, 'usr', 'lib')

# Update PATH environment variable
os.environ['PATH'] = bin_path + os.pathsep + os.environ['PATH']

# Update LD_LIBRARY_PATH environment variable
os.environ['LD_LIBRARY_PATH'] = lib_path + os.pathsep + os.environ.get('LD_LIBRARY_PATH', '')



def main():
    
    """Console script for ffmpeg wrapper"""

    input_command = sys.argv[1]

    commands_list = shlex.split(input_command)

    runner(commands_list)

def runner(commands_list: list[str]):

    try:

        commands_list= ["ffmpeg"] + commands_list

        print(f"shlex command list: {commands_list}")

        result = subprocess.run(commands_list)

        if result.returncode == 0:
            print(f"Successfully executed {commands_list}")
        else:
            print(f"Subprocess failed with exit code {result.returncode} : {commands_list}")
        
        sys.exit(result.returncode)
        
    except Exception as e:
        print(f"Unexpected error: {e}")
        sys.exit(e.returncode)


if __name__ == "__main__":
    main()

```

## Build Instructions

You can build the app with prebuilt binaries by opening the project with Android Studio and then run build task.

### If you want to build your own python and busybox binaries

* Set Environment Variable `ANDROID_NDK_ROOT` to your Android NDK installation folder.
* Run the `./build-all.sh` script to build all dependencies and store them in the Assets folder.


## Support
* Supports only aarch64/arm-v8 as of now. It should run on most newer phones.


## Thanks To

[Jared Rummler](https://github.com/jaredrummler)

[Termux](https://github.com/termux)



