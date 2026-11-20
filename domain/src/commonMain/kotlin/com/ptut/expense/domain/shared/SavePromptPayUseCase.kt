package com.ptut.expense.domain.shared

/** #199: my PromptPay ID in a group, as [PromptPay.normalize] gives it; null takes it off. */
class SavePromptPayUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, promptPayId: String?) = repository.setPromptPay(groupId, promptPayId)
}
