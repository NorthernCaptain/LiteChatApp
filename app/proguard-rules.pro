# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class **JsonAdapter {
    <init>(...);
}
-keepnames @com.squareup.moshi.JsonClass class *

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
