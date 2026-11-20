package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.shared.GetSyncAccessUseCase
import com.ptut.expense.domain.shared.JoinGroupUseCase
import com.ptut.expense.domain.shared.OpenInviteUseCase
import com.ptut.expense.sync.JoinChoice
import com.ptut.expense.sync.syncMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class JoinNameUiModel(val memberId: String, val name: String)

sealed interface JoinUiState {
    /** This build names no Firebase project, so there is nothing to join. */
    data object Unavailable : JoinUiState

    data object NeedsSignIn : JoinUiState

    data object Loading : JoinUiState

    data class Choose(val groupName: String, val names: List<JoinNameUiModel>, val joining: Boolean = false) : JoinUiState

    /** This account already holds [memberName] in the group: it opens it rather than joining twice. */
    data class AlreadyIn(val groupName: String, val memberName: String, val opening: Boolean = false) : JoinUiState

    data class Failed(val message: String) : JoinUiState

    data class Done(val groupId: String) : JoinUiState
}

/**
 * #190: someone opened an invite. They sign in, then pick the name the group already holds for
 * them, so the expenses already split to that name become theirs.
 */
class JoinViewModel(
    private val code: String,
    getSyncAccess: GetSyncAccessUseCase,
    private val openInvite: OpenInviteUseCase,
    private val joinGroup: JoinGroupUseCase,
) : ViewModel() {

    private val state = MutableStateFlow<JoinUiState>(JoinUiState.Loading)
    val uiState: StateFlow<JoinUiState> = state

    private val access = getSyncAccess()

    private var choice: JoinChoice? = null

    init {
        when {
            !access.available -> state.value = JoinUiState.Unavailable

            access.signedInNow -> open()

            else -> {
                state.value = JoinUiState.NeedsSignIn
                viewModelScope.launch {
                    access.signedIn.first { it }
                    open()
                }
            }
        }
    }

    fun retry() = open()

    /** [memberId] null is "I'm not on the list": the account's own name joins the group. */
    fun pick(memberId: String?) {
        // A second tap before the button greys out would claim a second name.
        val current = (state.value as? JoinUiState.Choose)?.takeUnless { it.joining } ?: return
        state.value = current.copy(joining = true)
        join(memberId)
    }

    /** Already in the group: claiming the same name again changes nothing and brings the group here. */
    fun openGroup() {
        val current = (state.value as? JoinUiState.AlreadyIn)?.takeUnless { it.opening } ?: return
        val mine = choice?.mine ?: return
        state.value = current.copy(opening = true)
        join(mine.first)
    }

    private fun join(memberId: String?) {
        if (!access.available) return
        val choice = choice ?: return
        viewModelScope.launch {
            state.value = joinGroup(choice, memberId).fold(
                onSuccess = { groupId -> JoinUiState.Done(groupId) },
                onFailure = { failure -> JoinUiState.Failed(failure.syncMessage()) },
            )
        }
    }

    private fun open() {
        if (!access.available) return
        state.value = JoinUiState.Loading
        viewModelScope.launch {
            state.value = openInvite(code).fold(
                onSuccess = { opened ->
                    choice = opened
                    opened.mine?.let { (_, name) -> JoinUiState.AlreadyIn(opened.groupName, name) }
                        ?: JoinUiState.Choose(opened.groupName, opened.names.map { (id, name) -> JoinNameUiModel(id, name) })
                },
                onFailure = { failure -> JoinUiState.Failed(failure.syncMessage()) },
            )
        }
    }
}
