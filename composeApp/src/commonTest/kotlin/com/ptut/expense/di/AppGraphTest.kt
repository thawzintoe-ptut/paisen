package com.ptut.expense.di

import com.ptut.expense.ViewModelTest
import com.ptut.expense.agent.agentModule
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.PtutDatabaseConstructor
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.domainModule
import com.ptut.expense.domain.folders.FakeFolders
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.domain.folders.FolderReadTally
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.PlayBook
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.domain.settings.FakeAppSettingsStore
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.TitleChange
import com.ptut.expense.domain.shared.TransactionSeed
import com.ptut.expense.onboarding.FakeOnboardingFlag
import com.ptut.expense.onboarding.FakePhotoAccessRequest
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.ui.ScreenReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.error.DefinitionParameterException
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.error.NoParameterFoundException
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app died on its first frame with `No definition found for type FolderReadTally`, and every
 * gate had been green: 371 host tests, both compiles, four source rules. Nothing in the suite ever
 * resolved the graph the app starts, so nothing could have caught it.
 *
 * A `get()` inside a definition is invisible to the compiler. The type it asks for is a reified
 * argument settled when the lambda runs, so an implementation registered under one of the two
 * interfaces it carries still compiles, still passes every ViewModel test, and still ships an app
 * that never draws. Building the real definitions is the only thing that asks what a launch asks.
 *
 * So this takes [ledgerModule], [agentModule], [domainModule], [viewModelModule] and [slipSeamModule] exactly
 * as they ship and builds every definition in them. Nothing here restates the wiring: a binding added
 * to the app is swept the next time this runs, with nobody having to remember to list it.
 *
 * Two things stay out of reach and are worth naming. The platform half of [dataModule] cannot be
 * built without a real `Context`, but every definition in it takes only that handle and resolves
 * nothing, so it has no way to fail like this. `platformSlipModule` does resolve, and does need a
 * `Context` at the moment it is built, so its `get()` calls are still only covered on a device.
 */
class AppGraphTest : ViewModelTest() {

    @Test
    fun `a release graph binds every type it resolves`() {
        assertEquals(emptyList(), unbound(AppBuild(debug = false)))
    }

    /**
     * Asked twice, as the seams are: a debug build binds the injectable source and the debug
     * menu's own state holder, and those are definitions a release build never reaches.
     */
    @Test
    fun `a debug graph binds every type it resolves`() {
        assertEquals(emptyList(), unbound(AppBuild(debug = true)))
    }

    /**
     * Swept twice. The first sweep builds the shipped repositories; the second stands in the ones
     * that read a table when built, because the first failure stops every screen above them.
     */
    private fun unbound(build: AppBuild): List<String> =
        (sweep(build, standIns = module { }) + sweep(build, standIns = databaseStandIns)).distinct().sorted()

    /** Every definition in the graph, and what any of them asked for in vain. */
    @OptIn(KoinInternalApi::class)
    private fun sweep(build: AppBuild, standIns: Module): List<String> {
        val application = koinApplication {
            modules(
                ledgerModule,
                agentModule,
                domainModule,
                viewModelModule,
                slipSeamModule(build),
                platformStandIns,
                standIns,
            )
        }
        return try {
            application.koin.instanceRegistry.instances.values
                .map { it.beanDefinition }
                .distinctBy { it.primaryType to it.qualifier }
                .mapNotNull { application.koin.missingBindingFor(it) }
        } finally {
            application.close()
        }
    }

    /**
     * Builds one definition and reports only what Koin could not find.
     *
     * A repository that reached its own constructor and then failed on the database is a pass. The
     * host has no SQLite driver, so a [PtutDatabase] nothing opened throws the moment a DAO asks it
     * for a flow — and that is after every `get()` in the definition has already been answered,
     * which is the part being asked about. Widening this to "nothing may throw" would trade a guard
     * that holds for one that breaks on the next repository to read its table at construction.
     */
    private fun Koin.missingBindingFor(definition: BeanDefinition<*>): String? {
        // A screen holding one record takes its id; a few take more. Each is built with the first
        // shape it accepts, so a screen with five parameters is checked as fully as one with one.
        for (parameters in PARAMETER_SHAPES) {
            try {
                get<Any>(definition.primaryType, definition.qualifier) { parametersOf(*parameters) }
                return null
            } catch (failure: Exception) {
                if (failure.isParameterMismatch()) continue
                return failure.missingDefinition()?.let { "${definition.primaryType.simpleName} wanted $it" }
            }
        }
        return "${definition.primaryType.simpleName} takes parameters this test does not pass"
    }

    /** Too few parameters, or one of the wrong type: try the next shape rather than counting it as a pass. */
    private fun Throwable.isParameterMismatch(): Boolean = when (this) {
        is NoParameterFoundException, is DefinitionParameterException, is ClassCastException -> true
        else -> cause?.takeIf { it !== this }?.isParameterMismatch() ?: false
    }

