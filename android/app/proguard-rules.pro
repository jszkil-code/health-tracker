# Minification is currently disabled for release builds (see app/build.gradle.kts).
# Keep rules for when it is turned on:
-keep class net.zetetic.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.slf4j.**
