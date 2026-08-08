plugins {
    id("smtp.kmp.web")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.smtpCore)
            implementation(libs.kotlinx.datetime)
        }
    }
}
