# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.keybook.vault.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.keybook.vault.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
