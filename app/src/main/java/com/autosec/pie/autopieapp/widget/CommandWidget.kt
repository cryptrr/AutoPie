package com.autopi.autopieapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.autopi.MainActivity
import com.autopi.DirectCommandActivity
import com.autopi.R
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.blacksquircle.ui.language.base.model.SyntaxScheme
import com.blacksquircle.ui.language.json.JsonLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

object CommandWidgetState {
    val commandId = stringPreferencesKey("command_id")
    val commandName = stringPreferencesKey("command_name")
    val output = stringPreferencesKey("output")
    val status = stringPreferencesKey("status")
}

internal sealed interface DisplayOutput {
    data object Empty : DisplayOutput
    data class Number(val value: String) : DisplayOutput
    data class Items(val values: List<String>) : DisplayOutput
    data class Json(val value: String) : DisplayOutput
    data class PlainText(val value: String) : DisplayOutput
}

private val prettyJson = GsonBuilder().setPrettyPrinting().create()

internal fun parseDisplayOutput(raw: String?): DisplayOutput {
    if (raw == null) return DisplayOutput.Empty
    if (raw.isBlank()) return DisplayOutput.PlainText("")

    return runCatching {
        val json = JsonParser.parseString(raw)
        when {
            json.isJsonPrimitive && json.asJsonPrimitive.isNumber -> {
                DisplayOutput.Number(json.asJsonPrimitive.asString)
            }
            json.isJsonArray && json.asJsonArray.all {
                it.isJsonPrimitive && it.asJsonPrimitive.isString
            } -> {
                DisplayOutput.Items(json.asJsonArray.map { it.asString })
            }
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                DisplayOutput.PlainText(json.asString)
            }
            else -> DisplayOutput.Json(prettyJson.toJson(json))
        }
    }.getOrElse { DisplayOutput.PlainText(raw) }
}

internal fun boundWidgetOutput(raw: String): String = when (val output = parseDisplayOutput(raw)) {
    DisplayOutput.Empty -> ""
    is DisplayOutput.Number -> output.value
    is DisplayOutput.Items -> Gson().toJson(
        output.values.take(MAX_STORED_ITEMS).map { it.take(MAX_STORED_ITEM_LENGTH) }
    )
    is DisplayOutput.Json -> boundJson(output.value)
    is DisplayOutput.PlainText -> Gson().toJson(output.value.take(MAX_STORED_OUTPUT_LENGTH))
}

private fun boundJson(prettyValue: String): String {
    val compact = Gson().toJson(JsonParser.parseString(prettyValue))
    if (compact.length <= MAX_STORED_OUTPUT_LENGTH) return compact

    return Gson().toJson(JsonObject().apply {
        addProperty("preview", compact.take(MAX_STORED_OUTPUT_LENGTH - 128))
        addProperty("truncated", true)
    })
}

class CommandWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        bindPendingCommandIfNecessary(context, id)
        provideContent {
            GlanceTheme {
                CommandWidgetContent(context)
            }
        }
    }
}

