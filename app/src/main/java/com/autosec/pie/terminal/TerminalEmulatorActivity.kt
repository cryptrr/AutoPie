package com.autosec.pie.terminal

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


import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.autosec.pie.R
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.jaredrummler.ktsh.Shell
import com.autosec.pie.terminal.terminal.Terminal
import org.koin.java.KoinJavaComponent.inject

/**
 * A simple terminal emulator demo using [Shell.SH].
 */
class TerminalEmulatorActivity : ComponentActivity(), TerminalEmulatorView {

    private lateinit var terminal: Terminal
    private val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)


    private val viewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(TerminalEmulatorViewModel::class.java)
    }

    private val preferences: SharedPreferences by lazy {
        getSharedPreferences(packageName, Context.MODE_PRIVATE)
    }

    private var userTheme: Terminal.Theme
        get() = Terminal.Theme.valueOf(
            requireNotNull(preferences.getString(PREF_THEME_NAME, Terminal.Theme.DEFAULT.name))
        )
        set(value) {
            preferences.edit().putString(PREF_THEME_NAME, value.name).apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal_emulator)
        //TODO:
        //setSupportActionBar(findViewById(R.id.toolbar))
        setUpTerminal()
    }

    override fun execute(
        shell: Shell,
        command: String,
        callback: (result: Shell.Command.Result) -> Unit
    ) {
        viewModel.run(shell, command, callback)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val themeMenu =
            menu.addSubMenu(0, Menu.FIRST, 0, "Color Scheme")
        themeMenu.setIcon(R.drawable.ic_launcher_foreground)
        themeMenu.item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        val selectedTheme = userTheme
        for (theme in Terminal.Theme.values()) {
            val itemId = theme.ordinal + Menu.FIRST + 1
            val title = theme.name.replace("_", " ")
            val item = themeMenu.add(GROUP_COLOR_SCHEME, itemId, theme.ordinal, title)
            item.isChecked = selectedTheme == theme
        }
        themeMenu.setGroupCheckable(GROUP_COLOR_SCHEME, true, true)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.groupId) {
        GROUP_COLOR_SCHEME -> applyTheme(item)
        else -> super.onOptionsItemSelected(item)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> terminal.navigate(
            Terminal.History.Direction.UP
        )
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> terminal.navigate(
            Terminal.History.Direction.DOWN
        )
        else -> super.onKeyUp(keyCode, event)
    }

    private fun setUpTerminal() {
        terminal = findViewById(R.id.terminal)
        val customShell = processManagerService.createTerminalShell()
        if(customShell != null){
            terminal.attach(customShell, this)
            applyTheme(userTheme)
        }
    }

    private fun applyTheme(item: MenuItem): Boolean {
        item.isChecked = true
        return applyTheme(Terminal.Theme.values()[item.order])
    }

    private fun applyTheme(theme: Terminal.Theme): Boolean {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(theme.scheme.syntaxScheme.keywordColor)
        window.statusBarColor = theme.scheme.syntaxScheme.keywordColor
        terminal.theme = theme
        userTheme = theme
        return true
    }

    companion object {
        private const val GROUP_COLOR_SCHEME = 0x01
        private const val PREF_THEME_NAME = "theme_name"
    }
}
