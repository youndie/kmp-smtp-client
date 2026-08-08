plugins {
    id("smtp.kmp.web")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.smtpCore)
            implementation(libs.kotlincrypto.hmac.md)
            implementation(libs.kotlincrypto.hmac.sha1)
            implementation(libs.kotlincrypto.hmac.sha2)
            implementation(libs.kotlincrypto.hash.sha1)
            implementation(libs.kotlincrypto.hash.sha2)
            implementation(libs.kotlincrypto.random)
        }
    }
}
