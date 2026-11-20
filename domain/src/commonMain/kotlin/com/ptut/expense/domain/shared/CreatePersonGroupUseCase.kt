package com.ptut.expense.domain.shared

/** #141: a one-off bill with one person is a group of two behind the scenes, named after them. Returns the new group's id. */
class CreatePersonGroupUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(name: String, personId: String?, icon: String): String =
        repository.createGroup(name.trim(), GroupType.Other, icon, listOf(NewMember(name, personId)))
}
