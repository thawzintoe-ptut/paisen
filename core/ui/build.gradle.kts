// The design system: tokens, theme, Sky components, toasts, icons and the bundled fonts.
plugins {
    id("ptut.kmp.compose")
}

/**
 * Fixed rather than derived. The default package is built from the group and module name, so a
 * rename would silently move `Res` and break every import that reads a font. Public because the
 * app module reads the licence texts through it.
 */
compose.resources {
    packageOfResClass = "com.ptut.expense.resources"
    publicResClass = true
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.util)
            implementation(projects.core.locale)
            implementation(projects.core.platform)
        }
        commonTest.dependencies {
            // PtutIconTest checks every seeded category names a drawn icon.
            implementation(projects.data)
        }
    }
}
