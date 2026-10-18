package com.ptut.expense.agent

import com.ptut.expense.agent.resources.Res
import com.ptut.expense.di.languageModel
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.inAppLanguage
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * #205–#208. Every piece is a `single`: the normaliser reads its model once, and Siri, the icon and
 * the Add sheet share one loop. The groups are read through `getOrNull` because the iOS UI tests
 * build the app without the shared-costs graph.
 */
val agentModule: Module = module {
    single { NormaliserSource { Res.readBytes(ZAWGYI_MODEL_FILE) } }
    single<LanguageModelPort> { languageModel() }
    single { AgentReader(ledger = get()) }
    single {
        val scope = this
        AgentEntry(
            normaliser = get(),
            reader = get(),
            categories = { scope.agentCategories() },
            subCategories = { scope.agentSubCategories() },
            groups = { scope.agentGroups() },
        )
    }
    single {
        val scope = this
        AgentLoop(
            normaliser = get(),
            model = get(),
            context = {
                AgentContext(
                    categories = scope.agentCategories(),
                    subCategories = scope.agentSubCategories(),
                    groups = scope.agentGroups(),
                    rowIds = (scope.get<LedgerRepository>().allTransactions.value as? LedgerSnapshot.Rows)
                        ?.transactions?.mapTo(mutableSetOf()) { it.id }.orEmpty(),
                    now = Clock.System.now(),
                )
            },
        )
    }
}

/** The categories as stored, or the seed while the table has not answered, as the screens read them. */
private fun Scope.agentCategories(): List<Category> =
    getOrNull<CategoryRepository>()?.categories?.value.orEmpty().ifEmpty { Category.seed().map(Category::inAppLanguage) }

/** The sub-categories as stored. None while the table has not answered: the seed has none. */
private fun Scope.agentSubCategories(): List<SubCategory> = getOrNull<CategoryRepository>()?.subCategories?.value.orEmpty()

private fun Scope.agentGroups(): List<SharedGroup> = getOrNull<SharedRepository>()?.snapshot?.value?.groups.orEmpty()
