# Hilt 関連の警告を抑制
-dontwarn com.google.errorprone.annotations.**

# kotlinx.serialization 用の保持ルール
# @Serializable がついたクラスのシリアライザーを保持
-keep,includedescriptorclasses class io.github.taskengineer.rcgear.**$$serializer { *; }
-keepclassmembers class io.github.taskengineer.rcgear.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.taskengineer.rcgear.** {
    kotlinx.serialization.KSerializer serializer(...);
}
