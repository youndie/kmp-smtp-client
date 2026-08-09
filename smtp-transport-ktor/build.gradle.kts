plugins {
    id("smtp.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.smtpCore)
            implementation(libs.ktor.network)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // Only the E2E needs it, to ask the server what it stored. Production code here has no
            // HTTP client and no reason for one.
            implementation(libs.ktor.client.cio)
        }
    }
}
