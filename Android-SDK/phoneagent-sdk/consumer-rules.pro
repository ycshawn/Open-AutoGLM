# PhoneAgent SDK ProGuard rules

# Keep public API classes
-keep public class com.autoglm.phoneagent.** {
    public *;
}

# Keep data classes
-keep class com.autoglm.phoneagent.model.** { *; }
-keep class com.autoglm.phoneagent.agent.** { *; }
-keep class com.autoglm.phoneagent.action.** { *; }

# Keep Gson model classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
