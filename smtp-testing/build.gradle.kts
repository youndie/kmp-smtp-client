plugins {
    // Not smtp.kmp.web: the fake server needs sockets, and ktor-network has no browser story.
    // The scripted transport alone would work on js, but there is no client transport there to
    // feed it (M-84a).
    id("smtp.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.smtpCore)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.network)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.smtpClient)
            implementation(projects.smtpTransportKtor)
        }
    }
}
