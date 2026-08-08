plugins {
    id("smtp.kmp.web")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            // Test-only: the published module keeps its "stdlib and nothing else" dependency list.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
