package br.com.cartaourbs.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaldoCartaoTest {

    private fun saldo(centavos: Long) =
        SaldoCartao(numeroCartao = null, saldoCentavos = centavos, titular = null, atualizadoEm = null)

    /** O formato pt-BR usa espaço não separável (U+00A0); normaliza para comparar. */
    private fun SaldoCartao.texto() = saldoFormatado.replace('\u00A0', ' ')

    @Test
    fun `formata saldo com reais e centavos`() {
        assertEquals("R${'$'} 51,00", saldo(5100).texto())
    }

    @Test
    fun `formata centavos com zero a esquerda`() {
        assertEquals("R${'$'} 0,05", saldo(5).texto())
        assertEquals("R${'$'} 1,09", saldo(109).texto())
    }

    @Test
    fun `formata saldo zero`() {
        assertEquals("R${'$'} 0,00", saldo(0).texto())
    }

    @Test
    fun `formata valor alto com separador de milhar`() {
        assertEquals("R${'$'} 1.234,56", saldo(123456).texto())
        assertEquals("R${'$'} 1.000.000,00", saldo(100_000_000).texto())
    }

    @Test
    fun `formata saldo negativo`() {
        val texto = saldo(-150).texto()
        // O formato antigo gerava "R$ -1,-50".
        assertTrue("deveria ter sinal negativo: $texto", texto.startsWith("-"))
        assertTrue("deveria terminar em 1,50: $texto", texto.endsWith("1,50"))
        assertEquals(1, texto.count { it == '-' })
    }

    @Test
    fun `usa espaco nao separavel entre o simbolo e o valor`() {
        assertEquals("R${'$'}\u00A051,00", saldo(5100).saldoFormatado)
    }
}
