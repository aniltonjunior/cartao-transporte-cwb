package br.com.cartaourbs.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import br.com.cartaourbs.util.Cpf

/**
 * Mostra o CPF como "000.000.000-00" enquanto o estado guarda só os dígitos.
 *
 * O [OffsetMapping] traduz a posição do cursor entre o texto real (dígitos)
 * e o exibido (com pontos e traço); sem ele o cursor "pula" ao digitar.
 * As contas ficam em [Cpf], que tem testes unitários.
 */
object CpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digitos = text.text
        val mapeamento = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                Cpf.posicaoFormatada(offset, digitos.length)

            override fun transformedToOriginal(offset: Int): Int =
                Cpf.posicaoOriginal(offset, digitos.length)
        }
        return TransformedText(AnnotatedString(Cpf.formatar(digitos)), mapeamento)
    }
}
