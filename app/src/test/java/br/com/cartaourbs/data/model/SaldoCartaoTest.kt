package br.com.cartaourbs.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SaldoCartaoTest {

    private fun saldo(centavos: Long) =
        SaldoCartao(numeroCartao = null, saldoCentavos = centavos, titular = null, atualizadoEm = null)

    @Test
    fun `formata saldo com reais e centavos`() {
        assertEquals("R${'$'} 51,00", saldo(5100).saldoFormatado)
    }

    @Test
    fun `formata centavos com zero a esquerda`() {
        assertEquals("R${'$'} 0,05", saldo(5).saldoFormatado)
        assertEquals("R${'$'} 1,09", saldo(109).saldoFormatado)
    }

    @Test
    fun `formata saldo zero`() {
        assertEquals("R${'$'} 0,00", saldo(0).saldoFormatado)
    }

    @Test
    fun `formata valor alto`() {
        assertEquals("R${'$'} 1234,56", saldo(123456).saldoFormatado)
    }
}
