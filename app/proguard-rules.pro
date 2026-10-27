# ProGuard / R8 rules for DriveMount

# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.drivemount.app.**$$serializer { *; }
-keepclassmembers class com.drivemount.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.drivemount.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── OkHttp ──
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── Hilt ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Tink ──
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Credential Manager ──
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# ── Google Identity ──
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ── General ──
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

