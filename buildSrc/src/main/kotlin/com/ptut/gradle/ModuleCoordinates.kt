package com.ptut.gradle

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider

/**
 * The `group:name` of every module this configuration resolves to, transitive arrivals included.
 *
 * Reads the resolution result rather than the version catalogue, so a dependency nobody declared
 * still shows up. Stays a [Provider] of plain strings so the gate can hold it as a task input
 * without resolving anything at configuration time.
 */
fun Configuration.moduleCoordinates(): Provider<Set<String>> =
    incoming.resolutionResult.rootComponent.map { it.flatten() }

private fun ResolvedComponentResult.flatten(): Set<String> {
    val seen = mutableSetOf<ComponentIdentifier>()
    val coordinates = sortedSetOf<String>()

    fun walk(component: ResolvedComponentResult) {
        if (!seen.add(component.id)) return
        (component.id as? ModuleComponentIdentifier)?.let { coordinates += "${it.group}:${it.module}" }
        component.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            // A constraint pins a version without putting anything on the classpath. A BOM that
            // names okhttp is not the same as shipping okhttp.
            .filterNot { it.isConstraint }
            .forEach { walk(it.selected) }
    }

    walk(this)
    return coordinates
}
