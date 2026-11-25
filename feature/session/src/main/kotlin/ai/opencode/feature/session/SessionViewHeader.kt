package ai.opencode.feature.session

import ai.opencode.core.strings.localized
import ai.opencode.core.R as CoreR
import ai.opencode.core.motion.transitionBlur

import ai.opencode.core.motion.Motion
import ai.opencode.core.motion.MotionPhase
import ai.opencode.core.motion.MotionSpring
import ai.opencode.core.motion.rememberReduceMotion
import ai.opencode.core.sharing.SharedKeys
import ai.opencode.core.sharing.shared
import ai.opencode.core.store.StoreOf
import ai.opencode.core.store.observe
import ai.opencode.core.ui.AccessibilityFocusTarget
import ai.opencode.core.ui.OpenCodeColors
import ai.opencode.core.ui.OpenCodeIcon
import ai.opencode.core.ui.OpenCodeIconButton
import ai.opencode.core.ui.OpenCodeIconButtonStyle
import ai.opencode.core.ui.OpenCodeIconSize
import ai.opencode.core.ui.OpenCodeShape
import ai.opencode.core.ui.OpenCodeSurfaces
import ai.opencode.core.ui.OpenCodeTheme
import ai.opencode.core.ui.SFSymbol
import ai.opencode.core.ui.accessibilityFocusRequest
import ai.opencode.core.ui.accessibilityFocusTarget
import ai.opencode.core.ui.openCodeTapTarget
import ai.opencode.core.ui.rememberAccessibilityFocusRequest
import ai.opencode.feature.composer.EscapeHandler
import ai.opencode.feature.session.SessionFeature.Action
import ai.opencode.feature.session.SessionFeature.State
import ai.opencode.feature.session.header.BreathingTitleHold
import ai.opencode.feature.session.header.HeaderBlurTunables
import ai.opencode.feature.session.header.SessionContextGauge
import ai.opencode.feature.session.header.SessionContextText
import ai.opencode.server.model.OpenCodeExportFormat
import ai.opencode.server.model.isUntitled
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max

// The header row (drawer or Back, the title capsule, Back to main session and the … session menu),
// and the session menu's items. The header's Liquid Glass controls are M3 tonal surfaces; the …
// menu is a Material dropdown that runs the Session sheet's actions without opening it
// (`Action.SessionMenu`).

/** The header's controls: the 44 pt glass circle, as a 48 dp tonal circle (the minimum target). */
internal val headerButtonSize: Dp = max(HeaderBlurTunables.buttonSize.value, 48f).dp

/** The AX sizes start at about 1.65× body. */
internal fun isAccessibilityFontScale(fontScale: Float): Boolean = fontScale >= 1.65f

