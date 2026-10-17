import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Dados da chave de assinatura (upload key) para o build de release.
 *
 * Ficam em `keystore.properties`, na raiz do projeto, que NÃO vai para o git
 * (ver .gitignore e docs/PUBLICACAO.md). Sem esse arquivo, o release é gerado
 * sem assinatura: compila, mas não pode ser instalado nem enviado à Play Store.
 */
val assinatura = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    // Pacote do código-fonte (classe R, BuildConfig). Não aparece para o usuário.
    namespace = "br.com.cartaourbs"
    compileSdk = 36

    defaultConfig {
        // Identificador do app na Play Store. ATENÇÃO: depois do primeiro envio
        // à Play Store ele não pode mais mudar. Sem "urbs" no nome, para não
        // sugerir vínculo com a URBS.
        applicationId = "br.com.saldotransportecwb"
        minSdk = 24
        // Exigência da Play Store para apps novos desde 31/08/2026.
        targetSdk = 36
        // Aumente versionCode a cada envio à Play Store (1, 2, 3...).
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Mostrados na tela "Sobre". Configure em gradle.properties antes de
        // publicar (ver docs/PUBLICACAO.md). Vazios = a tela esconde o item.
        buildConfigField(
            "String", "URL_POLITICA_PRIVACIDADE",
            "\"${providers.gradleProperty("urlPoliticaPrivacidade").getOrElse("")}\"",
        )
        buildConfigField(
            "String", "EMAIL_CONTATO",
            "\"${providers.gradleProperty("emailContato").getOrElse("")}\"",
        )
    }

    signingConfigs {
        if (assinatura.isNotEmpty()) {
            create("release") {
                // Caminho relativo à raiz do projeto, ou absoluto.
                storeFile = rootProject.file(assinatura.getProperty("storeFile"))
                storePassword = assinatura.getProperty("storePassword")
                keyAlias = assinatura.getProperty("keyAlias")
                keyPassword = assinatura.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: remove código e recursos não usados e ofusca nomes.
            // App menor e mais difícil de inspecionar.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Gera BuildConfig.DEBUG e VERSION_NAME, usados no app.
        buildConfig = true
    }
}

// Substitui o antigo android { kotlinOptions { jvmTarget = "17" } }, obsoleto
// no Kotlin 2.x.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Rede
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    // Armazenamento seguro de credenciais: DataStore + criptografia Tink
    // (chave protegida pelo Android Keystore).
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)

    debugImplementation(libs.androidx.ui.tooling)

    // Testes unitários (JVM, sem emulador)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.jsoup)
}