@Composable
private fun CommandWidgetContent(context: Context) {
    val state = currentState<Preferences>()
    val commandId = state[CommandWidgetState.commandId]
    val commandName = state[CommandWidgetState.commandName].orEmpty()
    val status = state[CommandWidgetState.status]
    val output = parseDisplayOutput(state[CommandWidgetState.output])

    val primaryAction = actionStartActivity(
        if (commandId != null) {
            Intent(context, DirectCommandActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra("commandId", commandId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(22.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = commandName.ifBlank { "AutoPie command" },
                    maxLines = 2,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            when (output) {
                DisplayOutput.Empty -> EmptyOutput(commandId != null)
                is DisplayOutput.Number -> NumberOutput(output.value)
                is DisplayOutput.Items -> ListOutput(output.values)
                is DisplayOutput.Json -> JsonOutput(context, output.value)
                is DisplayOutput.PlainText -> TextOutput(output.value)
            }
        }

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(24.dp)
                    .clickable(primaryAction)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .background(ColorProvider(statusColor(status)))
                        .cornerRadius(5.dp)
                ) {}
                Spacer(GlanceModifier.width(9.dp))
                Text(
                    text = if (commandId == null) "Open" else "Run",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyOutput(isConfigured: Boolean) {
    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(
            text = if (isConfigured) "No output yet" else "Choose a command from AutoPie",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun NumberOutput(value: String) {
    Text(
        text = value,
        modifier = GlanceModifier.fillMaxWidth(),
        maxLines = 2,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    )
}

@Composable
private fun ListOutput(values: List<String>) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        values.take(6).forEach { value ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "•",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(9.dp))
                Text(
                    text = value,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp
                    )
                )
            }
        }
        if (values.size > 6) {
            Text(
                text = "+ ${values.size - 6} more",
                modifier = GlanceModifier.padding(top = 5.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Composable
private fun TextOutput(value: String) {
    Text(
        text = value.ifBlank { "Output is empty" },
        modifier = GlanceModifier.fillMaxWidth(),
        maxLines = 8,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 16.sp,
            textAlign = TextAlign.Start
        )
    )
}

@Composable
private fun JsonOutput(context: Context, value: String) {
    val remoteViews = RemoteViews(context.packageName, R.layout.command_widget_json_output).apply {
        setTextViewText(R.id.command_widget_json_text, highlightedJson(context, value))
        setTextColor(R.id.command_widget_json_text, jsonPalette(context).text)
    }
    AndroidRemoteViews(
        remoteViews = remoteViews,
        modifier = GlanceModifier.fillMaxWidth()
    )
}

private data class JsonPalette(
    val text: Int,
    val number: Int,
    val operator: Int,
    val keyword: Int,
    val string: Int
)

private fun jsonPalette(context: Context): JsonPalette {
    val isDark = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return if (isDark) {
        JsonPalette(
            text = 0xFFE6E1E5.toInt(),
            number = 0xFF82AAFF.toInt(),
            operator = 0xFF89DDFF.toInt(),
            keyword = 0xFFC792EA.toInt(),
            string = 0xFFC3E88D.toInt()
        )
    } else {
        JsonPalette(
            text = 0xFF24292F.toInt(),
            number = 0xFF0550AE.toInt(),
            operator = 0xFF0A3069.toInt(),
            keyword = 0xFF8250DF.toInt(),
            string = 0xFF116329.toInt()
        )
    }
}

private fun highlightedJson(context: Context, value: String): CharSequence {
    val palette = jsonPalette(context)
    val scheme = SyntaxScheme(
        numberColor = palette.number,
        operatorColor = palette.operator,
        keywordColor = palette.keyword,
        typeColor = palette.keyword,
        langConstColor = palette.keyword,
        preprocessorColor = palette.keyword,
        variableColor = palette.text,
        methodColor = palette.text,
        stringColor = palette.string,
        commentColor = palette.text,
        tagColor = palette.text,
        tagNameColor = palette.text,
        attrNameColor = palette.text,
        attrValueColor = palette.string,
        entityRefColor = palette.operator
    )
    val highlighted = SpannableString(value)
    JsonLanguage().getStyler().execute(value, scheme).forEach { syntaxSpan ->
        val paint = TextPaint().apply { color = palette.text }
        syntaxSpan.updateDrawState(paint)
        highlighted.setSpan(
            ForegroundColorSpan(paint.color),
            syntaxSpan.start.coerceIn(0, value.length),
            syntaxSpan.end.coerceIn(0, value.length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return highlighted
}

private fun statusColor(status: String?): Color = when (status) {
    "running" -> Color(0xFFFFBE5C)
    "success" -> Color(0xFF5BCF8E)
    "failed" -> Color(0xFFEF7070)
    else -> Color(0xFF878992)
}

class CommandWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandWidget()
}

class CommandWidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val commandId = intent.getStringExtra(EXTRA_COMMAND_ID)
        val commandName = intent.getStringExtra(EXTRA_COMMAND_NAME)
        if (
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            commandId.isNullOrBlank() ||
            commandName.isNullOrBlank()
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Timber.d("Binding command $commandId to pinned widget $appWidgetId")
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { preferences ->
                    preferences[CommandWidgetState.commandId] = commandId
                    preferences[CommandWidgetState.commandName] = commandName
                }
                PendingCommandBinding.clear(context, commandId)
                CommandWidget().update(context, glanceId)
            } catch (error: Exception) {
                Timber.e(error, "Unable to bind pinned command widget")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_COMMAND_ID = "commandId"
        const val EXTRA_COMMAND_NAME = "commandName"
    }
}

fun requestPinCommandWidget(
    context: Context,
    commandId: String,
    commandName: String
): Boolean {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

    val provider = ComponentName(context, CommandWidgetReceiver::class.java)
    PendingCommandBinding.save(
        context = context,
        commandId = commandId,
        commandName = commandName,
        existingWidgetIds = appWidgetManager.getAppWidgetIds(provider).toSet()
    )

    val callbackIntent = Intent(context, CommandWidgetPinReceiver::class.java).apply {
        action = "${context.packageName}.BIND_COMMAND_WIDGET"
        data = Uri.Builder()
            .scheme("autopie")
            .authority("command-widget")
            .appendPath(commandId)
            .build()
        putExtra(CommandWidgetPinReceiver.EXTRA_COMMAND_ID, commandId)
        putExtra(CommandWidgetPinReceiver.EXTRA_COMMAND_NAME, commandName)
    }
    val callback = PendingIntent.getBroadcast(
        context,
        commandId.hashCode(),
        callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    )

    val pinExtras = Bundle().apply {
        putString(CommandWidgetPinReceiver.EXTRA_COMMAND_ID, commandId)
        putString(CommandWidgetPinReceiver.EXTRA_COMMAND_NAME, commandName)
    }
    val requested = appWidgetManager.requestPinAppWidget(
        provider,
        pinExtras,
        callback
    )
    if (!requested) PendingCommandBinding.clear(context, commandId)
    return requested
}

private suspend fun bindPendingCommandIfNecessary(context: Context, glanceId: GlanceId) {
    val current = getAppWidgetState(
        context,
        PreferencesGlanceStateDefinition,
        glanceId
    )
    if (current[CommandWidgetState.commandId] != null) return

    val manager = GlanceAppWidgetManager(context)
    val appWidgetId = manager.getAppWidgetId(glanceId)
    val pending = PendingCommandBinding.consumeForNewWidget(context, appWidgetId) ?: return
    Timber.d("Binding pending command ${pending.commandId} to new widget $appWidgetId")
    updateAppWidgetState(context, glanceId) { preferences ->
        preferences[CommandWidgetState.commandId] = pending.commandId
        preferences[CommandWidgetState.commandName] = pending.commandName
    }
}

private data class PendingCommand(
    val commandId: String,
    val commandName: String
)

private object PendingCommandBinding {
    private const val PREFERENCES_NAME = "command_widget_pending_binding"
    private const val KEY_COMMAND_ID = "command_id"
    private const val KEY_COMMAND_NAME = "command_name"
    private const val KEY_EXISTING_WIDGET_IDS = "existing_widget_ids"
    private const val KEY_CREATED_AT = "created_at"
    private const val MAX_BINDING_AGE_MILLIS = 2 * 60 * 1000L

    fun save(
        context: Context,
        commandId: String,
        commandName: String,
        existingWidgetIds: Set<Int>
    ) {
        preferences(context).edit()
            .putString(KEY_COMMAND_ID, commandId)
            .putString(KEY_COMMAND_NAME, commandName)
            .putStringSet(KEY_EXISTING_WIDGET_IDS, existingWidgetIds.map(Int::toString).toSet())
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun consumeForNewWidget(context: Context, appWidgetId: Int): PendingCommand? {
        val preferences = preferences(context)
        val commandId = preferences.getString(KEY_COMMAND_ID, null) ?: return null
        val commandName = preferences.getString(KEY_COMMAND_NAME, null) ?: return null
        val createdAt = preferences.getLong(KEY_CREATED_AT, 0L)
        val existingIds = preferences.getStringSet(KEY_EXISTING_WIDGET_IDS, emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()

        if (
            System.currentTimeMillis() - createdAt > MAX_BINDING_AGE_MILLIS ||
            appWidgetId in existingIds
        ) {
            if (System.currentTimeMillis() - createdAt > MAX_BINDING_AGE_MILLIS) {
                clear(context, commandId)
            }
            return null
        }

        clear(context, commandId)
        return PendingCommand(commandId, commandName)
    }

    fun clear(context: Context, expectedCommandId: String) {
        val preferences = preferences(context)
        if (preferences.getString(KEY_COMMAND_ID, null) != expectedCommandId) return
        preferences.edit().clear().apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}

/**
 * UI-layer entry point for the command execution pipeline. Calling this persists the latest
 * bounded OUTPUT value and asks every widget bound to [commandId] to recompose immediately.
 */
suspend fun updateCommandWidgets(
    context: Context,
    commandId: String,
    commandName: String = commandId,
    rawOutput: String?,
    status: String,
    replaceOutput: Boolean = true
) {
    val widget = CommandWidget()
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(CommandWidget::class.java).forEach { glanceId ->
        val state = getAppWidgetState(
            context,
            PreferencesGlanceStateDefinition,
            glanceId
        )
        val matchesCommand = state[CommandWidgetState.commandId] == commandId ||
            state[CommandWidgetState.commandName] == commandName
        if (!matchesCommand) return@forEach

        updateAppWidgetState(context, glanceId) { preferences ->
            if (replaceOutput) {
                if (rawOutput == null) {
                    preferences.remove(CommandWidgetState.output)
                } else {
                    preferences[CommandWidgetState.output] = boundWidgetOutput(rawOutput)
                }
            }
            preferences[CommandWidgetState.status] = status
        }
        widget.update(context, glanceId)
    }
}

private const val MAX_STORED_OUTPUT_LENGTH = 16 * 1024
private const val MAX_STORED_ITEMS = 50
private const val MAX_STORED_ITEM_LENGTH = 512
