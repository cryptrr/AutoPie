package com.autopi.use_case

data class AutoPieUseCases (
    val getCommandsList: GetCommandsList,
    val getRepoCommandsList: GetRepoCommandsList,
    val getShareCommands: GetShareCommands,
    val createCommand: CreateCommand,
    val getCommandDetails: GetCommandDetails,
    val runCommand: RunCommand,
    val runCommandForDirectory: RunCommandForDirectory,
    val runCommandForUrl : RunCommandForUrl,
    val runCommandForFiles: RunCommandForFiles,
    val runCommandForText: RunCommandForText,
    val runStandaloneCommand: RunStandaloneCommand,
    val changeCommandDetails: ChangeCommandDetails,
    val deleteCommand: DeleteCommand,
    val addCommandToHistory: AddCommandToHistory,
    val getHistoryOfCommand: GetHistoryOfCommand,
    val getLatestUsedPackages: GetLatestUsedPackages,
    val getUserTags: GetUserTags,
    val addUserTag: AddUserTag,
    val deleteUserTag: DeleteUserTag,
    val getInstalledPackages: GetInstalledPackages,
    val runInteractiveCommand: RunInteractiveCommand,
    val toggleCommandDebugMode: ToggleCommandDebugMode,
    val storeCommandExtraInputs: StoreCommandExtraInputs
)
