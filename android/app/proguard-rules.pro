# Add project specific ProGuard rules here.
-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Activity
-keep class com.learne.data.model.** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer