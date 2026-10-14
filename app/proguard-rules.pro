# ==============================================================================
# SECURE EXAM BROWSER - R8 / PROGUARD OBFUSCATION & SHRINKING RULES
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Bytecode Protection & Source Name Masking
# ------------------------------------------------------------------------------
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 2. WebView & JavaScript Interface Protection (CRITICAL FOR EXAM INTEGRITY)
# ------------------------------------------------------------------------------
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.security.ExamAutoSaveBridge { *; }

# ------------------------------------------------------------------------------
# 3. Room Database & Local Data Entities
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 4. JSON Serialization & Network Models (Moshi / Retrofit / OkHttp)
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keepclassmembers enum * { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ------------------------------------------------------------------------------
# 5. Security Models & Payloads
# ------------------------------------------------------------------------------
-keep class com.example.security.ParsedQrPayload { *; }
-keep class com.example.security.ValidationResult { *; }
-keep class com.example.security.BreachAuditLog { *; }
-keep class com.example.security.ExamSecurityPolicy { *; }
-keep class com.example.security.ExamSecurityManager { *; }

# ------------------------------------------------------------------------------
# 6. ZXing QR Engine & CameraX
# ------------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-dontwarn androidx.camera.**

# ------------------------------------------------------------------------------
# 7. Kotlin Coroutines & Jetpack Compose
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ------------------------------------------------------------------------------
# 8. Sound & Synthesis Protection
# ------------------------------------------------------------------------------
-keep class com.example.utils.SoundUtils { *; }

