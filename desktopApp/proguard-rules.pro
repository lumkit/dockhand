# Dockhand release rules (Compose Desktop uses ProGuard on JVM)

# Entry point
-keep class io.github.lumkit.dockhand.MainKt {
    public static void main(java.lang.String[]);
}

# Compose runtime/UI use class metadata in several places. Keep the runtime
# surface intact; application classes remain shrinkable/obfuscatable.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.ui.text.** { *; }

# Miuix and Compose resources: keep generated resource accessors and names.
-keep class dockhand.shared.generated.resources.** { *; }
-keep class top.yukonga.miuix.** { *; }

# FFM bridge is loaded by native symbol names.
-keep class io.github.lumkit.dockhand.data.file.MtpNativeBridge { *; }
-keep class io.github.lumkit.dockhand.data.file.MtpNativeBridge$Companion { *; }

# DataStore's protobuf-lite messages are accessed reflectively by field name.
# Shrinking/renaming them breaks PreferencesProto.preferences_ lookup at startup.
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keep class androidx.datastore.preferences.core.** { *; }
-keep class androidx.datastore.core.** { *; }

# Coil uses ServiceLoader and reflective component registries for decoders/fetchers.
# Shrinking or renaming these classes breaks image/SVG/video thumbnail loading in release.
-keep class coil3.** { *; }
-keep class okio.** { *; }
-keep class io.github.lumkit.dockhand.core.thumbnail.** { *; }
-keepclassmembers class * implements coil3.fetch.Fetcher$Factory { *; }
-keepclassmembers class * implements coil3.decode.Decoder$Factory { *; }
-keepclassmembers class * implements coil3.key.Keyer { *; }

# kotlinx.coroutines: ProGuard's shrinker can drop redundant direct interfaces
# (e.g. Job from JobSupport) while keeping invokespecial default-method calls.
# That produces a VerifyError (JobSupport.cancel) at runtime. Keep the whole
# coroutines surface: the app, Compose/Skiko and Ktor all rely on it.
-keep class kotlinx.coroutines.** { *; }

# Ktor Client (CIO): the engine/plugins are referenced directly, but Ktor's
# engine discovery and optional modules use service loaders and metadata.
# Keep the client surface intact in release; our only Ktor usage is the
# GitHub update check (HttpClient(CIO) + HttpTimeout + defaultRequest).
-keep class io.ktor.** { *; }
-keep class kotlinx.io.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn io.ktor.**

# JNA callback descriptors must keep their original class/type names.
-keep class com.sun.jna.** { *; }

# SQLite JDBC and Skiko use JNI/native method names.
-keep class org.sqlite.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }

# Room generated databases/DAOs and entities are accessed reflectively.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase_Impl { *; }

# Kotlin serialization generated serializers.
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer {
    *** serializer(...);
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep annotations and metadata needed by reflection/serialization.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, MethodParameters, Exceptions, SourceFile, LineNumberTable

# Optional dependencies not present on desktop.
-dontwarn org.jetbrains.skiko.**
-dontwarn org.sqlite.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.**
-dontwarn java.lang.management.**
-dontwarn org.apache.commons.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.sun.jna.**
-dontwarn org.tukaani.**
-dontwarn org.slf4j.**
