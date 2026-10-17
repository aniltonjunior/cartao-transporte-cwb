package br.com.cartaourbs.util

/**
 * Regras do CPF: limpeza, validação dos dígitos verificadores e máscara
 * "000.000.000-00". Tudo puro (sem Android), para testar na JVM.
 *
 * O CPF é sempre guardado só com dígitos; a máscara é apenas visual (ver
 * `ui/CpfVisualTransformation.kt`).
 */
object Cpf {

    const val TAMANHO = 11

    /** Mantém só dígitos e corta em 11. Aceita texto colado com pontos e traço. */
    fun limpar(texto: String): String = texto.filter(Char::isDigit).take(TAMANHO)

    /**
     * Valida os dois dígitos verificadores. Rejeita sequências repetidas
     * (ex.: 111.111.111-11), que passam na conta mas não são CPFs válidos.
     */
    fun valido(digitos: String): Boolean {
        if (digitos.length != TAMANHO || !digitos.all(Char::isDigit)) return false
        if (digitos.all { it == digitos[0] }) return false

        val numeros = digitos.map { it - '0' }
        fun digito(quantos: Int): Int {
            // Pesos de (quantos + 1) até 2.
            val soma = (0 until quantos).sumOf { i -> numeros[i] * (quantos + 1 - i) }
            val resto = soma % 11
            return if (resto < 2) 0 else 11 - resto
        }
        return digito(9) == numeros[9] && digito(10) == numeros[10]
    }

    /** "12345678909" -> "123.456.789-09". Funciona com CPF incompleto. */
    fun formatar(digitos: String): String = buildString {
        digitos.forEachIndexed { i, c ->
            when (i) {
                3, 6 -> append('.')
                9 -> append('-')
            }
            append(c)
        }
    }

    /**
     * Posição do cursor no texto com máscara, dada a posição no texto só com
     * dígitos. Ex.: depois do 4º dígito ("123.4|") -> 5.
     */
    fun posicaoFormatada(posicao: Int, tamanhoDigitos: Int): Int {
        val p = posicao.coerceIn(0, tamanhoDigitos)
        val separadores = listOf(3, 6, 9).count { p > it }
        return (p + separadores).coerceAtMost(formatar("0".repeat(tamanhoDigitos)).length)
    }

    /** Caminho inverso de [posicaoFormatada]. */
    fun posicaoOriginal(posicaoFormatada: Int, tamanhoDigitos: Int): Int {
        val t = posicaoFormatada
        val p = when {
            t <= 3 -> t
            t <= 7 -> t - 1
            t <= 11 -> t - 2
            else -> t - 3
        }
        return p.coerceIn(0, tamanhoDigitos)
    }
}
