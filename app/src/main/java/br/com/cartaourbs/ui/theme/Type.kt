package br.com.cartaourbs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

/**
 * Tipografia padrão do Material 3, com o título grande (usado no valor do
 * saldo) em negrito. Os tamanhos são em sp, então respeitam o tamanho de
 * fonte escolhido pelo usuário nas configurações de acessibilidade.
 */
val Tipografia = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    )
}
