plugins {
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
