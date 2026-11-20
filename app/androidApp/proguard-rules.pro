# R8 Full Mode rules for UNICEP Portal (Kotlinx Serialization, Ktor OkHttp, WorkManager, Compose)

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# Kotlinx Serialization
-keepclassmembers class me.muriloscolari.unicep.data.api.** { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.ktor.**

# WorkManager Workers & BroadcastReceivers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class me.muriloscolari.unicep.platform.NotificationSyncReceiver { *; }

# Remove verbose debug logs in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
