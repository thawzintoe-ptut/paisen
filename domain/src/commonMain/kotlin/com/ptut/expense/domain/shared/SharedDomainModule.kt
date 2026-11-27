package com.ptut.expense.domain.shared

import com.ptut.expense.sync.SharedSyncApi
import org.koin.core.module.Module
import org.koin.dsl.module

/** The use cases behind Shared groups, people, sign-in and sync screens. */
val sharedUseCases: Module = module {
    factory { GetSharedHomeUseCase(get()) }
    factory { GetPeopleBalancesUseCase() }
    factory { GetGroupDetailUseCase(get()) }
    factory { GetActivityUseCase(get()) }
    factory { GetSettlementUseCase() }
    factory { GetPaymentBetweenUseCase() }
    factory { LoadExpenseDraftUseCase(get()) }
    factory { LoadRepeatDraftUseCase(get(), get()) }
    factory { GetLastGroupRateUseCase(get()) }
    factory { TrackGroupSeenUseCase(get()) }

    factory { CreateGroupUseCase(get()) }
    factory { EditGroupUseCase(get()) }
    factory { AddMembersUseCase(get()) }
    factory { RemoveMemberUseCase(get()) }
    factory { SetPersonArchivedUseCase(get()) }
    factory { SetSimplifyDebtsUseCase(get()) }

    factory { GetSyncAccessUseCase(getOrNull()) }
    factory { ObserveSyncStatusUseCase(getOrNull()) }
    factory { PollSyncUseCase(getOrNull(), runDueRepeats = getOrNull()) }
    factory { SyncGroupNowUseCase(getOrNull(), runDueRepeats = getOrNull()) }
    factory { SyncAllNowUseCase(getOrNull()) }
    factory { AcknowledgeRefusedUseCase(getOrNull()) }
    factory { ObserveRefusedChangesUseCase(getOrNull()) }
    factory { DismissRefusedChangeUseCase(getOrNull()) }
    factory { ShareGroupUseCase(getOrNull()) }
    factory { RenewInviteUseCase(getOrNull()) }
    factory { SwitchOffInviteUseCase(getOrNull()) }
    factory { OpenInviteUseCase(getOrNull()) }
    factory { JoinGroupUseCase(getOrNull()) }

    factory { ObserveAccountUseCase(getOrNull()) }
    factory { SignInUseCase(getOrNull()) }
    factory { SignOutUseCase(getOrNull(), getOrNull()) }
    factory { DeleteAccountUseCase(getOrNull(), getOrNull()) }

    factory { CreatePersonGroupUseCase(get()) }
    factory { SavePromptPayUseCase(get()) }
    factory { SaveSharedExpenseUseCase(get()) }
    factory { RemoveSharedExpenseUseCase(get()) }
    factory { RestoreSharedExpenseUseCase(get()) }
    factory { RecordPaymentUseCase(get()) }
    factory { RemovePaymentUseCase(get()) }
    factory { PutBackPaymentUseCase(get()) }
    factory { SaveRepeatUseCase(get(), runDueRepeats = getOrNull()) }
    factory { StopRepeatUseCase(get(), runDueRepeats = getOrNull()) }
    factory { RemoveGroupUseCase(get(), exits = getOrNull<SharedSyncApi>()?.let(::SharedSyncExits), runDueRepeats = getOrNull()) }
}
