plugins {
    id("smtp.kmp.web")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.smtpCore)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