    /** Koin reports a failed resolve wrapped in the definition that was being built. */
    private fun Throwable.missingDefinition(): String? = when (this) {
        is NoDefinitionFoundException -> message
        else -> cause?.takeIf { it !== this }?.missingDefinition()
    }
}

private const val ANY_ID = "any-id"

/** The parameter lists the app passes: one id, an id and a flag (Edit category), and two to five ids (shared screens). */
private val PARAMETER_SHAPES: List<Array<Any>> = listOf(
    arrayOf(ANY_ID),
    arrayOf(ANY_ID, false),
    arrayOf(ANY_ID, ANY_ID),
    arrayOf(ANY_ID, ANY_ID, ANY_ID),
    arrayOf(ANY_ID, ANY_ID, ANY_ID, ANY_ID, ANY_ID),
)

/**
 * What only a platform can supply, standing in. Each of these is bound in the half of [dataModule]
 * that takes the database handle, and not one of them resolves anything itself — which is what
 * makes swapping them here honest. Everything above them is the shipped wiring.
 */
private val platformStandIns = module {
    // Room's own constructor, which makes the object without opening a file. Nothing below runs
    // a query through it, and no definition here would be found any better if one could.
    single<PtutDatabase> { PtutDatabaseConstructor.initialize() }

    single<LedgerFileStore> { NoFiles }
    single<AppSettingsStore> { FakeAppSettingsStore() }
    single<OnboardingFlag> { FakeOnboardingFlag() }
    single<PhotoAccessRequest> { FakePhotoAccessRequest() }
    single<FolderCatalog> { FolderCatalog { emptyList() } }
    single { FolderAccess(action = null, explanation = "") }
    single { FolderChooser { } }
    single { ScreenReader { false } }
}

/**
 * The repositories that read a table in their constructor, which the host cannot open. Built in
 * the first sweep, so their own `get()`s are still checked there.
 */
private val databaseStandIns = module {
    single<LedgerRepository> { FakeLedger() }
    single<WatchedFolderRepository> { FakeFolders() }
    single<FolderReadTally> { FolderReadTally { _, _ -> } }
    single<CategoryRepository> { NoCategories }
    single<RuleRepository> { NoRules }
    single<SharedRepository> { NoShared }
    single<PlayBook> { FakePlayBook() }
}

private object NoCategories : CategoryRepository {
    override val categories: StateFlow<List<Category>> = MutableStateFlow(emptyList())
    override val subCategories: StateFlow<List<SubCategory>> = MutableStateFlow(emptyList())

    override suspend fun save(category: Category, subCategories: List<SubCategory>, removedSubCategoryIds: List<String>) = Unit

    override suspend fun saveSubCategory(subCategory: SubCategory) = Unit

    override suspend fun reorder(ordered: List<Category>) = Unit

    override suspend fun removeSubCategory(id: String) = Unit

    override suspend fun transactionCount(categoryId: String): Int = 0

    override suspend fun shopRuleCount(categoryId: String): Int = 0

    override suspend fun delete(categoryId: String, moveTo: String) = Unit
}

private object NoRules : RuleRepository {
    override val rules: StateFlow<List<RememberedMerchant>> = MutableStateFlow(emptyList())

    override suspend fun recategorise(id: String, categoryId: String) = Unit

    override suspend fun forget(id: String) = Unit

    override suspend fun rename(id: String, displayName: String?) = Unit
}

private object NoShared : SharedRepository {
    override val snapshot: StateFlow<SharedSnapshot> = MutableStateFlow(SharedSnapshot.Empty)

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String) = ""

    override suspend fun addMembers(groupId: String, members: List<NewMember>) = Unit

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = Unit

    override suspend fun setPromptPay(groupId: String, promptPayId: String?) = Unit

    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) = Unit

    override suspend fun deleteGroup(groupId: String) = Unit

    override suspend fun setPersonArchived(personId: String, archived: Boolean) = Unit

    override suspend fun removeMember(groupId: String, memberId: String) = Unit

    override suspend fun saveExpense(draft: ExpenseDraft) = ""

    override suspend fun removeExpense(expenseId: String) = Unit

    override suspend fun recordPayment(draft: PaymentDraft) = ""

    override suspend fun transactionSeed(transactionId: String): TransactionSeed? = null

    override suspend fun followTransaction(transactionId: String, before: TransactionEdit, titleChange: TitleChange?) = Unit

    override suspend fun removePayment(paymentId: String) = Unit

    override suspend fun putBackPayment(paymentId: String) = Unit

    override suspend fun restoreExpense(expenseId: String) = Unit

    override suspend fun relink(expenseId: String, transactionId: String) = Unit

    override suspend fun saveRepeat(rule: RepeatRule) = ""

    override suspend fun stopRepeat(ruleId: String) = Unit

    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = Unit

    override suspend fun addDueRepeats(today: LocalDate) = 0

    override suspend fun lastRate(currency: String, into: String): Rate? = null

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = Unit
}

private object NoFiles : LedgerFileStore {
    override suspend fun write(suggestedName: String, contents: String): String? = null

    override suspend fun read(): String? = null
}
