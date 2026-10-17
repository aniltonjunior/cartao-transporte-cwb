# Regras padrão de ProGuard para o app.
# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Jsoup
-keep class org.jsoup.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
