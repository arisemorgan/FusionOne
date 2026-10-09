-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class com.fusionone.app.**$$serializer { *; }
-keepclassmembers class com.fusionone.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.fusionone.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class androidx.room.** { *; }
