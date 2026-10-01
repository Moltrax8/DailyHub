# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.moltrax.personalnoteapp.**$$serializer { *; }
-keepclassmembers class com.moltrax.personalnoteapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.moltrax.personalnoteapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Sign-In / Play Services
-dontwarn com.google.android.gms.**

# Hilt
-keep class javax.inject.** { *; }
