package com.ptut.expense.di

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.sync.flags.FeatureFlags
import com.ptut.expense.sync.flags.Flag
import com.ptut.expense.ui.inAppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.Koin
import org.koin.core.scope.Scope

/**
 * The categories as the reader has them, for a screen that turns an id into a name.
 *
 * Falls back to the seed twice over: while the flow has not emitted — the repository shares
 * eagerly, but a ViewModel built in the same frame as the graph can still arrive first — and where
 * a screen test's graph binds no category table at all. A screen of unnamed rows would be worse
 * than one naming them as installed.
 *
 * Used by the screens that offer a choice as well as the ones that only name. A category carries
 * its own direction now, so `forDirection` filters this list rather than a compiled-in pair — which
 * is what lets a category the reader made reach the sheet that files into it.
 */
internal fun Scope.storedCategories(): List<Category> =
    getOrNull<CategoryRepository>()?.categories?.value.orEmpty().ifEmpty { Category.seed().map(Category::inAppLanguage) }

internal fun Scope.isDebug(): Boolean = getOrNull<AppBuild>()?.debug == true

/** The Remote Config switches this launch read, or the defaults in a graph that binds none. */
internal fun Koin.featureFlags(): FeatureFlags = getOrNull() ?: FeatureFlags.Defaults

/** A Remote Config switch as this launch read it; debug builds have every one on. */
internal fun Scope.flagOn(flag: Flag): Boolean = getKoin().featureFlags().isOn(flag, debug = isDebug())

/** Other currencies (#33–#48): on in release since 19 Nov 2026. The one switch for all three places. */
internal fun Scope.currenciesOn(): Boolean = flagOn(Flag.Currencies)

/** The sub-categories as stored, read when a screen is built, like [storedCategories] (#111). */
internal fun Scope.storedSubCategories(): List<SubCategory> =
    getOrNull<CategoryRepository>()?.subCategories?.value.orEmpty()

/** [storedSubCategories] as they change, for the tabs, like [liveCategories] (#112). */
internal fun Scope.liveSubCategories(): Flow<List<SubCategory>> =
    getOrNull<CategoryRepository>()?.subCategories ?: flowOf(emptyList())

/**
 * [storedCategories] as it changes (#115), for the tabs: they keep their holder while the reader
 * is away, so a list read once would miss a rename or a new category until a restart.
 */
internal fun Scope.liveCategories(): Flow<List<Category>> =
    getOrNull<CategoryRepository>()?.categories?.map { it.ifEmpty { Category.seed().map(Category::inAppLanguage) } } ?: flowOf(Category.seed().map(Category::inAppLanguage))

/** The phone's country as a currency, or baht in a graph that binds none. */
internal fun Scope.phoneCurrency(): Currency = getOrNull<PhoneCurrency>()?.current() ?: Currencies.Thb

/** One of the reader's stored choices as it changes, or [default] in a graph that binds no settings. */
internal fun <T> Scope.choice(default: T, pick: (AppChoices) -> T): Flow<T> =
    getOrNull<AppSettings>()?.choices?.map(pick) ?: flowOf(default)
