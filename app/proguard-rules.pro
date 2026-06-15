# Keep Retrofit / kotlinx.serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class net.secorp.rssreader.**$$serializer { *; }
-keepclassmembers class net.secorp.rssreader.** {
    *** Companion;
}
-keepclasseswithmembers class net.secorp.rssreader.** {
    kotlinx.serialization.KSerializer serializer(...);
}
