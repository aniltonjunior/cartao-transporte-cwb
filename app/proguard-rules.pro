# Regras do R8 (build de release).
#
# Retrofit, OkHttp, Okio, Tink, DataStore e Compose já trazem as próprias
# regras dentro das bibliotecas; aqui só entra o que falta para este app.

# --- Retrofit -----------------------------------------------------------------
# As interfaces de API (SbeApi) são lidas por reflexão. As regras do Retrofit
# já as mantêm; estes atributos garantem as anotações e os genéricos
# (Response<ResponseBody>) usados nas funções suspend.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# --- Avisos de classes opcionais ----------------------------------------------
# Dependências opcionais que as bibliotecas só usam se estiverem presentes.
# Sem estas linhas o R8 para o build com "Missing class".
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Jsoup: suporte opcional a expressões regulares RE2J e anotações de
# nulidade (JSpecify) que só existem na compilação.
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.annotations.**
# Tink: anotações usadas só em tempo de compilação.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# --- Jsoup --------------------------------------------------------------------
# Mantido inteiro por precaução: o parser carrega tabelas internas (entidades
# HTML) e um erro aqui só apareceria no release, no aparelho. Custa ~400 KB.
-keep class org.jsoup.** { *; }

# --- Logs ---------------------------------------------------------------------
# Remove chamadas de log de depuração/informação do release (os logs de erro do
# app já só rodam em debug; isto também limpa os de bibliotecas).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
