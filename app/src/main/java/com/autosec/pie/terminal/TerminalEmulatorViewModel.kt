/*
 * Copyright (C) 2021 Jared Rummler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.autosec.pie.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.jaredrummler.ktsh.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class TerminalEmulatorViewModel : ViewModel() {

    private val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)


    fun run(
        shell: Shell,
        command: String,
        callback: (result: Shell.Command.Result) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(dispatchers.io) { shell.run(command) }
        withContext(dispatchers.main) { callback(result) }
    }
}