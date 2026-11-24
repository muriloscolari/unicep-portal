import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val webOnly = providers.gradleProperty("webOnly").orNull == "true"
val androidOnly = providers.gradleProperty("androidOnly").orNull == "true"
require(!(webOnly && androidOnly)) { "Cannot set both -PwebOnly=true and -PandroidOnly=true" }

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

if (!webOnly) {
    apply(plugin = libs.plugins.androidKotlinMultiplatformLibrary.get().pluginId)
}

kotlin {
    jvmToolchain(21)

    if (!webOnly) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension>("android") {
            namespace = "me.muriloscolari.unicep.shared"
            compileSdk = 37
            minSdk = 26
            androidResources.enable = true
            withHostTest {}
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    if (!androidOnly) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser {
                commonWebpackConfig {
                    outputFileName = "unicep-portal.js"
                }
                testTask {
                    useKarma {
                        useFirefoxHeadless()
                    }
                }
            }
            binaries.executable()
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.haze.glass)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        if (!webOnly) {
            androidMain.dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
            }
        }

        if (!androidOnly) {
            wasmJsMain.dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    packageOfResClass = "me.muriloscolari.unicep.resources"
}

if (!androidOnly) {
    val distDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    tasks.matching { it.name == "wasmJsBrowserDistribution" }.configureEach {
        doLast {
            val outDir = distDir.get().asFile
            val indexFile = outDir.resolve("index.html")
            if (!indexFile.isFile) return@doLast

            fun shortSha256(fileName: String): String {
                val file = outDir.resolve(fileName)
                if (!file.isFile) return "v1"
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                return digest.take(4).joinToString("") { b ->
                    ((b.toInt() and 0xff) + 0x100).toString(16).substring(1)
                }
            }

            val appHash = shortSha256("unicep-portal.js")
            val loaderHash = shortSha256("loader.js")
            val updated = indexFile.readText(Charsets.UTF_8)
                .replace("unicep-portal.js?v=dev", "unicep-portal.js?v=$appHash")
                .replace("loader.js?v=dev", "loader.js?v=$loaderHash")
            indexFile.writeText(updated, Charsets.UTF_8)
        }
    }
}