/** What the header reads from the session, observed as one value so the header recomposes only when it changes. */
@Immutable
internal data class SessionHeaderModel(
    val titleText: String?,
    val draftProjectName: String?,
    val isDraft: Boolean,
    val isSyncing: Boolean,
    val isLastKnown: Boolean,
    val autoRenameFailed: Boolean,
    val isBusy: Boolean,
    val isResyncing: Boolean,
    val refreshFailed: Boolean,
    val canChooseProject: Boolean,
    val identity: TranscriptIdentity,
    val sessionID: String?,
    val sessionTitle: String?,
    val familyRootID: String?,
    val contextFraction: Double?,
    val contextTokens: Int?,
    val contextLimit: Int?,
    /** What Rename starts from: the server's title, or nothing while the session reads as its first prompt. */
    val renameTitle: String = "",
) {
    /** A subagent family's root is elsewhere: Back to main session. */
    val backToRootID: String? get() = familyRootID?.takeIf { it != sessionID }

    companion object {
        fun from(state: State): SessionHeaderModel {
            val titleText = headerTitleText(state)
            val isDraft = state.isDraft
            // A session just made from this draft reads New Session as the draft did, without a resync's breathing.
            val isSyncing = !isDraft &&
                !(state.session?.id == state.draftBecameSessionID && readsNewSession(state)) &&
                (state.isResyncing || state.sessionSheet?.operation == SessionSheetFeature.Operation.AutoRenaming)
            return SessionHeaderModel(
                titleText = titleText,
                draftProjectName = if (isDraft || state.session?.id == state.draftBecameSessionID) state.draftProject?.name else null,
                isDraft = isDraft,
                isSyncing = isSyncing,
                isLastKnown = !isDraft && state.refreshFailed,
                autoRenameFailed = state.sessionSheet?.autoRenameFailed == true,
                isBusy = state.isBusy,
                isResyncing = state.isResyncing,
                refreshFailed = state.refreshFailed,
                canChooseProject = state.canChooseProject,
                identity = state.transcriptIdentity,
                sessionID = state.session?.id,
                sessionTitle = state.session?.title,
                familyRootID = state.familyRootID,
                contextFraction = state.contextFraction,
                contextTokens = state.contextUsage?.tokens,
                contextLimit = state.contextLimit,
                renameTitle = state.session?.let { if (it.isUntitled) "" else it.title }.orEmpty(),
            )
        }

        /**
         * What the header capsule reads: a draft's New Session, and once its session exists the
         * session's title. The server's placeholder title reads the same as the draft's, so the
         * first send changes nothing here; a real title replaces it in the same capsule.
         */
        fun headerTitleText(state: State): String? {
            val session = state.session ?: return if (state.isDraft) localized(R.string.session_new_session_title) else null
            if (readsNewSession(state)) return localized(R.string.session_new_session_title)
            return session.title
        }

        /** True when a session's title is empty or the server's “New session” placeholder, which the header reads as New Session. */
        private fun readsNewSession(state: State): Boolean {
            val session = state.session ?: return state.isDraft
            val title = session.title.trim()
            return title.isEmpty() || title.equals("New session", ignoreCase = true)
        }
    }
}

/**
 * The header row: the drawer button (or Back on a pushed subagent, nothing on a Catch Up card), the
 * title capsule centred on the header, and the trailing controls. Reports its laid-out height for
 * the backdrop.
 *
 * @param navigationIcon The drawer button Home supplies.
 * @param onHeaderHeight the row's height in dp, whenever it changes.
 */
@Composable
internal fun SessionHeaderControls(
    store: StoreOf<State, Action>,
    listContext: SessionListContext,
    navigationIcon: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    onCatchUpOpen: (() -> Unit)?,
    onHeaderHeight: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model by store.observe(SessionHeaderModel::from)
    val reducedMotion = rememberReduceMotion()
    val density = LocalDensity.current
    // While the drawer button's label shows, the title steps aside under it.
    val noticeCoversTitle = onBack == null && listContext.notice != null

    // A Material top app bar: start-aligned title, plain icon buttons, 64 dp tall.
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            // Measured with the card's top inset, so the scrolled bar and the transcript's top
            // clear the whole header.
            .onSizeChanged { onHeaderHeight(with(density) { it.height.toDp().value }) }
            .padding(top = if (onCatchUpOpen == null) 0.dp else CATCH_UP_COMPOSER_INSET.dp)
            .heightIn(min = TOP_APP_BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> BackControl(onBack)
            onCatchUpOpen == null -> navigationIcon()
        }
        Box(
            Modifier
                .weight(1f)
                .steppingAside(noticeCoversTitle, reducedMotion),
            contentAlignment = Alignment.CenterStart,
        ) {
            HeaderTitle(store, model, listContext, onCatchUpOpen, reducedMotion)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.backToRootID?.let { rootID ->
                FloatingControl(SFSymbol.ArrowUturnBackward, stringResource(R.string.session_back_to_main_description)) { store.send(Action.FamilyPicked(rootID)) }
            }
            // The context ring alone, in its own circle: no dots, no menu. A tap opens the session info.
            if (onCatchUpOpen == null && model.sessionID != null) ContextRingBubble(store, model)
        }
    }
}

