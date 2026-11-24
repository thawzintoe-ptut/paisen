package com.ptut.expense.di

import com.ptut.expense.agent.ModelState
import com.ptut.expense.categories.CategoriesViewModel
import com.ptut.expense.categories.EditCategoryViewModel
import com.ptut.expense.domain.play.PlayBook
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.ObserveAssistantModelUseCase
import com.ptut.expense.folders.PhotoFoldersViewModel
import com.ptut.expense.locale.Language
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.resources.Res
import com.ptut.expense.settings.AssistantModelViewModel
import com.ptut.expense.settings.BackgroundImportViewModel
import com.ptut.expense.settings.HomeCurrencyViewModel
import com.ptut.expense.settings.KeepFailedSlipsViewModel
import com.ptut.expense.settings.LanguageViewModel
import com.ptut.expense.settings.LedgerBackupViewModel
import com.ptut.expense.settings.LicencesViewModel
import com.ptut.expense.settings.SettingsViewModel
import com.ptut.expense.settings.SlipImportCadence
import com.ptut.expense.settings.TimezoneViewModel
import com.ptut.expense.settings.slipImportCadence
import com.ptut.expense.sync.flags.Flag
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Settings and every screen a Settings row opens, the photo folders and categories among them. */
val settingsModule: Module = module {
    /**
     * C-4: what Settings may claim about importing is the platform's to say, like the pickers. A
     * factory, because its lines are words and a single would keep the language it was made in.
     */
    factory { slipImportCadence() }

    viewModel {
        SettingsViewModel(
            observeLastImport = get(),
            observeChoices = get(),
            cadence = get<SlipImportCadence>(),
            saveMoodByTimeOfDay = get(),
            saveTransparency = get(),
            saveWeeklyCheckIn = get(),
            saveSlipOwner = get(),
            enableAppLock = get(),
            disableAppLock = get(),
            switchNoteToneUseCase = get(),
            observeFolders = get(),
            observeCategoryCounts = get(),
            observeAssistant = get(),
            showAssistant = flagOn(Flag.Assistant),
            canLock = getOrNull<DeviceAuth>()?.isAvailable() == true,
            showNoteTone = flagOn(Flag.NoteTone),
            showHomeCurrency = currenciesOn(),
            showPlay = flagOn(Flag.Play) && laterOn(),
            showJar = flagOn(Flag.Play),
            showV2 = v2On(),
            observeGoals = get<PlayBook>().goals,
        )
    }

    viewModel {
        BackgroundImportViewModel(
            observeLastImport = get(),
            observeChoices = get(),
            setBackgroundImport = get(),
            runImportNow = get(),
            toasts = get(),
            cadence = get<SlipImportCadence>(),
        )
    }
    viewModel { KeepFailedSlipsViewModel(observeKeptSlips = get(), observeChoices = get(), setRetention = get(), soonestExpiry = get()) }
    viewModel {
        LedgerBackupViewModel(
            observeHolding = get(),
            restoring = get(),
            files = get(),
            toasts = get(),
            homeToday = get(),
            buildExport = get(),
        )
    }
    viewModel { TimezoneViewModel() }
    viewModel { LanguageViewModel(saveLanguage = get(), offered = Language.offered(offersUnreleased = flagOn(Flag.ThaiLanguage))) }
    viewModel { AssistantModelViewModel(observeModel = get(), download = get(), remove = get()) }
    viewModel {
        val model = get<ObserveAssistantModelUseCase>()().value
        LicencesViewModel(
            readText = { file -> Res.readBytes(file).decodeToString() },
            // Gemma's terms apply once the model can be offered; with the assistant on, its row shows already.
            gemma = flagOn(Flag.Assistant) || model != ModelState.NotOffered,
        )
    }
    viewModel {
        PhotoFoldersViewModel(
            refreshCatalog = get(),
            observeFolders = get(),
            applyWatchedFolders = get(),
            folderAccess = get(),
            chooser = get(),
            toasts = get(),
        )
    }
    viewModel { CategoriesViewModel(observeMonthlySpend = get(), moveCategory = get(), toasts = get()) }
    viewModel { (categoryId: String, isNew: Boolean) ->
        EditCategoryViewModel(
            categoryId = categoryId,
            observeTransactions = get(),
            observeCategories = get(),
            observeSubCategories = get(),
            getDeleteOptions = get(),
            getCategoryStats = get(),
            saveCategory = get(),
            deleteCategory = get(),
            isNew = isNew,
            haptics = get(),
            weeklyTarget = choice(null) { it.weeklyTarget },
            // A graph with no settings draws no target field.
            offersTarget = getOrNull<AppSettings>() != null,
            toasts = get(),
            savedState = get(),
        )
    }
    viewModel {
        HomeCurrencyViewModel(
            isLedgerEmpty = get(),
            changeHomeCurrency = get(),
            convertHomeCurrency = get(),
            previewConversion = get(),
        )
    }
}
