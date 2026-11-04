# ProGuard rules for Public Land Map
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepnames @com.squareup.moshi.JsonClass class *

# Keep Moshi-generated adapters for DTOs
-keep class com.connorhansen.publiclandmap.data.remote.dto.** { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# MapLibre
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# JTS Geometry
-keep class org.locationtech.jts.** { *; }

# Domain models (used with Moshi/Gson serialization)
-keep class com.connorhansen.publiclandmap.domain.model.** { *; }

# Room entities
-keep class com.connorhansen.publiclandmap.data.local.db.entity.** { *; }

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# DataStore
-keep class androidx.datastore.** { *; }
