package app.monolauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monolauncher.R
import app.monolauncher.apps.AppEntry
import app.monolauncher.routine.Routine
import app.monolauncher.session.SessionState

/**
 * Home for both session states. ACTIVE: routines, search over registered apps, '종료'.
 * EXITED: '시작', '설정', search over all apps.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, compact: Boolean) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val canControlGrayscale by viewModel.canControlGrayscale.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val active = session == SessionState.ACTIVE

    val searchField = @Composable { SearchField(viewModel.query, onGo = viewModel::launchFirstResult) }
    val searchResults = @Composable { bestAtBottom: Boolean ->
        SearchResults(
            hasQuery = viewModel.query.text.isNotEmpty(),
            results = viewModel.results,
            registeredOnly = active,
            bestAtBottom = bestAtBottom,
            onLaunch = viewModel::launch,
        )
    }
    val top = @Composable {
        SetupBanners(canControlGrayscale, Modifier.padding(bottom = 24.dp))
        ClockHeader(compact)
        Spacer(Modifier.height(if (compact) 32.dp else 48.dp))
        if (active) Routines(routines, onRun = viewModel::runRoutine) else StartPanel(onStart = viewModel::start)
    }

    // Both layouts: only the top content scrolls; status, results, search and the bottom bar are pinned below it.
    // safeDrawingPadding includes the IME, so the weighted column shrinks and the pinned part stays above the keyboard.
    if (compact) {
        // Cover screen: status, results and search sit at the bottom, within thumb reach.
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { top() }
            message?.let { MessageLine(it) }
            searchResults(true)
            searchField()
            BottomBar(active, onExit = viewModel::requestExit, onOpenSettings = viewModel::openSettings)
        }
    } else {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    top()
                    Spacer(Modifier.height(24.dp))
                }
                message?.let { MessageLine(it) }
                // Results grow upward from the field, so the field does not move while typing.
                searchResults(true)
                searchField()
                BottomBar(active, onExit = viewModel::requestExit, onOpenSettings = viewModel::openSettings)
            }
        }
    }
}

@Composable
private fun Routines(routines: RoutineCheck, onRun: (Routine) -> Unit) {
    when (routines) {
        is RoutineCheck.Invalid -> Text(
            stringResource(R.string.routines_error, routines.message),
            color = Mono.Dim,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        is RoutineCheck.Valid -> Column {
            routines.config.routines.forEach { routine ->
                key(routine.id) {
                    Text(
                        routine.label,
                        fontSize = 26.sp,
                        color = Mono.Text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onRun(routine) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StartPanel(onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MonoOutlinedButton(
            text = stringResource(R.string.start),
            onClick = onStart,
            fontSize = 28.sp,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(80.dp),
        )
        Text(stringResource(R.string.start_caption), color = Mono.Muted, fontSize = 14.sp)
    }
}

@Composable
private fun MessageLine(message: HomeMessage) {
    val text = when (message) {
        is HomeMessage.RoutineRunning -> stringResource(R.string.routine_running, message.label)
        HomeMessage.RoutineDone -> stringResource(R.string.routine_done)
        is HomeMessage.RoutineProblem -> message.reason
        HomeMessage.NotRegistered -> stringResource(R.string.launch_not_registered)
        HomeMessage.LaunchFailed -> stringResource(R.string.launch_failed)
    }
    Text(text, color = Mono.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
}

/** Up to five results; the best one is drawn brightest and sits next to the search field. */
@Composable
private fun SearchResults(
    hasQuery: Boolean,
    results: List<AppEntry>,
    registeredOnly: Boolean,
    bestAtBottom: Boolean,
    onLaunch: (AppEntry) -> Unit,
) {
    if (!hasQuery) return
    if (results.isEmpty()) {
        val text = stringResource(if (registeredOnly) R.string.search_no_results_registered else R.string.search_no_results)
        Text(text, color = Mono.Dim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        val ordered = if (bestAtBottom) results.asReversed() else results
        ordered.forEach { entry ->
            key(entry.key) {
                Text(
                    entry.label,
                    fontSize = 20.sp,
                    color = if (entry == results.first()) Mono.Text else Mono.Muted,
                    fontWeight = if (entry == results.first()) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { onLaunch(entry) }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(state: TextFieldState, onGo: () -> Unit) {
    val style = TextStyle(fontSize = 22.sp, color = Mono.Text)
    BasicTextField(
        state = state,
        modifier = Modifier.fillMaxWidth().basicTextMenuOnly(),
        textStyle = style,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Go,
        ),
        onKeyboardAction = { onGo() },
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(Mono.Text),
        decorator = { inner ->
            FieldDecoration(state.text.isEmpty(), stringResource(R.string.search_placeholder), style, inner)
        },
    )
}

@Composable
private fun BottomBar(active: Boolean, onExit: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Spacer(Modifier)
            MonoTextButton(stringResource(R.string.exit), onClick = onExit, color = Mono.Dim, fontSize = 14.sp)
        } else {
            MonoTextButton(stringResource(R.string.settings), onClick = onOpenSettings, color = Mono.Muted)
            Spacer(Modifier)
        }
    }
}
