import com.ptut.gradle.VerifyForbiddenImports

// A feature: screens and their ViewModels. Its ViewModels call :domain's use cases, read :data's models,
// and never depend on another feature; the app module connects features through navigation callbacks.
plugins {
    id("ptut.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
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
    description = "Fails when a feature imports Room, a repository or a service instead of calling :domain's use cases."
    sources.from(fileTree(sourceDir) { include("*Main/**/*.kt") })
    sourceRoot.set(sourceDir)
    forbiddenPrefixes.set(
        listOf(
            "com.ptut.expense.data.local",
            "com.ptut.expense.data.repository",
            // A ViewModel calls use cases; these are what the use cases call.
            "com.ptut.expense.domain.ledger.LedgerRepository",
            "com.ptut.expense.domain.shared.SharedRepository",
            "com.ptut.expense.domain.categories.CategoryRepository",
            "com.ptut.expense.domain.rules.RuleRepository",
            "com.ptut.expense.domain.folders.WatchedFolderRepository",
            "com.ptut.expense.domain.folders.FolderCatalog",
            "com.ptut.expense.domain.settings.AppSettings",
            "com.ptut.expense.domain.home.HomeBook",
            "com.ptut.expense.domain.backup.LedgerArchive",
            "com.ptut.expense.domain.slip.ImportStore",
            "com.ptut.expense.sync.SharedSync",
            "com.ptut.expense.sync.auth.AuthRepository",
            "com.ptut.expense.importer.SlipImporter",
            "com.ptut.expense.importer.SlipDiscards",
            "com.ptut.expense.locale.LanguageStore",
            "com.ptut.expense.agent.AgentLoop",
            "com.ptut.expense.agent.AgentReader",
            "com.ptut.expense.agent.LanguageModelPort",
            "com.ptut.expense.platform.work.BackgroundImport",
            "com.ptut.expense.platform.notify.RepeatReminders",
            "com.ptut.expense.domain.folders.FirstRunFolders",
            "com.ptut.expense.domain.folders.FolderReadTally",
            "com.ptut.expense.importer.SlipOwnerBook",
            "com.ptut.expense.sync.auth.TokenStore",
        ),
    )
    receipt.set(layout.buildDirectory.file("reports/verifyFeatureImports/scanned.txt"))
}

tasks.named("check") { dependsOn(verifyFeatureImports) }
