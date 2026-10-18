rootProject.name = "PtutExpense"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":composeApp")
include(":androidApp")

include(":data")

include(":core:util")
include(":core:locale")
include(":core:platform")
include(":core:ui")
include(":core:sync")
include(":core:importer")
include(":core:agent")
include(":core:testing")

include(":feature:today")
include(":feature:ledger")
include(":feature:reports")
include(":feature:record")
include(":feature:add")
include(":feature:review")
include(":feature:shared")
include(":feature:settings")
include(":feature:categories")
include(":feature:onboarding")