private const val CATCH_UP_COMPOSER_INSET = 12f

/** Material 3's small top app bar height. */
private val TOP_APP_BAR_HEIGHT = 64.dp

/** The drawer button's label grows over the title: it fades and blurs out of the way, and back once it has folded past. */
private fun Modifier.steppingAside(aside: Boolean, reducedMotion: Boolean): Modifier = composed {
    val spec = when {
        reducedMotion -> tween<Float>(250, easing = Motion.EaseInOut)
        aside -> tween(180, easing = Motion.EaseOut)
        else -> asideSmooth.animation<Float>(reduceMotion = false, delaySeconds = 0.12)
    }
    val progress by animateFloatAsState(if (aside) 1f else 0f, spec, label = "steppingAside")
    this
        .alpha(1 - progress)
        .transitionBlur(if (reducedMotion) 0.dp else (6 * progress).dp)
        .then(if (aside) Modifier.clearAndSetSemantics {} else Modifier)
}

private val asideSmooth = MotionSpring(0.35, 0.0)

/** A pushed subagent's way back to its parent, where the drawer button is on the root session: the same circle. */
@Composable
private fun BackControl(onBack: () -> Unit) {
    OpenCodeIconButton(
        symbol = SFSymbol.ChevronBackward,
        contentDescription = stringResource(CoreR.string.common_back),
        onClick = onBack,
        size = headerButtonSize,
        style = OpenCodeIconButtonStyle.Circle,
        modifier = Modifier.testTag("subagent-back"),
    )
}

@Composable
internal fun FloatingControl(symbol: SFSymbol, label: String, onClick: () -> Unit) {
    OpenCodeIconButton(symbol = symbol, contentDescription = label, onClick = onClick, size = headerButtonSize, style = OpenCodeIconButtonStyle.Circle)
}

/** Secondary under high contrast, else primary at 75 %. */
internal fun headerProjectColor(colors: OpenCodeColors): Color =
    if (colors.highContrast) colors.secondaryText else colors.text.copy(alpha = 0.75f)

/**
 * The header's one title capsule. A draft and the session it becomes share it, so creating the
 * session never replaces or moves it. A draft's capsule chooses its project; a session's shows
 * its details; a Catch Up card's opens the session.
 */
