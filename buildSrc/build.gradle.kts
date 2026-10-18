// Home for the build-time gates and the convention plugins. They live here rather than in a build
// script because many modules need the same task types and setup, and a build script cannot share a class.
plugins {
    `kotlin-dsl`
}

// The convention plugins apply these by id, so they must be on this classpath. Module build files
// then name the plugins without a version: a second, versioned request fails resolution.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.jetbrains.compose:compose-gradle-plugin:${libs.versions.composeMultiplatform.get()}")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    implementation("androidx.room:room-gradle-plugin:${libs.versions.room.get()}")

    // Gradle builds buildSrc before the main build. A gate that scans sources is itself logic, and
    // it is the one place a bug goes unnoticed, so the gates keep their own tests.
    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
