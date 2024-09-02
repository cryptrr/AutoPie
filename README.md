# AutoPie

Create your own Commands and Run them on your Android without wasting time.

Lets you run Python scripts with or without included binaries on Android.

https://github.com/user-attachments/assets/b0b04c0b-a829-42e9-b2df-7f311f9cc153

## Installation

1) Build from source yourself or get the prebuilt APK from the releases section.
2) Install the APK and accept Play Protect Dialogs if any.
3) Wait for the Python Binaries to get installed.
4) Grant necessary permissions.
5) AutoPie will try to download an init binary & configuration archive and extract it into the `AutoSec` directory. If it fails, you can download the `autosec.tar.xz` file and extract to the `AutoSec` folder.
6) Optional: Disable Battery Optimization for AutoPie.

## Usage

1) Open AutoPie App
2) There are currently two types of commands. `Share Sheet Commands` and `Folder Observer Commands`
3) Add your desired Commands in the AutoPie App by clicking on Add Button or Edit an already existing command.

## Troubleshooting
* Check that the `AutoSec` folder contains `observers.json` for Folder Observation Automation and `shares.json` for Share Sheet Configuration. And a `bin` folder with the binaries like `ffmpeg`.

## Command Format

| PLACEHOLDER   | DESCRIPTION                                                                                     |
|---------------|-------------------------------------------------------------------------------------------------|
| {INPUT_FILE}  | Use it to pass input file or url in the command                                                 |
| {INPUT_FILES} | If multiple files are needed as input to the command<br/> Example : `magick combine two images` |

## Example Commands

| USE                             | COMMAND                                                                                     |
|---------------------------------|---------------------------------------------------------------------------------------------|
| Ffmpeg Extract Audio from Video | `-i {INPUT_FILE} -b:a 192K -vn {INPUT_FILE}.mp3`                                            |
| ImageMagick combine horizontal  | `{INPUT_FILES} +append {INPUT_FILE}.horiz.jpeg` |

###  This configuration enables you to automatically convert each screenshot you take into webp.

https://github.com/user-attachments/assets/cff1f4bd-a13d-4a85-89f6-dbea3fb90461

### More Command Placeholders will be available later.

## Custom Defined Command Arguments for Customization.

WIP: AutoPie is working on supporting taking custom arguments that can be defined in a command .

This will show a card where you can input details to customize the behaviour of the command.

## Commands and Package Repository

A repository where users can search and add pre-made AutoPie command snippets and install packages is in the works.

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


## Support
* Supports only aarch64/arm-v8 as of now. It should run on most newer phones.

## Why

* Makes it easier to automate and run any task.
* Termux will be constrained in the future by Android Platform limitations.
* Needed to find an alternative method to Termux by including a whole Python installation in the APK itself that enables us to run scripts and binaries from anywhere on the storage.


## Thanks To

[Jared Drummler](https://github.com/jaredrummler)




