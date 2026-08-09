plugins {
    // Not smtp.kmp.web: a session needs a transport, and there is none on js or wasm (M-84a).
    // The modules that are useful on their own there — core, sasl, mime — keep those targets.
    id("smtp.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.smtpCore)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.smtpTesting)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
