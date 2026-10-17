package br.com.cartaourbs.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mesma estrutura do extrato real do SBE, com dados fictícios. */
class ExtratoParserTest {

    private val extratoReal = """
        <table class="reportResultNew" style="width:100%;border:1px solid black;">
            <thead>
                <tr>
                    <td>Data</td><td>Número de Série</td><td>Operação da Conta</td><td>Linha</td>
                    <td>Trecho</td><td>Veículo</td><td>Valor(R${'$'})</td><td>Saldo Cartão</td><td>Saldo Agendado</td>
                </tr>
            </thead>
            <tbody>
                <tr class="">
                    <td nowrap>03/10/2026 17:47:47</td>
                    <td>0000000001</td>
                    <td title="Processamento de débitos" class="text"> Débito </td>
                    <td title="OPER S/LINHA"> 000 </td>
                    <td>PADRAO</td>
                    <td title="006 - Empresa Exemplo"> 00025 </td>
                    <td nowrap style="text-align:right;" title=""> - 6,00 </td>
                    <td nowrap style="text-align:right;"> 63,00 </td>
                    <td nowrap style="text-align:right;"> -- </td>
                </tr>
                <tr class="row_off">
                    <td nowrap>14/10/2026 19:53:48</td>
                    <td>0000000001</td>
                    <td title="Processamento de débitos" class="text"> Débito </td>
                    <td title="PRAÇA CARLOS GOMES"> TCG </td>
                    <td>PADRAO</td>
                    <td title=""> 00043 </td>
                    <td nowrap style="text-align:right;" title=""> - 6,00 </td>
                    <td nowrap style="text-align:right;"> 1.057,00 </td>
                    <td nowrap style="text-align:right;"> 12,00 </td>
                </tr>
            </tbody>
            <tfoot>
                <tr><td colspan="12">
                    <table class="reportResultNew" style="width:100%;border:1px solid black;">
                        <thead><tr><td>Total de Débitos</td><td>Total de Integrações</td><td>Total Geral</td></tr></thead>
                        <tbody><tr><td>2</td><td>0</td><td>2</td></tr></tbody>
                    </table>
                </tr></td>
            </tfoot>
        </table>
    """.trimIndent()

    @Test
    fun `le exatamente as linhas da tabela principal e ignora os totais do rodape`() {
        val movs = requireNotNull(ExtratoParser.parse(extratoReal))
        assertEquals(2, movs.size)
    }

    @Test
    fun `extrai os campos da movimentacao`() {
        val m = requireNotNull(ExtratoParser.parse(extratoReal))[0]
        assertEquals("03/10/2026 17:47:47", m.dataHora)
        assertEquals("0000000001", m.numeroSerie)
        assertEquals("Débito", m.operacao)
        assertEquals("000", m.linhaCodigo)
        assertEquals("OPER S/LINHA", m.linhaNome)
        assertEquals("PADRAO", m.trecho)
        assertEquals("00025", m.veiculo)
        assertEquals("006 - Empresa Exemplo", m.empresa)
        assertEquals(-600L, m.valorCentavos)
        assertEquals(6300L, m.saldoCartaoCentavos)
        assertNull("'--' vira null", m.saldoAgendadoCentavos)
    }

    @Test
    fun `nome da linha vem do title e milhar e interpretado`() {
        val m = requireNotNull(ExtratoParser.parse(extratoReal))[1]
        assertEquals("TCG", m.linhaCodigo)
        assertEquals("PRAÇA CARLOS GOMES", m.linhaNome)
        assertNull("title vazio vira null", m.empresa)
        assertEquals(105700L, m.saldoCartaoCentavos)
        assertEquals(1200L, m.saldoAgendadoCentavos)
    }

    @Test
    fun `tabela sem linhas e lista vazia`() {
        val html = """
            <table class="reportResultNew"><thead><tr><td>Data</td></tr></thead><tbody></tbody></table>
        """.trimIndent()
        assertEquals(emptyList<Any>(), ExtratoParser.parse(html))
    }

    @Test
    fun `aviso de nenhum registro e lista vazia`() {
        assertEquals(emptyList<Any>(), ExtratoParser.parse("<div>Nenhum registro encontrado.</div>"))
    }

    @Test
    fun `resposta sem tabela nem aviso e nula`() {
        assertNull(ExtratoParser.parse("<html><body>tela de login</body></html>"))
    }

    @Test
    fun `valores com sinal`() {
        assertEquals(-600L, ExtratoParser.valorEmCentavos(" - 6,00 "))
        assertEquals(600L, ExtratoParser.valorEmCentavos("+ 6,00"))
        assertEquals(106300L, ExtratoParser.valorEmCentavos("1.063,00"))
        assertNull(ExtratoParser.valorEmCentavos("--"))
        assertNull(ExtratoParser.valorEmCentavos(""))
    }

    // ---- idUsuario -------------------------------------------------------

    @Test
    fun `idUsuario em campo escondido`() {
        val id = ExtratoParser.extrairIdUsuario(
            """<form><input type="hidden" name="filtro.idUsuario" value="4242"/></form>"""
        )
        assertEquals("4242", id?.valor)
        assertEquals(ExtratoParser.Origem.CAMPO_DE_FORMULARIO, id?.origem)
    }

    @Test
    fun `idUsuario em atributo data`() {
        val id = ExtratoParser.extrairIdUsuario("""<div data-idusuario="99"></div>""")
        assertEquals("99", id?.valor)
        assertEquals(ExtratoParser.Origem.ATRIBUTO, id?.origem)
    }

    @Test
    fun `idUsuario em script`() {
        val html = """
            <script>
              ${'$'}.post(url, { 'filtro.idUsuario': '5150', 'filtro.dataInicial': ini });
            </script>
        """.trimIndent()
        val id = ExtratoParser.extrairIdUsuario(html)
        assertEquals("5150", id?.valor)
        assertEquals(ExtratoParser.Origem.SCRIPT, id?.origem)
    }

    @Test
    fun `idUsuario em query string de link`() {
        val id = ExtratoParser.extrairIdUsuario(
            """<a href="/sbe-web/x.html?filtro.idUsuario=31337&popup=true">ver</a>"""
        )
        assertEquals("31337", id?.valor)
    }

    @Test
    fun `sem idUsuario retorna nulo`() {
        assertNull(ExtratoParser.extrairIdUsuario("""<input name="documentoNacional" value="123"/>"""))
    }

    @Test
    fun `campo idUsuario vazio ou nao numerico e ignorado`() {
        assertNull(ExtratoParser.extrairIdUsuario("""<input name="idUsuario" value=""/>"""))
        assertTrue(ExtratoParser.extrairIdUsuario("""<input name="idUsuario" value="abc"/>""") == null)
    }
}
