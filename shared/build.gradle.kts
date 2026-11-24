import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(25)
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)

            // UI：Miuix 取代 Material3
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.blur)
            implementation(libs.miuix.squircle)
            implementation(libs.miuix.nav)
            implementation(libs.miuix.material)
            implementation(libs.miuix.glass)
            implementation(libs.miuix.glass.icons)
            // desktop 专用模块（无 android 变体）；本项目只有 JVM 目标，放这里即可
            implementation(libs.miuix.window)

            // 持久化
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}

dependencies {
    // Room 的注解处理器只跑在 JVM 目标上
    add("kspJvm", libs.androidx.room.compiler)
}

// Room schema 随源码保留，保证后续迁移有历史版本可对照。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
// ---------------------------------------------------------------------------
// Rust MTP bridge
// ---------------------------------------------------------------------------
val mtpBridgeDir = rootProject.layout.projectDirectory.dir("native/mtp-bridge")
val cargoExecutable = sequenceOf(
    System.getenv("CARGO"),
    "${System.getProperty("user.home")}/.cargo/bin/cargo",
    "/opt/homebrew/bin/cargo",
    "/usr/local/bin/cargo",
).filterNotNull().firstOrNull { File(it).canExecute() } ?: "cargo"

val buildMtpBridge = tasks.register<Exec>("buildMtpBridge") {
    group = "build"
    description = "Builds the mtp-rs native bridge (cdylib)"
    workingDir(mtpBridgeDir)
    environment("PATH", "${System.getProperty("user.home")}/.cargo/bin:" + (System.getenv("PATH") ?: ""))
    commandLine(cargoExecutable, "build", "--release")
    inputs.files(
        fileTree(mtpBridgeDir) {
            include("Cargo.toml", "Cargo.lock", "src/**")
        }
    )
    outputs.file(mtpBridgeDir.file("target/release/libdockhand_mtp.dylib"))
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(buildMtpBridge)
    inputs.files(buildMtpBridge)
    from(buildMtpBridge.map { mtpBridgeDir.file("target/release/libdockhand_mtp.dylib") }) {
        into("native")
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}