package app.monolauncher.ui.settings

import android.Manifest
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.monolauncher.R
import app.monolauncher.search.SearchEngine
import app.monolauncher.ui.AdbCommand
import app.monolauncher.ui.LocationPermissions
import app.monolauncher.ui.hasLocationPermission
import app.monolauncher.ui.Mono
import app.monolauncher.ui.MonoTextButton
import app.monolauncher.ui.MonoTextField
import app.monolauncher.ui.RoutineCheck
import app.monolauncher.ui.SectionLabel
import app.monolauncher.ui.hasDndAccess
import app.monolauncher.ui.isDefaultHome
import app.monolauncher.ui.openSystemSettings
import app.monolauncher.ui.rememberRequestDefaultHome
import app.monolauncher.ui.rememberStatusOnResume
import app.monolauncher.ui.versionName
import kotlinx.coroutines.Dispatchers

private enum class Section(@StringRes val title: Int) {
    REGISTERED(R.string.settings_registered),
    ALIASES(R.string.settings_aliases),
    VARIABLES(R.string.settings_variables),
    ROUTINES(R.string.settings_routines),
    PERMISSIONS(R.string.settings_permissions),
}

/** Settings, shown only in the EXITED state. A menu of sections; back returns to the menu, then home. */
@Composable
fun SettingsScreen(
    compact: Boolean,
    canControlGrayscale: Boolean,
    onClose: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    var section by rememberSaveable { mutableStateOf<Section?>(null) }
    BackHandler(enabled = section != null) { section = null }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .then(if (compact) Modifier else Modifier.widthIn(max = 560.dp))
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoTextButton(
                    text = stringResource(if (section == null) R.string.close else R.string.back),
                    onClick = { if (section == null) onClose() else section = null },
                    color = Mono.Muted,
                )
                Text(
                    stringResource(section?.title ?: R.string.settings),
                    fontSize = 22.sp,
                    color = Mono.Text,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            when (section) {
                null -> SettingsMenu(viewModel, canControlGrayscale, onSelect = { section = it })
                Section.REGISTERED -> RegisteredApps(viewModel)
                Section.ALIASES -> Aliases(viewModel)
                Section.VARIABLES -> Variables(viewModel)
                Section.ROUTINES -> RoutinesEditor(viewModel)
                Section.PERMISSIONS -> Permissions(canControlGrayscale)
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsMenu(viewModel: SettingsViewModel, canControlGrayscale: Boolean, onSelect: (Section) -> Unit) {
    val context = LocalContext.current
    val registered by viewModel.registered.collectAsStateWithLifecycle()
    val isDefaultHome = rememberStatusOnResume { isDefaultHome() }
    val hasDnd = rememberStatusOnResume { hasDndAccess() }
    val version = remember(context) { context.versionName() }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Section.entries.forEach { s ->
            val detail = when (s) {
                Section.REGISTERED -> stringResource(R.string.registered_count, registered.size)
                Section.PERMISSIONS ->
                    if (canControlGrayscale && isDefaultHome && hasDnd) null else stringResource(R.string.needs_attention)
                else -> null
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelect(s) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(s.title), fontSize = 20.sp, color = Mono.Text, modifier = Modifier.weight(1f))
                detail?.let { Text(it, fontSize = 15.sp, color = Mono.Muted) }
            }
        }
    }
    Text(stringResource(R.string.version, version), color = Mono.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun ColumnScope.RegisteredApps(viewModel: SettingsViewModel) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val registered by viewModel.registered.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }

    val engine = remember(apps) { SearchEngine(apps.map { it.toSearchItem() }) }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val shown = remember(apps, filter) {
        if (filter.isBlank()) apps else engine.search(filter, limit = apps.size).mapNotNull { byKey[it.key] }
    }
    val notInstalled = remember(apps, registered) {
        (registered - apps.map { it.packageName }.toSet()).sorted()
    }

    MonoTextField(filter, { filter = it }, placeholder = stringResource(R.string.filter_placeholder))
    Text(
        stringResource(R.string.registered_count, registered.size) + " · " + stringResource(R.string.registered_hint),
        color = Mono.Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    LazyColumn(Modifier.weight(1f)) {
        items(notInstalled, key = { "missing:$it" }) { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pkg, color = Mono.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.not_installed), color = Mono.Dim, fontSize = 12.sp)
                }
                MonoTextButton(stringResource(R.string.unregister), onClick = { viewModel.setRegistered(pkg, false) })
            }
        }
        items(shown, key = { it.packageName }) { app ->
            val checked = app.packageName in registered
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox) { viewModel.setRegistered(app.packageName, it) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                Column {
                    Text(app.label, color = Mono.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = Mono.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Aliases(viewModel: SettingsViewModel) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val registered by viewModel.registered.collectAsStateWithLifecycle()
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()
    val rows = remember(apps, registered) {
        val labels = apps.associate { it.packageName to it.label }
        registered.map { it to (labels[it] ?: it) }.sortedBy { it.second }
    }

    Text(stringResource(R.string.aliases_hint), color = Mono.Muted, fontSize = 13.sp)
    if (rows.isEmpty()) {
        Text(stringResource(R.string.aliases_empty), color = Mono.Dim, modifier = Modifier.padding(top = 16.dp))
        return
    }
    LazyColumn(Modifier.weight(1f)) {
        items(rows, key = { it.first }) { (pkg, label) ->
            var text by rememberSaveable { mutableStateOf(aliases[pkg].orEmpty().joinToString(", ")) }
            Column(Modifier.padding(top = 16.dp)) {
                SectionLabel(label)
                MonoTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        viewModel.setAliases(pkg, it)
                    },
                    placeholder = stringResource(R.string.aliases_placeholder),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Variables(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val names by viewModel.variableNames.collectAsStateWithLifecycle()
    val values by viewModel.variables.collectAsStateWithLifecycle()
    // In the view model, so a fix that lands after a fold/unfold recreation still reaches this screen.
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val locationNotice by viewModel.locationNotice.collectAsStateWithLifecycle()

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onLocationPermissionResult(
            fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.variables_hint), color = Mono.Muted, fontSize = 13.sp)
        MonoTextButton(
            text = stringResource(if (locating) R.string.location_locating else R.string.location_save_home),
            onClick = {
                if (!locating) {
                    if (context.hasLocationPermission()) viewModel.saveCurrentLocation()
                    else requestPermission.launch(LocationPermissions)
                }
            },
            modifier = Modifier.padding(top = 12.dp),
        )
        locationNotice?.let { Text(stringResource(it.text), color = Mono.Muted, fontSize = 13.sp) }
        names.forEach { name ->
            key(name) {
                val stored = values[name].orEmpty()
                var text by rememberSaveable { mutableStateOf(stored) }
                // Pick up writes from elsewhere (a location fix), but not this field's own (trimmed) edits.
                LaunchedEffect(stored) { if (stored != text.trim()) text = stored }
                Column(Modifier.padding(top = 16.dp)) {
                    SectionLabel(name)
                    MonoTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            viewModel.setVariable(name, it)
                        },
                    )
                }
            }
        }
    }
}

@get:StringRes
private val LocationNotice.text: Int
    get() = when (this) {
        LocationNotice.SAVED -> R.string.location_saved
        LocationNotice.SERVICES_OFF -> R.string.location_services_off
        LocationNotice.FAILED -> R.string.location_failed
        LocationNotice.DENIED -> R.string.location_denied
        LocationNotice.PRECISE_NEEDED -> R.string.location_precise_needed
    }

private sealed interface EditorFeedback {
    data class Checked(val result: RoutineCheck, val saved: Boolean) : EditorFeedback
    data object Reset : EditorFeedback
}

@Composable
private fun ColumnScope.RoutinesEditor(viewModel: SettingsViewModel) {
    val saved by viewModel.routinesJson.collectAsStateWithLifecycle()
    // The draft lives in the view model so a HOME press (which closes settings) does not drop it.
    // Immediate dispatch updates the field synchronously on each keystroke, as local state would
    // (a text field fed asynchronously can break Hangul composition).
    val draft by viewModel.routineDraft.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val editor = draft ?: saved
    var feedback by remember { mutableStateOf<EditorFeedback?>(null) }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.routines_help), color = Mono.Muted, fontSize = 13.sp)
        MonoTextField(
            value = editor,
            onValueChange = {
                viewModel.editRoutines(it)
                feedback = null
            },
            singleLine = false,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            modifier = Modifier.heightIn(min = 240.dp).padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
            MonoTextButton(stringResource(R.string.routines_validate), onClick = {
                feedback = EditorFeedback.Checked(viewModel.validate(editor), saved = false)
            })
            MonoTextButton(stringResource(R.string.routines_save), onClick = {
                feedback = EditorFeedback.Checked(viewModel.save(editor), saved = true)
            })
            MonoTextButton(stringResource(R.string.routines_reset), color = Mono.Muted, onClick = {
                viewModel.resetRoutines()
                feedback = EditorFeedback.Reset
            })
        }
        feedback?.let { Text(feedbackText(it), color = Mono.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun feedbackText(feedback: EditorFeedback): String = when (feedback) {
    EditorFeedback.Reset -> stringResource(R.string.routines_reset_done)
    is EditorFeedback.Checked -> when (val result = feedback.result) {
        is RoutineCheck.Invalid -> stringResource(R.string.routines_invalid, result.message)
        is RoutineCheck.Valid -> stringResource(
            if (feedback.saved) R.string.routines_saved else R.string.routines_valid,
            result.config.routines.size,
        )
    }
}

@Composable
private fun ColumnScope.Permissions(canControlGrayscale: Boolean) {
    val context = LocalContext.current
    val isDefaultHome = rememberStatusOnResume { isDefaultHome() }
    val hasDnd = rememberStatusOnResume { hasDndAccess() }
    val requestDefaultHome = rememberRequestDefaultHome()

    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PermissionItem(R.string.perm_grayscale, canControlGrayscale) {
            Text(stringResource(R.string.perm_grayscale_how), color = Mono.Muted, fontSize = 13.sp)
            AdbCommand()
        }
        PermissionItem(R.string.perm_default_home, isDefaultHome) {
            Row {
                MonoTextButton(stringResource(R.string.set_default_home), onClick = requestDefaultHome)
                MonoTextButton(
                    stringResource(R.string.open_home_settings),
                    color = Mono.Muted,
                    onClick = { context.openSystemSettings(Settings.ACTION_HOME_SETTINGS) },
                )
            }
        }
        PermissionItem(R.string.perm_dnd, hasDnd) {
            Text(stringResource(R.string.perm_dnd_why), color = Mono.Muted, fontSize = 13.sp)
            MonoTextButton(
                stringResource(R.string.open_settings),
                onClick = { context.openSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
            )
        }
    }
}

/** A permission line with its status; [fix] is shown only while it is missing. */
@Composable
private fun PermissionItem(@StringRes title: Int, granted: Boolean, fix: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), fontSize = 18.sp, color = Mono.Text, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (granted) R.string.perm_granted else R.string.perm_missing),
                color = if (granted) Mono.Muted else Mono.Text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (!granted) fix()
    }
}
