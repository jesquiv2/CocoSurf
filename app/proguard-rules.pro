# Coco Surf · reglas de R8 para la versión release.
# GeckoView trae sus propias reglas (consumer-rules) para lo que usa por JNI.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Room genera código por KSP; se conserva lo que usa por reflexión.
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.mozilla.**
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**
-dontwarn fi.iki.elonen.**
-keep class fi.iki.elonen.** { *; }
-dontwarn androidx.media3.**
