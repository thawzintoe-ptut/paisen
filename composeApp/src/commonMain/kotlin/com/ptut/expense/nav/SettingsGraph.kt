package com.ptut.expense.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ptut.expense.categories.CategoriesView
import com.ptut.expense.categories.CategoriesViewModel
import com.ptut.expense.categories.EditCategoryView
import com.ptut.expense.categories.EditCategoryViewModel
import com.ptut.expense.debug.DebugSlipPanel
import com.ptut.expense.debug.DebugSlipViewModel
import com.ptut.expense.design.Spacing
import com.ptut.expense.di.AppBuild
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.folders.PhotoFoldersView
import com.ptut.expense.folders.PhotoFoldersViewModel
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.lock.AppLock
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.settings.AssistantModelView
import com.ptut.expense.settings.AssistantModelViewModel
import com.ptut.expense.settings.BackgroundImportView
import com.ptut.expense.settings.BackgroundImportViewModel
import com.ptut.expense.settings.ExportDataView
import com.ptut.expense.settings.FEEDBACK_ADDRESS
import com.ptut.expense.settings.HomeCurrencyView
import com.ptut.expense.settings.HomeCurrencyViewModel
import com.ptut.expense.settings.ImportDataView
import com.ptut.expense.settings.KeepFailedSlipsView
import com.ptut.expense.settings.KeepFailedSlipsViewModel
import com.ptut.expense.settings.LanguageView
import com.ptut.expense.settings.LanguageViewModel
import com.ptut.expense.settings.LedgerBackupViewModel
import com.ptut.expense.settings.LicencesView
import com.ptut.expense.settings.LicencesViewModel
import com.ptut.expense.settings.SettingsAction
import com.ptut.expense.settings.SettingsAdvancedView
import com.ptut.expense.settings.SettingsRow
import com.ptut.expense.settings.SettingsRowUiModel
import com.ptut.expense.settings.SettingsView
import com.ptut.expense.settings.SettingsViewModel
import com.ptut.expense.settings.SlipOwnerDialog
import com.ptut.expense.settings.TimezoneView
import com.ptut.expense.settings.TimezoneViewModel
import com.ptut.expense.settings.destination
import com.ptut.expense.settings.feedbackForm
import com.ptut.expense.settings.feedbackMail
import com.ptut.expense.settings.focusedRow
import com.ptut.expense.shared.AccountCard
import com.ptut.expense.shared.AccountUiState
import com.ptut.expense.shared.AccountViewModel
import com.ptut.expense.shared.SignInSheet
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyGroupedCard
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Settings and every screen a Settings row opens. */
internal fun NavGraphBuilder.settingsRoutes(nav: NavHostController) {
    composable<PtutRoute.Settings> { entry ->
        SettingsHost(nav, focus = entry.toRoute<PtutRoute.Settings>().focusedRow(), advanced = false)
    }

    composable<PtutRoute.SettingsAdvanced> { entry ->
        SettingsHost(nav, focus = entry.toRoute<PtutRoute.SettingsAdvanced>().focusedRow(), advanced = true)
    }

    composable<PtutRoute.BackgroundImport> {
        val background = koinViewModel<BackgroundImportViewModel>()
        val state by background.uiState.collectAsStateWithLifecycle()
        BackgroundImportView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onSetRunning = background::setRunning,
            onCheckNow = background::checkNow,
        )
    }

    composable<PtutRoute.KeepFailedSlips> {
        val keep = koinViewModel<KeepFailedSlipsViewModel>()
        val state by keep.uiState.collectAsStateWithLifecycle()
        KeepFailedSlipsView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onChoose = keep::choose,
        )
    }

    // One class for both ends of FR-22. Each screen resolves its own instance,
    // which is fine — they are separate destinations, so only one is ever on top.
    composable<PtutRoute.ExportData> {
        val backup = koinViewModel<LedgerBackupViewModel>()
        val state by backup.uiState.collectAsStateWithLifecycle()
        ExportDataView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onExport = backup::export,
        )
    }

    composable<PtutRoute.ImportData> {
        val backup = koinViewModel<LedgerBackupViewModel>()
        val state by backup.uiState.collectAsStateWithLifecycle()
        ImportDataView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onChooseFile = backup::chooseFile,
            onImport = backup::restore,
            onCancel = backup::cancelRestore,
        )
    }

    composable<PtutRoute.Language> {
        val language = koinViewModel<LanguageViewModel>()
        val state by language.uiState.collectAsStateWithLifecycle()
        LanguageView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onChoose = language::choose,
        )
    }

    composable<PtutRoute.HomeCurrency> {
        val homeCurrency = koinViewModel<HomeCurrencyViewModel>()
        val state by homeCurrency.uiState.collectAsStateWithLifecycle()
        HomeCurrencyView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onChoose = homeCurrency::choose,
            onRate = homeCurrency::typeRate,
            onConfirmConvert = homeCurrency::confirmConvert,
            onCancelConvert = homeCurrency::cancelConvert,
            onExport = { nav.navigate(PtutRoute.ExportData) },
        )
    }

    composable<PtutRoute.Timezone> {
        TimezoneView(
            uiState = koinViewModel<TimezoneViewModel>().uiState,
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.AssistantModel> {
        val assistant = koinViewModel<AssistantModelViewModel>()
        val state by assistant.uiState.collectAsStateWithLifecycle()
        AssistantModelView(
            uiState = state,
            onAct = assistant::act,
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.Licences> {
        val licences = koinViewModel<LicencesViewModel>()
        val state by licences.uiState.collectAsStateWithLifecycle()
        LicencesView(
            uiState = state,
            onToggle = licences::toggle,
            onBack = { nav.popBackStack() },
        )
    }

    composable<PtutRoute.Categories> {
        val categories = koinViewModel<CategoriesViewModel>()
        val state by categories.uiState.collectAsStateWithLifecycle()
        val created by categories.justCreated.collectAsStateWithLifecycle()

        // A new category is opened for editing at once: naming it is the first thing
        // screen 12a is for, so asking in a dialog first would ask twice.
        LaunchedEffect(created) {
            created?.let {
                categories.openedCreated()
                nav.navigate(PtutRoute.EditCategory(it, isNew = true))
            }
        }

        CategoriesView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onOpen = { nav.navigate(PtutRoute.EditCategory(it)) },
            onNew = { categories.newCategory() },
            onMove = { id, by -> categories.move(id, by) },
        )
    }

    composable<PtutRoute.EditCategory> { entry ->
        val route = entry.toRoute<PtutRoute.EditCategory>()
        val edit = koinViewModel<EditCategoryViewModel> { parametersOf(route.categoryId, route.isNew) }
        val state by edit.uiState.collectAsStateWithLifecycle()
        val deleted by edit.isDeleted.collectAsStateWithLifecycle()

        // The category is gone before the screen is, so leaving cannot cancel the
        // delete — the order the record screen already uses.
        LaunchedEffect(deleted) { if (deleted) nav.popBackStack() }

        EditCategoryView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onRename = edit::rename,
            onIcon = edit::chooseIcon,
            onDirection = edit::chooseDirection,
            onColor = edit::chooseColor,
            onSave = { edit.save() },
            onAddSubCategory = { edit.addSubCategory(it) },
            onRenameSubCategory = { id, name -> edit.renameSubCategory(id, name) },
            onRemoveSubCategory = { edit.removeSubCategory(it) },
            onDelete = { edit.delete(it) },
            onTarget = edit::typeTarget,
        )
    }

    composable<PtutRoute.PhotoFolders> {
        val folders = koinViewModel<PhotoFoldersViewModel>()
        val state by folders.uiState.collectAsStateWithLifecycle()

        PhotoFoldersView(
            uiState = state,
            onBack = { nav.popBackStack() },
            onToggle = folders::onToggle,
            onSave = { folders.save() },
            onChooseAnother = { folders.chooseMore() },
        )
        // Access granted in the system settings shows on return, not after a restart (#217).
        LifecycleResumeEffect(folders) {
            folders.refresh()
            onPauseOrDispose { }
        }
    }
}

/** Settings, or its Advanced screen (#101): one set of row taps and switches for both. */
@Composable
private fun SettingsHost(nav: NavHostController, focus: SettingsAction?, advanced: Boolean) {
    val settings = koinViewModel<SettingsViewModel>()
    val state by settings.uiState.collectAsStateWithLifecycle()
    // The switch in the background import row is that screen's own lever (#168).
    val background = koinViewModel<BackgroundImportViewModel>()
    val uriHandler = LocalUriHandler.current
    val build = koinInject<AppBuild>()
    val toasts = koinInject<ToastMessages>()
    val scope = rememberCoroutineScope()
    val noEmailApp = LocalCopy.current.settings.noEmailApp(FEEDBACK_ADDRESS)
    val deviceAuth = koinInject<DeviceAuth>()
    val appLock = koinInject<AppLock>()
    val unlockReason = LocalCopy.current.settings.unlockReason
    // #26: the one row that opens a dialog, not a screen.
    var editingOwner by rememberSaveable { mutableStateOf(false) }
    // The two rows that open a sheet over Settings: the account, and the small help list.
    var accountSheet by rememberSaveable { mutableStateOf(AccountSheet.None) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val account = koinViewModel<AccountViewModel>()
    val accountState by account.uiState.collectAsStateWithLifecycle()
    // Picked once at the tap, so signing out inside the card closes it instead of swapping in the sign-in sheet.
    val openAccount = { accountSheet = if (accountState is AccountUiState.SignedIn) AccountSheet.Account else AccountSheet.SignIn }
    LaunchedEffect(accountState) {
        if (accountSheet == AccountSheet.Account && accountState !is AccountUiState.SignedIn) accountSheet = AccountSheet.None
    }
    if (editingOwner) {
        SlipOwnerDialog(
            current = koinInject<AppSettings>().choices.value.slipOwner.name,
            onSave = {
                settings.setSlipOwner(it)
                editingOwner = false
            },
            onDismiss = { editingOwner = false },
        )
    }
    // Every row but a few is a destination, so the screen navigates and the state holder holds no
    // action — an operation started here would report its result over whichever screen the reader
    // had moved on to.
    val onAction: (SettingsAction) -> Unit = { action ->
        when (action) {
            // The form in the browser, else the phone's own mail app. Android throws when it has neither.
            SettingsAction.Feedback -> runCatching { uriHandler.openUri(feedbackForm(build)) }
                .recoverCatching { uriHandler.openUri(feedbackMail(build)) }
                .onFailure { scope.launch { toasts.show(ToastMessage(noEmailApp, ToastTone.Error)) } }

            SettingsAction.SlipOwner -> editingOwner = true

            SettingsAction.NoteTone -> settings.switchNoteTone()

            // #101: the fix the reader was sent for travels on, so its row is marked there too.
            SettingsAction.Advanced -> nav.navigate(PtutRoute.SettingsAdvanced(focus?.name)) { launchSingleTop = true }

            SettingsAction.GoalJar -> nav.navigate(PtutRoute.GoalJar(goalId = state.goalId)) { launchSingleTop = true }

            SettingsAction.SharedAccount -> openAccount()

            SettingsAction.Help -> helpOpen = true

            else -> action.destination()?.let { nav.navigate(it) }
        }
    }
    if (helpOpen) {
        SkyBottomSheet(onDismissRequest = { helpOpen = false }) {
            SkyGroupedCard(
                rows = state.help.map<SettingsRowUiModel, @Composable () -> Unit> { row ->
                    {
                        SettingsRow(row = row, onClick = {
                            helpOpen = false
                            onAction(row.id)
                        })
                    }
                },
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            )
        }
    }
    when (accountSheet) {
        AccountSheet.None -> Unit

        // Signed in: the card with sign out and delete. Signed out: straight to the sign-in sheet.
        AccountSheet.Account -> SkyBottomSheet(onDismissRequest = { accountSheet = AccountSheet.None }) {
            AccountCard(Modifier.padding(vertical = Spacing.sm))
        }

        AccountSheet.SignIn -> SignInSheet(
            reason = LocalCopy.current.shared.account.signInReason,
            onSignedIn = {
                accountSheet = AccountSheet.None
                account.syncNow()
            },
            onDismiss = { accountSheet = AccountSheet.None },
        )
    }
    val onSwitch: (SettingsAction, Boolean) -> Unit = { action, on ->
        when (action) {
            SettingsAction.MoodByTimeOfDay -> settings.setMoodByTimeOfDay(on)

            SettingsAction.WeeklyCheckIn -> settings.setWeeklyCheckIn(on)

            SettingsAction.Transparency -> settings.setTransparency(on)

            SettingsAction.AppLock -> settings.setAppLock(
                on = on,
                authenticate = { deviceAuth.authenticate(unlockReason) },
                onTurnedOn = appLock::unlock,
            )

            SettingsAction.BackgroundImport -> background.setRunning(on)

            // Rows with no switch.
            SettingsAction.SlipSource, SettingsAction.KeepFailedSlips, SettingsAction.SlipOwner,
            SettingsAction.Activity, SettingsAction.Categories, SettingsAction.NoteTone,
            SettingsAction.Language, SettingsAction.ExportLedger, SettingsAction.ImportLedger,
            SettingsAction.Timezone, SettingsAction.HomeCurrency,
            SettingsAction.Assistant, SettingsAction.Feedback, SettingsAction.Licences,
            SettingsAction.Play, SettingsAction.GoalJar, SettingsAction.Advanced,
            SettingsAction.SharedAccount, SettingsAction.Help,
            -> Unit
        }
    }
    if (advanced) {
        SettingsAdvancedView(uiState = state, onAction = onAction, onBack = { nav.popBackStack() }, focusRow = focus, onSwitch = onSwitch)
        return
    }
    SettingsView(
        uiState = state,
        onAction = onAction,
        focusRow = focus,
        onSwitch = onSwitch,
        account = accountRow(accountState)?.let { row -> { SettingsRow(row = row, onClick = openAccount) } },
        footer = {
            Column {
                // Absent from a release build rather than hidden in one: the graph binds nothing
                // this needs unless the entry point said debug.
                if (koinInject<AppBuild>().debug) {
                    val debug = koinViewModel<DebugSlipViewModel>()
                    val summary by debug.summary.collectAsStateWithLifecycle()
                    DebugSlipPanel(
                        summary = summary,
                        onInjectReadable = debug::injectReadable,
                        onInjectSameMerchant = debug::injectSameMerchant,
                        onInjectUnreadable = debug::injectUnreadable,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            }
        },
    )
}

/** #187: the account as one Settings row; null when the build names no Firebase project. */
@Composable
private fun accountRow(state: AccountUiState): SettingsRowUiModel? {
    val words = LocalCopy.current.shared.account
    val value = when (state) {
        AccountUiState.Hidden -> return null
        is AccountUiState.SignedIn -> state.line
        AccountUiState.SignedOut -> words.notSignedIn
    }
    return SettingsRowUiModel(id = SettingsAction.SharedAccount, name = words.title, value = value, icon = "group")
}

/** Which account sheet the Account row opened, fixed at the tap. */
private enum class AccountSheet { None, Account, SignIn }
