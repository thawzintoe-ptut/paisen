package com.ptut.expense.shoprule

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The gap rev 5 left: the shops screen went, and with it the only way to correct a shop filing
 * into the wrong category. Screen 11c still refuses to do it and points at a screen that no
 * longer offers it.
 */
class ShopRuleViewModelTest {

    private val sevenEleven = RememberedMerchant("r1", "7-Eleven", "shopping", slipCount = 38)

    @BeforeTest
    fun pinTheDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun releaseTheDispatcher() = Dispatchers.resetMain()

    @Test
    fun `the rule names its shop and where its slips land`() = runTest {
        val model = viewModel(listOf(sevenEleven))
        runCurrent()

        val state = assertIs<ShopRuleUiState.Loaded>(model.uiState.value)
        assertEquals("7-Eleven", state.shop)
        assertEquals("shopping", state.chosenCategoryId)
        assertEquals("38 slips filed here without asking.", state.filedSoFar)
    }

    /** C-2: a slip is money sent, so a shop can never be taught to file as income. */
    @Test
    fun `only spending categories are offered`() = runTest {
        val model = viewModel(listOf(sevenEleven))
        runCurrent()

        val offered = assertIs<ShopRuleUiState.Loaded>(model.uiState.value).categories
        assertTrue(offered.all { it.direction == Direction.Expense })
        assertTrue(offered.none { it.id == "income" })
    }

    @Test
    fun `changing the category writes the rule and nothing else`() = runTest {
        val rules = FakeRules(listOf(sevenEleven))
        val model = watching(ShopRuleViewModel("7-Eleven", rules))
        runCurrent()

        model.recategorise("groceries")
        runCurrent()

        assertEquals("groceries", rules.rules.value.single().categoryId)
        // FR-13: the rule decides where the next slip lands and rewrites nothing already filed.
        assertTrue(rules.ledgerWrites.isEmpty())
    }

    @Test
    fun `forgetting the shop removes the rule and closes the screen`() = runTest {
        val rules = FakeRules(listOf(sevenEleven))
        val model = watching(ShopRuleViewModel("7-Eleven", rules))
        runCurrent()

        model.forget()
        runCurrent()

        assertTrue(rules.rules.value.isEmpty())
        assertTrue(model.isForgotten.value)
        assertTrue(rules.ledgerWrites.isEmpty())
    }

    /** The name a slip printed varies in case, and the engine matches on a trimmed lowercase key. */
    @Test
    fun `a shop is found however the slip printed its name`() = runTest {
        val model = viewModel(listOf(sevenEleven), shop = "  7-ELEVEN ")
        runCurrent()

        assertIs<ShopRuleUiState.Loaded>(model.uiState.value)
    }

    @Test
    fun `a shop with no rule has nothing to correct`() = runTest {
        val model = viewModel(listOf(sevenEleven), shop = "Foodland")
        runCurrent()

        assertEquals(ShopRuleUiState.Missing, model.uiState.value)
        assertFalse(model.isForgotten.value)
    }

    @Test
    fun `one slip reads as one`() = runTest {
        val model = viewModel(listOf(sevenEleven.copy(slipCount = 1)))
        runCurrent()

        assertEquals(
            "One slip filed here without asking.",
            assertIs<ShopRuleUiState.Loaded>(model.uiState.value).filedSoFar,
        )
    }

    private fun TestScope.viewModel(held: List<RememberedMerchant>, shop: String = "7-Eleven") =
        watching(ShopRuleViewModel(shop, FakeRules(held)))

    private fun TestScope.watching(model: ShopRuleViewModel): ShopRuleViewModel {
        backgroundScope.launch { model.uiState.collect {} }
        return model
    }

    private class FakeRules(held: List<RememberedMerchant>) : RuleRepository {
        private val state = MutableStateFlow(held)
        override val rules: StateFlow<List<RememberedMerchant>> = state

        /** Nothing here may reach the ledger. A test asserts this stays empty. */
        val ledgerWrites = mutableListOf<String>()

        override suspend fun recategorise(id: String, categoryId: String) {
            state.value = state.value.map { if (it.id == id) it.copy(categoryId = categoryId) else it }
        }

        override suspend fun forget(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun rename(id: String, displayName: String?) {
            state.value = state.value.map { if (it.id == id) it.copy(displayName = displayName) else it }
        }
    }
}
