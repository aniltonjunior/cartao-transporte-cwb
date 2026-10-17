package br.com.cartaourbs.data.remote

import br.com.cartaourbs.data.model.Movimentacao
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Lê o HTML do extrato
 * (`v2/usuario/consultarInformacoesCartaoUsuarioContaCorrente.html`).
 *
 * Estrutura observada (fragmento de HTML, sem <html>/<body>):
 * ```
 * <table class="reportResultNew">
 *   <thead><tr><td>Data</td><td>Número de Série</td><td>Operação da Conta</td>
 *     <td>Linha</td><td>Trecho</td><td>Veículo</td><td>Valor(R$)</td>
 *     <td>Saldo Cartão</td><td>Saldo Agendado</td></tr></thead>
 *   <tbody><tr>... 9 células por movimentação ...</tr></tbody>
 *   <tfoot>... OUTRA table.reportResultNew com os totais ...</tfoot>
 * </table>
 * ```
 * Detalhes:
 *  - O nome da linha está no `title` da célula (texto "TCG", title
 *    "PRAÇA CARLOS GOMES"); a empresa, no `title` da célula do veículo.
 *  - Valores sem "R$": "- 6,00" (débito), "63,00", e "--" quando não há.
 *  - A tabela de totais do rodapé também é `reportResultNew`: só lemos as
 *    linhas do `tbody` da tabela principal.
 */
object ExtratoParser {

    private const val COLUNAS = 9
    private val regexValor = Regex("""^([+-]?)\s*((?:\d{1,3}(?:\.\d{3})*|\d+),\d{2})$""")

    /**
     * @return as movimentações (lista vazia se o período não tem nenhuma),
     *   ou null se a resposta não tem cara de extrato (layout mudou).
     */
    fun parse(html: String): List<Movimentacao>? {
        val doc = Jsoup.parse(html)

        val tabela = doc.select("table.reportResultNew").firstOrNull { t ->
            t.selectFirst("> thead")?.text()?.contains("Data", ignoreCase = true) == true
        }

        if (tabela == null) {
            // Período sem movimentação pode vir só com um aviso.
            val texto = doc.text()
            return if (texto.contains("nenhum", ignoreCase = true) ||
                texto.contains("não foram encontrad", ignoreCase = true)
            ) emptyList() else null
        }

        return tabela.select("> tbody > tr").mapNotNull { linha ->
            val celulas = linha.select("> td")
            if (celulas.size < COLUNAS) return@mapNotNull null
            val valor = valorEmCentavos(celulas[6].text()) ?: return@mapNotNull null
            Movimentacao(
                dataHora = celulas[0].text().trim(),
                numeroSerie = celulas[1].text().trim(),
                operacao = celulas[2].text().trim(),
                linhaCodigo = celulas[3].text().trim(),
                linhaNome = celulas[3].tituloOuNull(),
                trecho = celulas[4].text().trim(),
                veiculo = celulas[5].text().trim(),
                empresa = celulas[5].tituloOuNull(),
                valorCentavos = valor,
                saldoCartaoCentavos = valorEmCentavos(celulas[7].text()),
                saldoAgendadoCentavos = valorEmCentavos(celulas[8].text()),
            )
        }
    }

    /** "- 6,00" -> -600; "1.063,00" -> 106300; "--" -> null. */
    internal fun valorEmCentavos(texto: String): Long? {
        val m = regexValor.find(texto.replace('\u00A0', ' ').trim()) ?: return null
        val absoluto = m.groupValues[2].replace(".", "").replace(",", "").toLongOrNull()
            ?: return null
        return if (m.groupValues[1] == "-") -absoluto else absoluto
    }

    private fun Element.tituloOuNull(): String? =
        attr("title").trim().takeIf { it.isNotEmpty() }

    // ---- idUsuario -------------------------------------------------------

    /**
     * Onde o identificador foi encontrado (para diagnóstico, sem expor o valor).
     */
    enum class Origem { CAMPO_DE_FORMULARIO, ATRIBUTO, SCRIPT }

    data class IdUsuario(val valor: String, val origem: Origem)

    private val regexIdNoTexto = Regex(
        """idUsuario['"]?\s*[:=,]\s*['"]?(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Procura, na página do cartão, o número que o site manda como
     * `filtro.idUsuario` ao pedir o extrato. Não é o id do cartão.
     *
     * ATENÇÃO: ainda não confirmamos em qual parte da página ele fica. As
     * buscas abaixo cobrem os formatos mais comuns, em ordem:
     *  1. campo `<input>`/`<select>` cujo name ou id contém "idUsuario";
     *  2. qualquer atributo `data-*`/value ligado a "idUsuario";
     *  3. texto de script, ex.: `idUsuario: 123`, `filtro.idUsuario=123`,
     *     `'idUsuario', '123'`.
     *
     * @return null se não achar. Nesse caso o app mostra um erro em vez de
     *   adivinhar um número.
     */
    fun extrairIdUsuario(html: String): IdUsuario? {
        val doc = Jsoup.parse(html)

        doc.select("input, select").forEach { campo ->
            val nome = campo.attr("name") + " " + campo.attr("id")
            if (nome.contains("idUsuario", ignoreCase = true)) {
                val valor = campo.`val`().trim()
                if (valor.isNotEmpty() && valor.all(Char::isDigit)) {
                    return IdUsuario(valor, Origem.CAMPO_DE_FORMULARIO)
                }
            }
        }

        doc.getAllElements().forEach { el ->
            el.attributes().forEach { atr ->
                if (atr.key.contains("idusuario", ignoreCase = true)) {
                    val valor = atr.value.trim()
                    if (valor.isNotEmpty() && valor.all(Char::isDigit)) {
                        return IdUsuario(valor, Origem.ATRIBUTO)
                    }
                }
            }
        }

        val scripts = doc.select("script").joinToString("\n") { it.data() } +
            "\n" + doc.select("[onclick], [href]").joinToString("\n") {
                it.attr("onclick") + " " + it.attr("href")
            }
        regexIdNoTexto.find(scripts)?.let {
            return IdUsuario(it.groupValues[1], Origem.SCRIPT)
        }
        return null
    }
}
