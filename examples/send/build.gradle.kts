plugins {
    id("smtp.example")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.smtpClient)
            implementation(projects.smtpMime)
            implementation(projects.smtpTransportKtor)
        }
    }
}
