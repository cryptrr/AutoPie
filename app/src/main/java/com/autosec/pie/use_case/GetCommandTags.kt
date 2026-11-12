package com.autopi.use_case

import com.autopi.autopieapp.data.CommandModel
import com.autopi.utils.getCommandExec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun AutoPieUseCases.getCommandTags(
    commands: Flow<List<CommandModel>>,
    hideAutomaticTags: Flow<Boolean>
): Flow<List<String>> =
    combine(
        commands,
        getLatestUsedPackages(3),
        getUserTags(),
        hideAutomaticTags
    ) { availableCommands, recentlyUsed, userTags, hideAutomatic ->
        if (hideAutomatic) {
            userTags.distinct()
        } else {
            val frequent = availableCommands.mapNotNull { getCommandExec(it) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(7)

            ((frequent - recentlyUsed.toSet()) + recentlyUsed + userTags).distinct()
        }
    }
