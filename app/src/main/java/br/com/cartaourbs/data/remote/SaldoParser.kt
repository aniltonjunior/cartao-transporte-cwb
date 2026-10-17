package br.com.cartaourbs.data.remote

import br.com.cartaourbs.data.model.SaldoCartao
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Extrai o saldo, o titular e o número do cartão do HTML retornado pelo POST
 * em `consultarInformacoesCartaoUsuario.html` do SBE.
 *
 * Estrutura observada no HTML real:
 *  - Titular: bloco `div.dadosLogado`, no formato "Nome Completo (00000000000)".
 *  - Valores: duas células `<td style="width:50%">` lado a lado, ex.:
 *    "R$ 0,00" e "R$ 51,00". A SEGUNDA era o saldo atual (conferido com o
 *    extrato); a primeira, o saldo agendado.
 *  - Cartões: `<select name="cartao">` com `<option value="idInterno">NUMERO -
 *    Tipo - Status</option>`. O número exibido (ex.: 0001234567) é o do cartão.
 *
 * Como escolher o saldo atual:
 *  1. Pelo RÓTULO de cada célula com valor (célula ao lado, cabeçalho da
 *     coluna ou linha de cima): "agendado" é descartado; "saldo"/"atual" vence.
 *  2. Sem rótulo reconhecível: usa a posição da amostra real, ou seja, o
 *     ÚLTIMO valor não agendado.
 *
 * A regra antiga ("primeiro valor diferente de zero") mostrava o saldo
 * agendado quando o saldo atual era R$ 0,00.
 */
object SaldoParser {

    // Valor monetário "1.234,56" (com ou sem milhar).
    private const val VALOR = """((?:\d{1,3}(?:\.\d{3})*|\d+),\d{2})"""

    // "R$ 51,00" em qualquer lugar do texto.
    private val regexReais = Regex("""R\$[\s\u00A0]*$VALOR""")

    // Célula cujo conteúdo é SÓ um valor: "R$ 51,00".
    private val regexCelulaValor = Regex("""^R\$[\s\u00A0]*$VALOR$""")

    // Titular no formato "Nome (12345678901)".
    private val regexTitular = Regex("""(.+?)\s*\((\d{11})\)""")

    private enum class Tipo { ATUAL, AGENDADO, DESCONHECIDO }

    private data class CelulaValor(val centavos: Long, val tipo: Tipo)

    fun parse(html: String): SaldoCartao? {
        val doc = Jsoup.parse(html)

        val saldoCentavos = extrairSaldoAtual(doc) ?: return null

        // --- Titular ---
        val textoLogado = doc.selectFirst(".dadosLogado")?.text()
            ?: doc.selectFirst("[class*=dadosLogado]")?.text()
            ?: doc.text()
        val titular = regexTitular.find(textoLogado)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        // --- Número do cartão (option selecionado ou o primeiro) ---
        val select = doc.selectFirst("select[name=cartao]")
        val optionTexto = select?.selectFirst("option[selected]")?.text()
            ?: select?.selectFirst("option")?.text()
        val numeroCartao = optionTexto
            ?.substringBefore(" - ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return SaldoCartao(
            numeroCartao = numeroCartao,
            saldoCentavos = saldoCentavos,
            titular = titular,
            atualizadoEm = null,
        )
    }

    /** Retorna o saldo atual em centavos, ou null se não houver valor. */
    private fun extrairSaldoAtual(doc: Document): Long? {
        val celulas = doc.select("td").mapNotNull { td ->
            val match = regexCelulaValor.find(td.text().trim()) ?: return@mapNotNull null
            val centavos = valorParaCentavos(match.groupValues[1]) ?: return@mapNotNull null
            CelulaValor(centavos, classificar(rotuloDe(td)))
        }

        if (celulas.isNotEmpty()) {
            // 1) Rótulo explícito de saldo atual.
            celulas.firstOrNull { it.tipo == Tipo.ATUAL }?.let { return it.centavos }
            // 2) Sem rótulo de saldo atual: último valor que não é agendado.
            //    Se todos forem agendados, não dá para saber o saldo atual.
            return celulas.lastOrNull { it.tipo != Tipo.AGENDADO }?.centavos
        }

        // Fallback: valores fora de células (layout diferente do esperado).
        return regexReais.findAll(doc.text())
            .mapNotNull { valorParaCentavos(it.groupValues[1]) }
            .lastOrNull()
    }

    /**
     * Procura o rótulo de uma célula com valor:
     *  - célula imediatamente à esquerda (layout "Rótulo | Valor");
     *  - cabeçalho da mesma coluna (`thead`);
     *  - mesma coluna da linha de cima (layout "rótulos em cima, valores embaixo").
     */
    private fun rotuloDe(td: Element): String {
        val anterior = td.previousElementSibling()?.text()?.trim().orEmpty()
        if (anterior.isNotEmpty() && !regexReais.containsMatchIn(anterior)) return anterior

        val linha = td.parent() ?: return ""
        val coluna = td.elementSiblingIndex()

        val tabela = td.parents().firstOrNull { it.tagName() == "table" }
        val cabecalho = tabela?.selectFirst("thead tr")
            ?.children()?.getOrNull(coluna)?.text()?.trim()
        if (!cabecalho.isNullOrEmpty() && linha.parent()?.tagName() != "thead") return cabecalho

        val acima = linha.previousElementSibling()
            ?.children()?.getOrNull(coluna)?.text()?.trim().orEmpty()
        return if (regexReais.containsMatchIn(acima)) "" else acima
    }

    private fun classificar(rotulo: String): Tipo {
        val r = rotulo.lowercase()
        return when {
            "agendad" in r -> Tipo.AGENDADO
            "saldo" in r || "atual" in r -> Tipo.ATUAL
            else -> Tipo.DESCONHECIDO
        }
    }

    /** Converte "1.234,56" em 123456 (centavos). */
    private fun valorParaCentavos(valor: String): Long? =
        valor.replace(".", "").replace(",", "").toLongOrNull()
}
