import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("smtp.kmp.native-host")
}

/**
 * Where OpenSSL 3 lives on this machine.
 *
 * Not written into the `.def` file: Homebrew keeps headers under a versioned prefix and Debian
 * keeps libraries in a multiarch directory, so a hardcoded path breaks on the next upgrade or on
 * the next distribution.
 */
val opensslPrefix: File =
    listOf(
        "/opt/homebrew/opt/openssl@3",
        "/usr/local/opt/openssl@3",
        "/usr",
        "/usr/local",
    ).map(::File)
        .firstOrNull { File(it, "include/openssl/ssl.h").exists() }
        ?: error(
            "OpenSSL 3 headers not found. Install `libssl-dev` (Debian/Ubuntu) or " +
                "`brew install openssl@3` (macOS), then re-run the build.",
        )

/**
 * Every directory the headers actually need.
 *
 * `ssl.h` sits in `include/openssl`, but on Debian multiarch `opensslconf.h` — which `macros.h`
 * includes — lives in `include/<triplet>/openssl` instead. Point cinterop only at the first one
 * and it fails with "'openssl/opensslconf.h' file not found".
 */
val opensslIncludeDirs: List<File> =
    buildList {
        val include = File(opensslPrefix, "include")
        add(include)
        include
            .listFiles()
            ?.filter { it.isDirectory && File(it, "openssl/opensslconf.h").exists() }
            ?.let(::addAll)
    }

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("openssl") {
            definitionFile.set(file("src/nativeInterop/cinterop/openssl.def"))
            includeDirs(*opensslIncludeDirs.toTypedArray())
        }

        binaries.all {
            // Multiarch: Debian puts the shared objects one level deeper than the prefix suggests.
            linkerOpts(
                "-L${File(opensslPrefix, "lib")}",
                "-L${File(opensslPrefix, "lib/x86_64-linux-gnu")}",
                "-lssl",
                "-lcrypto",
            )

            if (konanTarget.family == Family.LINUX) {
                // The Kotlin/Native sysroot deliberately ships an old glibc, while the
                // distribution's libssl references newer symbols (stat@GLIBC_2.33 and friends).
                // Without this the link fails; with it the dynamic loader resolves them at start-up,
                // where the real glibc is the distribution's own.
                linkerOpts("-Wl,--allow-shlib-undefined")
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            api(projects.smtpCore)
        }
        nativeTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // Test-only: the TLS tests need a real socket underneath, and that is the transport
            // module's job. Production code here never depends on it.
            implementation(projects.smtpTransportKtor)
            implementation(projects.smtpClient)
        }
    }
}
