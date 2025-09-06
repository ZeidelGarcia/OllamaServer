# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Basic Android rules
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class com.google.android.material.** { *; }
-keep interface com.google.android.material.** { *; }

# Keep application class
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @org.jetbrains.annotations.NotNull *;
    @org.jetbrains.annotations.Nullable *;
}
