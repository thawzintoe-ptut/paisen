// As in android/architecture-samples: the models, the repository interfaces and their Room
// implementations, in one module. The only module with KSP.
plugins {
    id("ptut.kmp.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
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
