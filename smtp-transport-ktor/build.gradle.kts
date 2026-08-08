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
        }
    }
}
