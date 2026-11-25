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

val isAppStoreBuild = providers.gradleProperty("dockhand.appStore")
    .orNull
    .toBoolean()
val macSignEnabled = providers.gradleProperty("dockhand.macSign")
    .orNull
    .toBoolean()
val macSignIdentity = providers.gradleProperty("dockhand.macSignIdentity").orNull
val macSignKeychain = providers.gradleProperty("dockhand.macKeychain").orNull
val macNotaryAppleId = providers.gradleProperty("dockhand.notaryAppleId").orNull
val macNotaryPassword = providers.gradleProperty("dockhand.notaryPassword").orNull
val macNotaryTeamId = providers.gradleProperty("dockhand.notaryTeamId").orNull
val macProvisioningProfile = providers.gradleProperty("dockhand.provisioningProfile").orNull

compose.desktop {
    application {
        mainClass = "io.github.lumkit.dockhand.MainKt"

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
                // CFBundleVersion，App Store 每次上传必须递增
                packageBuildVersion = "1"
                // App Store 构建必须启用沙盒；当前默认使用 Developer ID 的
                // Hardened Runtime entitlements。沙盒化的文件访问仍需改造。
                appStore = isAppStoreBuild
                entitlementsFile.set(
                    project.file(
                        if (isAppStoreBuild) "entitlements-appstore.plist"
                        else "entitlements.plist"
                    )
                )
                macProvisioningProfile?.let { provisioningProfile.set(project.file(it)) }
                iconFile.set(project.file("icons/Dockhand.icns"))
                dockName = "Dockhand"
                setDockNameSameAsPackageName = true
                appCategory = "public.app-category.utilities"
                minimumSystemVersion = "12.0"
                signing {
                    sign.set(macSignEnabled)
                    macSignIdentity?.let { identity.set(it) }
                    macSignKeychain?.let { keychain.set(it) }
                }
                notarization {
                    macNotaryAppleId?.let { appleID.set(it) }
                    macNotaryPassword?.let { password.set(it) }
                    macNotaryTeamId?.let { teamID.set(it) }
                }
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDisplayName</key>
                        <string>Dockhand</string>
                        <key>NSDesktopFolderUsageDescription</key>
                        <string>Dockhand 需要访问桌面文件，以便浏览和管理文件。</string>
                        <key>NSDocumentsFolderUsageDescription</key>
                        <string>Dockhand 需要访问文稿文件，以便浏览和管理文件。</string>
                        <key>NSDownloadsFolderUsageDescription</key>
                        <string>Dockhand 需要访问下载文件，以便浏览和管理文件。</string>
                        <key>NSRemovableVolumesUsageDescription</key>
                        <string>Dockhand 需要访问外接磁盘，以便浏览和管理其中的文件。</string>
                        <key>NSNetworkVolumesUsageDescription</key>
                        <string>Dockhand 需要访问网络卷，以便浏览和管理其中的文件。</string>
                    """.trimIndent()
                }
            }
        }

        // Compose Desktop 的 JVM 打包使用 ProGuard；release 只做 shrink，
        // 并把处理后的依赖 jar 合并成单一输出。
        buildTypes.release {
            proguard {
                // 启动阶段出现 obfuscate 后的 NoClassDefFoundError；release 先只做
                // shrink，避免优化/混淆破坏 Compose、Coil 或委托类的运行时查找。
                isEnabled.set(true)
                optimize.set(false)
                obfuscate.set(false)
                joinOutputJars.set(true)
                configurationFiles.from(layout.projectDirectory.file("proguard-rules.pro"))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// macOS bundle compliance
// ---------------------------------------------------------------------------
fun injectPrivacyManifest(
    taskName: String,
    appPath: String,
) {
    // 这些 File 在配置阶段捕获，避免 doLast 执行时访问 Task.project，
    // 从而兼容 configuration cache。
    val manifest = project.file("PrivacyInfo.xcprivacy")
    val app = project.file(appPath)
    val entitlements = project.file(
        if (isAppStoreBuild) "entitlements-appstore.plist"
        else "entitlements.plist"
    )
    val identity = macSignIdentity ?: "-"

    tasks.configureEach {
        if (name != taskName) return@configureEach
        inputs.file(manifest)
        doLast {
            val resources = app.resolve("Contents/Resources").apply { mkdirs() }
            manifest.copyTo(resources.resolve(manifest.name), overwrite = true)
            // 复制资源会改变 bundle 内容，必须重新签名，否则签名失效。
            val result = ProcessBuilder(
                "/usr/bin/codesign",
                "--force",
                "--deep",
                "--options", "runtime",
                "--entitlements", entitlements.absolutePath,
                "--sign", identity,
                app.absolutePath,
            ).inheritIO().start().waitFor()
            check(result == 0) { "codesign failed with exit code $result" }
        }
    }
}

injectPrivacyManifest(
    "createDistributableImpl",
    "build/compose/binaries/main/app/Dockhand.app",
)
injectPrivacyManifest(
    "createReleaseDistributableImpl",
    "build/compose/binaries/main-release/app/Dockhand.app",
)
