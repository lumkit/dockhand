import org.jetbrains.compose.desktop.application.dsl.TargetFormat

import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // 窗口装饰（MiuixWindow / MiuixCaptionBar）：shared 里是 implementation，不会透传到这里
    implementation(libs.miuix.window)
}

// FFM 需要 JDK 22+；项目统一使用 JDK 25 编译/运行/打包。
// javaHome 也指向同一个 toolchain，避免 desktopApp:run 回退到 Gradle daemon 的 JDK 21。
val jdk25Home = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}.get().metadata.installationPath.asFile.absolutePath

compose.desktop {
    application {
        mainClass = "io.github.dockhand.MainKt"

        // 菜单栏 / Dock 里的应用名，避免显示成 MainKt
        javaHome = jdk25Home
        jvmArgs += listOf(
            "-Xdock:name=Dockhand",
            "-Dapple.awt.application.name=Dockhand",
            "--enable-native-access=ALL-UNNAMED",
        )

        nativeDistributions {
            // 只发 macOS
            targetFormats(TargetFormat.Dmg)
            packageName = "Dockhand"
            packageVersion = "1.0.0"
            description = "Dockhand dual-pane file browser"
            vendor = "lumkit"
            copyright = "Copyright © 2026 lumkit. All rights reserved."
            // DataStore 的 protobuf 需要 sun.misc.Unsafe
            modules("jdk.unsupported")

            macOS {
                bundleID = "io.github.lumkit.dockhand"
                packageName = "Dockhand"
                dockName = "Dockhand"
                setDockNameSameAsPackageName = true
                appCategory = "public.app-category.utilities"
                minimumSystemVersion = "12.0"
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDisplayName</key>
                        <string>Dockhand</string>
                    """.trimIndent()
                }
            }
        }

        // Compose Desktop 的 JVM 打包使用 ProGuard；这里开启 release 优化/混淆，
        // 并把优化后的依赖 jar 合并成单一输出。
        buildTypes.release {
            proguard {
                isEnabled.set(true)
                optimize.set(true)
                obfuscate.set(true)
                joinOutputJars.set(true)
                configurationFiles.from(layout.projectDirectory.file("proguard-rules.pro"))
            }
        }
    }
}