package com.ptut.expense.di

import com.ptut.expense.ViewModelTest
import com.ptut.expense.agent.agentModule
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.data.local.PtutDatabaseConstructor
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.domain.settings.FakeImportSettingsStore
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.onboarding.FakeOnboardingFlag
import com.ptut.expense.onboarding.FakePhotoAccessRequest
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.photos.PhotoAccessRequest
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.error.NoDefinitionFoundException
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
 * So this takes [ledgerModule], [agentModule], [viewModelModule] and [slipSeamModule] exactly
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

    /** Every definition in the graph, and what any of them asked for in vain. */
    @OptIn(KoinInternalApi::class)
    private fun unbound(build: AppBuild): List<String> {
        val application = koinApplication {
            modules(
                ledgerModule,
                agentModule,
                viewModelModule,
                slipSeamModule(build),
                platformStandIns,
            )
        }
        return try {
            application.koin.instanceRegistry.instances.values
                .map { it.beanDefinition }
                .distinctBy { it.primaryType to it.qualifier }
                .mapNotNull { application.koin.missingBindingFor(it) }
                .sorted()
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
    private fun Koin.missingBindingFor(definition: BeanDefinition<*>): String? =
        try {
            // The screens holding one record take its id as a parameter, and every other
            // definition ignores what it was not asked for.
            get<Any>(definition.primaryType, definition.qualifier) { parametersOf(ANY_ID) }
            null
        } catch (failure: Exception) {
            failure.missingDefinition()?.let { "${definition.primaryType.simpleName} wanted $it" }
        }

    /** Koin reports a failed resolve wrapped in the definition that was being built. */
    private fun Throwable.missingDefinition(): String? = when (this) {
        is NoDefinitionFoundException -> message
        else -> cause?.takeIf { it !== this }?.missingDefinition()
    }
}

private const val ANY_ID = "any-id"

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
    single<ImportSettingsStore> { FakeImportSettingsStore() }
    single<OnboardingFlag> { FakeOnboardingFlag() }
    single<PhotoAccessRequest> { FakePhotoAccessRequest() }
    single<FolderCatalog> { FolderCatalog { emptyList() } }
    single { FolderAccess(action = null, explanation = "") }
    single { FolderChooser { } }
}

private object NoFiles : LedgerFileStore {
    override suspend fun write(suggestedName: String, contents: String): String? = null

    override suspend fun read(): String? = null
}
