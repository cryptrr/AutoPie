# AutoPie

Create your own Commands and Run them on your Android without wasting time.

Lets you run Python scripts with or without included binaries on Android.

## Installation

1) Build from source yourself or get the prebuilt APK from the releases section.
2) Install the APK and accept Play Protect Dialogs if any.
3) Grant Storage Manager Permission.
4) Optional: Disable Battery Optimization for AutoPie.

## Usage

1) Wait for the Python Binaries to get installed.
2) AutoPie will try to download an init binary & configuration archive and extract it into the `AutoSec` directory. If it fails, you can download the `autosec.tar.xz` file and extract to the `AutoSec` folder.
3) Check that the `AutoSec` folder contains a `bin` folder with the binaries like `ffmpeg`, `observers.json` for Folder Observation Automation and `shares.json` for Share Sheet Configuration.
4) Add your desired Commands in the AutoPie App by clicking on Add Button.

## Command Format

| PLACEHOLDER   | DESCRIPTION                                                                                     |
|---------------|-------------------------------------------------------------------------------------------------|
| {INPUT_FILE}  | Use it to pass input file or url in the command                                                 |
| {INPUT_FILES} | If multiple files are needed as input to the command<br/> Example : `magick combine two images` |

## Example Commands

| USE                             | COMMAND                                                                                         |
|---------------------------------|-------------------------------------------------------------------------------------------------|
| Ffmpeg Extract Audio from Video | `-i '{INPUT_FILE}' -b:a 192K -vn '{INPUT_FILE}.mp3'`                                                |
| ImageMagick combine horizontal  | `'{INPUT_FILES}' +append '{INPUT_FILE}.horiz.jpeg'` |

### More Command Placeholders will be available later.


## Support
* Supports only aarch64/arm-v8 as of now. It should run on most newer phones.

## Why

* Makes it easier to automate and run any task.
* Termux will be constrained in the future by Android Platform limitations.
* Needed to find an alternative method to Termux by including a whole Python installation in the APK itself that enables us to run scripts and binaries from anywhere on the storage.
* 

