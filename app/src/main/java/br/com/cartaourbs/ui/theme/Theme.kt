package br.com.cartaourbs.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val EsquemaClaro = lightColorScheme(
    primary = AzulPrimario,
    onPrimary = SobreAzul,
    primaryContainer = AzulContainer,
    onPrimaryContainer = SobreAzulContainer,
    secondary = Secundaria,
    secondaryContainer = SecundariaContainer,
    onSecondaryContainer = SobreSecundariaContainer,
    background = FundoClaro,
    surface = SuperficieClara,
    onSurface = SobreSuperficieClara,
)

private val EsquemaEscuro = darkColorScheme(
    primary = AzulPrimarioEscuro,
    onPrimary = SobreAzulEscuro,
    primaryContainer = AzulContainerEscuro,
    onPrimaryContainer = SobreAzulContainerEscuro,
    secondary = SecundariaEscura,
    secondaryContainer = SecundariaContainerEscura,
    onSecondaryContainer = SobreSecundariaContainerEscura,
    background = FundoEscuro,
    surface = SuperficieEscura,
    onSurface = SobreSuperficieEscura,
)

/**
 * Tema do app.
 *
 * - Segue o modo claro/escuro do sistema.
 * - No Android 12+ usa as cores dinâmicas (Material You), tiradas do papel de
 *   parede. Nas versões anteriores, usa a paleta azul de [Color.kt].
 */
@Composable
fun CartaoUrbsTheme(
    escuro: Boolean = isSystemInDarkTheme(),
    coresDinamicas: Boolean = true,
    content: @Composable () -> Unit,
) {
    val esquema = when {
        coresDinamicas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (escuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        escuro -> EsquemaEscuro
        else -> EsquemaClaro
    }

    MaterialTheme(
        colorScheme = esquema,
        typography = Tipografia,
        content = content,
    )
}
