package com.ptut.expense.domain.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** #141: a group with this phone's member and [members] in it. #48: [currency] stays the group's for good. The new group's id. */
class CreateGroupUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String =
        repository.createGroup(name, type, icon, members, currency)
}

/** A new name and type; any member may, and it syncs. A blank name leaves the group as it was. */
class EditGroupUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, name: String, type: GroupType, icon: String) {
        if (name.isBlank()) return
        repository.editGroup(groupId, name, type, icon)
    }
}

/** #218: names added after Create; in a shared group each joins from the link. Nothing happens when every name is blank. */
class AddMembersUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, members: List<NewMember>) {
        if (members.none { it.name.isNotBlank() }) return
        repository.addMembers(groupId, members)
    }
}

/** #218: one name leaves one group, archived, and past splits keep it. The repository holds the creator-only rule. */
class RemoveMemberUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, memberId: String) = repository.removeMember(groupId, memberId)
}

/** #141: an archived person keeps every past split but joins no new one (#56, #133). */
class SetPersonArchivedUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(personId: String, archived: Boolean) = repository.setPersonArchived(personId, archived)
}

/** #184: settle in the fewest payments, or in the original pairings. */
class SetSimplifyDebtsUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, on: Boolean) = repository.setSimplifyDebts(groupId, on)
}

/** #181: the last rate saved on this phone from [currency] into [into], a group's own money (#48), to pre-fill the next one. */
class GetLastGroupRateUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(currency: String, into: String): Rate? = repository.lastRate(currency, into)
}

/**
 * Research finding 5: emits the seen mark as it stood when the group opened, then keeps moving it to the newest row
 * while collected, so a row that arrives while the group is open is not new next time.
 */
class TrackGroupSeenUseCase(
    private val repository: SharedRepository,
    private val clock: Clock = Clock.System,
) {
    operator fun invoke(groupId: String): Flow<Long?> = flow {
        emit(repository.snapshot.first { it.group(groupId) != null }.group(groupId)?.lastSeenAtEpochMillis)
        repository.snapshot
            // Never past this phone's own clock: a member's phone stamps its rows, and one dated
            // ahead would hold the mark there and keep every later row from looking new.
            .map { it.newestRowAt(groupId)?.coerceAtMost(clock.now().toEpochMilliseconds()) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { repository.markSeen(groupId, it) }
    }
}
