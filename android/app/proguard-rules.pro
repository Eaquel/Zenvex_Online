# Zenvex Online R8 kuralları
# JNI: Rust kütüphanesi Native nesnesindeki yöntemleri adlarıyla çağırır/bulur
-keep class com.zenvex.online.game.Native { *; }
-keepclasseswithmembernames class * { native <methods>; }
# Google ile giriş (Credential Manager)
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**
# org.json (Android çerçevesi) kullanılır
-dontwarn org.json.**
# hata raporlarında satır numaraları
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
