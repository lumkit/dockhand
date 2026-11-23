This is a Kotlin Multiplatform project targeting Desktop (JVM).

## 架构

- [主页架构：双列表、焦点、顶栏路径、侧边导航](docs/home-architecture.md)

## MTP 后端

macOS 没有系统级 MTP 挂载 API。项目内嵌了 MIT/Apache-2.0 的
[`mtp-rs`](https://github.com/vdavid/mtp-rs) 作为 Rust native bridge：

- 原生库源码：`native/mtp-bridge`
- Gradle 的 `buildMtpBridge` 任务会执行 `cargo build --release`，并把
  `libdockhand_mtp.dylib` 打进 `shared` 的 resources
- JVM 侧通过 JDK 25 的 FFM 加载，不依赖 OpenMTP、JNA、JNI、libmtp 或 libusb
- 同时连接多台 MTP 设备时，会按 USB location 分别枚举，侧边栏显示为
  `设备型号 · 存储名`
- 侧边栏分组内的条目支持拖拽排序，顺序会持久化到本地设置
- 构建源码需要 Rust 1.90+；最终 App 不需要用户安装 Rust

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Desktop tests: `./gradlew :shared:jvmTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…