import com.ptut.gradle.VerifyForbiddenImports

// A feature: screens and their ViewModels. It reads data through :data's repositories and never
// depends on another feature; the app module connects features through navigation callbacks.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":data"))
            implementation(project(":core:ui"))
            implementation(project(":core:util"))
            implementation(project(":core:locale"))
            implementation(project(":core:platform"))
        }
    }
}

configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        if (path.startsWith(":feature:") && path != project.path) {
            throw GradleException("${project.path} depends on $path. Features never depend on each other.")
        }
    }
}

val sourceDir = layout.projectDirectory.dir("src")

val verifyFeatureImports = tasks.register<VerifyForbiddenImports>("verifyFeatureImports") {
    group = "verification"
    description = "Fails when a feature imports Room or a repository implementation from :data."
    sources.from(fileTree(sourceDir) { include("*Main/**/*.kt") })
    sourceRoot.set(sourceDir)
    forbiddenPrefixes.set(listOf("com.ptut.expense.data.local", "com.ptut.expense.data.repository"))
    receipt.set(layout.buildDirectory.file("reports/verifyFeatureImports/scanned.txt"))
}

tasks.named("check") { dependsOn(verifyFeatureImports) }
