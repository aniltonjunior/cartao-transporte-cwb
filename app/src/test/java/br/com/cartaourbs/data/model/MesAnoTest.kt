package br.com.cartaourbs.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MesAnoTest {

    @Test
    fun `datas no formato do SBE`() {
        val outubro = MesAno(2026, 10)
        assertEquals("01/10/2026", outubro.dataInicial)
        assertEquals("31/10/2026", outubro.dataFinal)
        assertEquals("30/11/2026", MesAno(2026, 11).dataFinal)
    }

    @Test
    fun `fevereiro e anos bissextos`() {
        assertEquals(28, MesAno(2026, 2).ultimoDia)
        assertEquals(29, MesAno(2028, 2).ultimoDia)
        assertEquals(28, MesAno(2100, 2).ultimoDia) // divisível por 100
        assertEquals(29, MesAno(2000, 2).ultimoDia) // divisível por 400
    }

    @Test
    fun `navega entre meses virando o ano`() {
        assertEquals(MesAno(2025, 12), MesAno(2026, 1).anterior())
        assertEquals(MesAno(2027, 1), MesAno(2026, 12).seguinte())
        assertEquals(MesAno(2026, 9), MesAno(2026, 10).anterior())
    }

    @Test
    fun `compara meses`() {
        assertTrue(MesAno(2026, 9) < MesAno(2026, 10))
        assertTrue(MesAno(2025, 12) < MesAno(2026, 1))
    }

    @Test
    fun `nome em portugues`() {
        assertEquals("outubro de 2026", MesAno(2026, 10).nomeCompleto())
        assertEquals("março de 2026", MesAno(2026, 3).nomeCompleto())
    }

    @Test
    fun `mes atual a partir do calendario`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16) }
        assertEquals(MesAno(2026, 10), MesAno.atual(cal))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mes invalido`() {
        MesAno(2026, 13)
    }
}
