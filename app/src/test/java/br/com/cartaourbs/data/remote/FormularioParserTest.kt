package br.com.cartaourbs.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dados 100% fictícios: CPF e cartões não são de ninguém. */
class FormularioParserTest {

    /** Mesma estrutura da página real de seleção de cartão (CPF + 3 cartões). */
    private val htmlFormulario = """
        <form id="formConsultarInformacaoUsurio" method="post">
          <input type="text" name="documentoNacional" value="12345678909" readonly="readonly"/>
          <select name="cartao" id="cartao" class="required">
            <option value="1000001">0001234567 - Usuário - Ativo</option>
            <option value="1000002">0007654321 - Usuário - Com solicitação de bloqueio</option>
            <option value="1000003">0009999999 - Usuário - Com solicitação de bloqueio</option>
          </select>
        </form>
    """.trimIndent()

    @Test
    fun `extrai o CPF do campo documentoNacional`() {
        val form = requireNotNull(FormularioParser.parse(htmlFormulario))
        assertEquals("12345678909", form.documentoNacional)
    }

    @Test
    fun `extrai todos os cartoes da lista`() {
        val form = requireNotNull(FormularioParser.parse(htmlFormulario))
        assertEquals(3, form.cartoes.size)
        assertEquals("1000001", form.cartoes[0].id)
        assertEquals("0001234567", form.cartoes[0].numero)
        assertEquals(true, form.cartoes[0].ativo)
        assertEquals(false, form.cartoes[1].ativo)
    }

    @Test
    fun `cartao preferido e o primeiro ativo`() {
        val form = requireNotNull(FormularioParser.parse(htmlFormulario))
        val pref = requireNotNull(form.cartaoPreferido)
        assertEquals("1000001", pref.id)
        assertEquals("0001234567", pref.numero)
    }

    @Test
    fun `cartao preferido cai para o primeiro quando nenhum e ativo`() {
        val html = """
            <input name="documentoNacional" value="11122233344"/>
            <select name="cartao">
              <option value="10">0000000001 - Usuário - Bloqueado</option>
              <option value="20">0000000002 - Usuário - Cancelado</option>
            </select>
        """.trimIndent()
        val form = requireNotNull(FormularioParser.parse(html))
        assertEquals("10", form.cartaoPreferido?.id)
    }

    @Test
    fun `situacao Inativo nao conta como ativo`() {
        // Regressão: a regra antiga usava contains("Ativo") e "Inativo" passava.
        val html = """
            <input name="documentoNacional" value="11122233344"/>
            <select name="cartao">
              <option value="10">0000000001 - Usuário - Inativo</option>
              <option value="20">0000000002 - Usuário - Ativo</option>
            </select>
        """.trimIndent()
        val form = requireNotNull(FormularioParser.parse(html))
        assertEquals(false, form.cartoes[0].ativo)
        assertEquals("20", form.cartaoPreferido?.id)
        assertEquals("Inativo", form.cartoes[0].situacao)
    }

    @Test
    fun `cartaoOuPreferido escolhe pelo id ou cai para o preferido`() {
        val form = requireNotNull(FormularioParser.parse(htmlFormulario))
        assertEquals("1000002", form.cartaoOuPreferido("1000002")?.id)
        assertEquals("1000001", form.cartaoOuPreferido("nao-existe")?.id)
        assertEquals("1000001", form.cartaoOuPreferido(null)?.id)
    }

    @Test
    fun `retorna nulo quando nao ha select de cartao`() {
        val html = "<form><input name='documentoNacional' value='x'/></form>"
        assertNull(FormularioParser.parse(html))
    }

    @Test
    fun `ignora options sem value`() {
        val html = """
            <input name="documentoNacional" value="11122233344"/>
            <select name="cartao">
              <option value="">Selecione...</option>
              <option value="99">0000000009 - Usuário - Ativo</option>
            </select>
        """.trimIndent()
        val form = requireNotNull(FormularioParser.parse(html))
        assertEquals(1, form.cartoes.size)
        assertEquals("99", form.cartoes[0].id)
    }
}