@Composable
private fun HeaderTitle(
    store: StoreOf<State, Action>,
    model: SessionHeaderModel,
    listContext: SessionListContext,
    onCatchUpOpen: (() -> Unit)?,
    reducedMotion: Boolean,
) {
    val latestTitle = model.titleText ?: return
    // The capsule's second line: the project, which a draft knows before the session list does.
    val project = listContext.project?.name ?: model.draftProjectName
    val projectColor = headerProjectColor(OpenCodeTheme.colors)
    val fontScale = LocalDensity.current.fontScale
    // TalkBack goes back to the title once Session Info, opened from it, closes, as VoiceOver
    // returns to the button that presented the sheet; with the keyboard up it landed on the
    // drawer button.
    var returnsFocusAfterInfo by remember { mutableStateOf(false) }
    var infoCloses by remember { mutableIntStateOf(0) }
    val showsInfo by store.observe { it.activePicker == State.ActivePicker.session }
    LaunchedEffect(showsInfo) {
        if (showsInfo || !returnsFocusAfterInfo) return@LaunchedEffect
        returnsFocusAfterInfo = false
        infoCloses++
    }
    val infoFocus = rememberAccessibilityFocusRequest(infoCloses.takeIf { it > 0 })
    // Auto Rename's new title waits for the breath to ease back to full; another session switched
    // in place shows its own title at once.
    BreathingTitleHold(identity = model.identity, title = latestTitle, isBreathing = model.isSyncing) { title, animates ->
        val subtitle = if (model.autoRenameFailed) stringResource(R.string.session_sheet_rename_failed) else project
        val titleDescription = if (subtitle == null) title else stringResource(R.string.session_list_two, title, subtitle)
        val onClick: () -> Unit = onCatchUpOpen
            ?: {
                if (!model.isDraft) returnsFocusAfterInfo = true
                store.send(if (model.isDraft) Action.ProjectButtonTapped else Action.SessionInfoTapped)
            }
        val hint = when {
            onCatchUpOpen != null -> stringResource(R.string.session_header_open_from_catch_up_label)
            model.isDraft -> if (model.canChooseProject) stringResource(R.string.session_draft_project_choose_label) else null
            else -> stringResource(R.string.session_header_show_details_label)
        }
        val value = if (model.isDraft) {
            ""
        } else {
            val activity = if (model.isBusy) stringResource(R.string.session_activity_running_description) else stringResource(R.string.session_header_idle_description)
            val freshness = if (model.isResyncing) stringResource(R.string.session_header_syncing_description) else if (model.refreshFailed) stringResource(R.string.session_header_last_known_description) else null
            if (freshness == null) activity else stringResource(R.string.session_list_two, activity, freshness)
        }
        // The title is its own bubble (a pill), like the circles beside it.
        Surface(
            shape = OpenCodeShape.capsule,
            color = OpenCodeSurfaces.neutralFill,
            tonalElevation = 0.dp,
            modifier = Modifier
                .clip(OpenCodeShape.capsule)
                .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
                .openCodeTapTarget()
                .testTag(if (model.isDraft) "draft-title" else "session-title")
                // Ahead of the clearing below, which would drop its marker.
                .accessibilityFocusRequest(infoFocus)
                .clearAndSetSemantics {
                    contentDescription = titleDescription
                    if (value.isNotEmpty()) stateDescription = value
                },
        ) {
            Column(
                Modifier
                    // The header doesn't carry the screen's transactions: the capsule animates its own width.
                    .then(if (!reducedMotion && animates) Modifier.animateContentSize(Motion.layout.animation(reduceMotion = false)) else Modifier)
                    .heightIn(min = headerButtonSize)
                    .padding(horizontal = 18.dp, vertical = if (isAccessibilityFontScale(fontScale)) 8.dp else 3.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SyncingSessionTitle(
                        title = title,
                        isSyncing = model.isSyncing,
                        isLastKnown = model.isLastKnown,
                        animates = animates,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (onCatchUpOpen != null) {
                        OpenCodeIcon(SFSymbol.ChevronRight, contentDescription = null, size = 12.dp, tint = OpenCodeTheme.colors.tertiaryText)
                    }
                }
                AnimatedVisibility(
                    visible = subtitle != null,
                    enter = fadeIn(Motion.layout.animation(reduceMotion = reducedMotion)),
                    exit = fadeOut(Motion.layout.animation(reduceMotion = reducedMotion)),
                ) {
                    val shown = subtitle ?: ""
                    if (model.autoRenameFailed) {
                        Text(
                            shown,
                            style = MaterialTheme.typography.labelMedium,
                            color = projectColor,
                            maxLines = 1,
                        )
                    } else {
                        ProjectHeaderSubtitle(shown, animates = animates) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = projectColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The context gauge in its own circle (it used to ring the ⋮ button). Shows only once the session has a context. */
@Composable
private fun ContextRingBubble(store: StoreOf<State, Action>, model: SessionHeaderModel) {
    val fraction = model.contextFraction ?: return
    val value = model.contextTokens?.let { SessionContextText.accessibilityValue(it, model.contextLimit) } ?: ""
    val label = stringResource(R.string.session_sheet_context)
    Box(
        Modifier
            .size(headerButtonSize)
            .clip(OpenCodeShape.circle)
            .background(OpenCodeSurfaces.neutralFill)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.session_header_show_details_label)) { store.send(Action.SessionInfoTapped) }
            .testTag("session-context")
            .clearAndSetSemantics {
                contentDescription = label
                if (value.isNotEmpty()) stateDescription = value
            },
    ) {
        SessionContextGauge(fraction = fraction, modifier = Modifier.fillMaxSize().padding(10.dp))
    }
}
