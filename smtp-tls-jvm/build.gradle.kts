plugins {
    id("smtp.jvm")
}

dependencies {
    api(projects.smtpCore)
    testImplementation(projects.smtpTransportKtor)
    testImplementation(projects.smtpClient)
    testImplementation(libs.kotlinx.coroutines.test)
}
