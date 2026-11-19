package com.ptut.expense.di

import com.ptut.expense.agent.LanguageModelPort
import com.ptut.expense.agent.ModelState
import com.ptut.expense.categories.CategoriesViewModel
import com.ptut.expense.categories.EditCategoryViewModel
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.folders.PhotoFoldersViewModel
import com.ptut.expense.locale.Language
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.platform.work.BackgroundImport
import com.ptut.expense.resources.Res
import com.ptut.expense.settings.AddCurrencyViewModel
import com.ptut.expense.settings.AssistantModelViewModel
import com.ptut.expense.settings.BackgroundImportViewModel
import com.ptut.expense.settings.CategoryCounts
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
            ledger = get(),
            settings = get(),
            cadence = get<SlipImportCadence>(),
            folders = getOrNull<WatchedFolderRepository>()?.folders ?: flowOf(emptyList()),
            categoryCounts = getOrNull<CategoryRepository>()?.let { repository ->
                repository.categories.combine(repository.subCategories) { all, subs -> CategoryCounts(all.size, subs.size) }
            } ?: flowOf(CategoryCounts()),
            assistant = getOrNull<LanguageModelPort>()?.state ?: flowOf(ModelState.NotOffered),
            showAssistant = flagOn(Flag.Assistant),
            canLock = getOrNull<DeviceAuth>()?.isAvailable() == true,
            showNoteTone = flagOn(Flag.NoteTone),
            showAddCurrency = currenciesOn(),
            phoneCurrency = { phoneCurrency().code },
        )
    }

    // getOrNull on the schedule: C-4 leaves iOS with no periodic job, so nothing binds one there
    // and the screen draws no switch. On Android the binding is always present.
    viewModel {
        BackgroundImportViewModel(
            ledger = get(),
            settings = get(),
            importer = get(),
            toasts = get(),
            cadence = get<SlipImportCadence>(),
            background = getOrNull<BackgroundImport>(),
        )
    }
    viewModel { KeepFailedSlipsViewModel(ledger = get(), settings = get()) }
    viewModel { LedgerBackupViewModel(ledger = get(), archive = get(), files = get(), toasts = get()) }
    viewModel { TimezoneViewModel() }
    // getOrNull: a screen test's graph binds no store; the choice then lasts until the process ends.
    viewModel { LanguageViewModel(store = getOrNull(), offered = Language.offered(offersUnreleased = flagOn(Flag.ThaiLanguage))) }
    viewModel { AssistantModelViewModel(model = get()) }
    viewModel {
        val model = getOrNull<LanguageModelPort>()?.state?.value
        LicencesViewModel(
            readText = { file -> Res.readBytes(file).decodeToString() },
            // Gemma's terms apply once the model can be offered; with the assistant on, its row shows already.
            gemma = flagOn(Flag.Assistant) || (model != null && model != ModelState.NotOffered),
        )
    }
    viewModel {
        // A provider, not the value: the factory below reads the permission each time it resolves (#217).
        PhotoFoldersViewModel(catalog = get(), folders = get(), access = { get() }, chooser = get(), toasts = get())
    }
    viewModel { CategoriesViewModel(ledger = get(), categories = get(), toasts = get()) }
    viewModel { (categoryId: String, isNew: Boolean) ->
        EditCategoryViewModel(categoryId = categoryId, ledger = get(), categories = get(), isNew = isNew, haptics = get(), settings = getOrNull(), toasts = get())
    }
    viewModel { HomeCurrencyViewModel(book = get()) }
    viewModel { AddCurrencyViewModel(settings = get(), phoneCurrency = { phoneCurrency() }) }
}
