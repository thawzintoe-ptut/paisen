package com.ptut.expense.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

private val APP_SCOPE = named("app")

/**
 * The one scope every `single` that shares flows eagerly runs in. Nothing cancels it: its flows
 * have to outlive every screen reading them, and a supervisor keeps one failure from stopping the rest.
 */
val appScopeModule: Module = module {
    single<CoroutineScope>(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}

internal fun Scope.appScope(): CoroutineScope = get(APP_SCOPE)
