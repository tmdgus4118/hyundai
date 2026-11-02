# Pleos Connect SDK: AIDL 바인더와 직렬화 모델을 리플렉션으로 쓰므로 통째로 보존
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# kotlinx.serialization (NaviHelper 데이터 모델)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Gson (FusedLocation 내부)
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# AAOS 플랫폼 라이브러리는 기기에서 제공
-dontwarn android.car.**
