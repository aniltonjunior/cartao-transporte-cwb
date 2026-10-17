package br.com.cartaourbs.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dados 100% fictícios: nome, CPF e cartões não são de ninguém. */
class SaldoParserTest {

    /**
     * Mesma estrutura da página real do SBE: duas células de valor sem
     * rótulo, a primeira com o agendado (0,00) e a segunda com o saldo atual.
     */
    private val htmlSaldoSemRotulo = """
        <html><body>
          <div class="dadosLogado">
            <nobr> Fulano de Tal (12345678909) </nobr>
          </div>
          <a title="Detalhes do Saldo Atual"></a>
          <table><tr>
            <td style="width:50%;text-align:left"> R${'$'} 0,00 </td>
            <td style="width:50%;text-align:left"> R${'$'} 51,00 </td>
          </tr></table>
          <select name="cartao" id="cartao">
            <option value="1000001">0001234567 - Usuário - Ativo</option>
          </select>
        </body></html>
    """.trimIndent()

    @Test
    fun `sem rotulo usa a posicao da pagina real - ultimo valor e o saldo atual`() {
        val saldo = requireNotNull(SaldoParser.parse(htmlSaldoSemRotulo))
        assertEquals(5100L, saldo.saldoCentavos)
        assertEquals("R${'$'} 51,00", saldo.saldoFormatado)
    }

    @Test
    fun `saldo atual zero com agendado positivo mostra zero`() {
        // Regressão: a regra antiga mostrava R$ 10,00 (o agendado) aqui.
        val html = """
            <table>
              <tr><td>Saldo Atual:</td><td> R${'$'} 0,00 </td></tr>
              <tr><td>Saldo Agendado:</td><td> R${'$'} 10,00 </td></tr>
            </table>
        """.trimIndent()
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(0L, saldo.saldoCentavos)
    }

    @Test
    fun `agendado antes do atual com rotulos na mesma linha`() {
        val html = """
            <table>
              <tr><td>Saldo Agendado</td><td> R${'$'} 20,00 </td></tr>
              <tr><td>Saldo Atual</td><td> R${'$'} 7,50 </td></tr>
            </table>
        """.trimIndent()
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(750L, saldo.saldoCentavos)
    }

    @Test
    fun `rotulos em linha de cabecalho acima dos valores`() {
        val html = """
            <table>
              <tr><td>Saldo Agendado</td><td>Saldo Atual</td></tr>
              <tr><td> R${'$'} 15,00 </td><td> R${'$'} 0,00 </td></tr>
            </table>
        """.trimIndent()
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(0L, saldo.saldoCentavos)
    }

    @Test
    fun `rotulos em thead`() {
        val html = """
            <table>
              <thead><tr><td>Saldo Atual</td><td>Saldo Agendado</td></tr></thead>
              <tbody><tr><td> R${'$'} 3,20 </td><td> R${'$'} 40,00 </td></tr></tbody>
            </table>
        """.trimIndent()
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(320L, saldo.saldoCentavos)
    }

    @Test
    fun `so ha valor agendado - nao inventa saldo atual`() {
        val html = """
            <table><tr><td>Saldo Agendado</td><td> R${'$'} 9,00 </td></tr></table>
        """.trimIndent()
        assertNull(SaldoParser.parse(html))
    }

    @Test
    fun `extrai o titular sem o CPF`() {
        val saldo = requireNotNull(SaldoParser.parse(htmlSaldoSemRotulo))
        assertEquals("Fulano de Tal", saldo.titular)
    }

    @Test
    fun `extrai o numero do cartao do primeiro option`() {
        val saldo = requireNotNull(SaldoParser.parse(htmlSaldoSemRotulo))
        assertEquals("0001234567", saldo.numeroCartao)
    }

    @Test
    fun `interpreta valores com milhar`() {
        val html = """
            <div class="dadosLogado"> Fulano de Tal (12345678909) </div>
            <table><tr><td> R${'$'} 1.234,56 </td></tr></table>
        """.trimIndent()
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(123456L, saldo.saldoCentavos)
    }

    @Test
    fun `saldo zero quando o unico valor e zero`() {
        val html = "<table><tr><td> R${'$'} 0,00 </td></tr></table>"
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(0L, saldo.saldoCentavos)
    }

    @Test
    fun `valor fora de celula usa o fallback de texto`() {
        val html = "<div>Seu saldo: <span>R${'$'} 12,00</span></div>"
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(1200L, saldo.saldoCentavos)
    }

    @Test
    fun `retorna nulo quando nao ha valor monetario`() {
        val html = "<html><body><p>sem saldo aqui</p></body></html>"
        assertNull(SaldoParser.parse(html))
    }

    @Test
    fun `titular ausente nao quebra a extracao do saldo`() {
        val html = "<table><tr><td> R${'$'} 10,00 </td></tr></table>"
        val saldo = requireNotNull(SaldoParser.parse(html))
        assertEquals(1000L, saldo.saldoCentavos)
        assertNull(saldo.titular)
    }
}
