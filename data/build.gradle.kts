// As in android/architecture-samples: the models, the repository interfaces and their Room
// implementations, in one module. The only module with KSP.
plugins {
    id("ptut.kmp.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

// The simulator reads the host's disk, so MigrationTestHelper can open the committed schemas in place.
val writeSchemaDirectory = tasks.register("writeSchemaDirectory") {
    val schemas = layout.projectDirectory.dir("schemas").asFile.absolutePath
    val output = layout.buildDirectory.dir("generated/schemaDirectory/kotlin")
    inputs.property("schemas", schemas)
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/ptut/expense/data/local/SchemaDirectory.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.ptut.expense.data.local\n\n" +
                "internal const val SCHEMA_DIRECTORY = \"${schemas.replace("\\", "/")}\"\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        // Migration tests run on the iOS simulator only: the bundled driver has no host JVM native library.
        iosTest {
            kotlin.srcDir(writeSchemaDirectory)
            dependencies {
                implementation(libs.room.testing)
                implementation(libs.kotlinx.serialization.json.okio)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
