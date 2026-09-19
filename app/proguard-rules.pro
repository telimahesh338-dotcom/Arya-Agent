# ARYA ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class com.arya.memory.db.** { *; }
-keep class com.arya.actions.ActionType { *; }
-keep class com.arya.perception.ScreenState { *; }
-keep class * implements kotlinx.serialization.KSerializer
-dontwarn dev.rikka.shizuku.**
