package br.com.cartaourbs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CpfTest {

    @Test
    fun `aceita CPFs com digitos verificadores corretos`() {
        // CPFs de exemplo, gerados só para teste.
        assertTrue(Cpf.valido("12345678909"))
        assertTrue(Cpf.valido("52998224725"))
        assertTrue(Cpf.valido("11144477735"))
    }

    @Test
    fun `rejeita digito verificador errado`() {
        assertFalse(Cpf.valido("12345678900"))
        assertFalse(Cpf.valido("52998224724"))
    }

    @Test
    fun `rejeita sequencias repetidas`() {
        assertFalse(Cpf.valido("00000000000"))
        assertFalse(Cpf.valido("11111111111"))
    }

    @Test
    fun `rejeita tamanho errado e caracteres que nao sao digitos`() {
        assertFalse(Cpf.valido("1234567890"))
        assertFalse(Cpf.valido("123456789091"))
        assertFalse(Cpf.valido("123.456.789-09"))
        assertFalse(Cpf.valido(""))
    }

    @Test
    fun `limpar mantem so digitos e corta em 11`() {
        assertEquals("12345678909", Cpf.limpar("123.456.789-09"))
        assertEquals("12345678909", Cpf.limpar("1234567890999"))
        assertEquals("", Cpf.limpar("abc"))
    }

    @Test
    fun `formata progressivamente`() {
        assertEquals("", Cpf.formatar(""))
        assertEquals("123", Cpf.formatar("123"))
        assertEquals("123.4", Cpf.formatar("1234"))
        assertEquals("123.456.7", Cpf.formatar("1234567"))
        assertEquals("123.456.789-0", Cpf.formatar("1234567890"))
        assertEquals("123.456.789-09", Cpf.formatar("12345678909"))
    }

    @Test
    fun `cursor no fim do texto continua no fim depois da mascara`() {
        for (n in 0..11) {
            val digitos = "1".repeat(n)
            assertEquals("n=$n", Cpf.formatar(digitos).length, Cpf.posicaoFormatada(n, n))
        }
    }

    @Test
    fun `posicoes conhecidas do cursor`() {
        // "123.456.789-09"
        assertEquals(3, Cpf.posicaoFormatada(3, 11)) // antes do primeiro ponto
        assertEquals(5, Cpf.posicaoFormatada(4, 11)) // depois do "4"
        assertEquals(11, Cpf.posicaoFormatada(9, 11)) // depois do "9", antes do "-"
        assertEquals(13, Cpf.posicaoFormatada(10, 11)) // depois do "0"
        assertEquals(3, Cpf.posicaoOriginal(4, 11)) // logo depois do ponto
        assertEquals(9, Cpf.posicaoOriginal(12, 11)) // logo depois do traço
    }

    @Test
    fun `ida e volta preserva a posicao original`() {
        for (n in 0..11) {
            for (p in 0..n) {
                val t = Cpf.posicaoFormatada(p, n)
                assertEquals("n=$n p=$p", p, Cpf.posicaoOriginal(t, n))
            }
        }
    }

    @Test
    fun `posicoes formatadas nunca passam do tamanho do texto exibido`() {
        for (n in 0..11) {
            val tamanho = Cpf.formatar("9".repeat(n)).length
            for (t in 0..tamanho) {
                val p = Cpf.posicaoOriginal(t, n)
                assertTrue(p in 0..n)
                assertTrue(Cpf.posicaoFormatada(p, n) in 0..tamanho)
            }
        }
    }
}
