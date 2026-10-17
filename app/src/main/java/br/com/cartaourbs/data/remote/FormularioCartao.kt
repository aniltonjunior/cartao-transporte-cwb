package br.com.cartaourbs.data.remote

import br.com.cartaourbs.data.model.CartaoOpcao
import org.jsoup.Jsoup

/**
 * Dados extraídos da página de seleção de cartão
 * (consultarInformacoesCartaoUsuario.html, via GET).
 */
data class FormularioCartao(
    val documentoNacional: String,
    val cartoes: List<CartaoOpcao>,
) {
    /** Cartão preferido para consulta: o primeiro "Ativo", ou o primeiro da lista. */
    val cartaoPreferido: CartaoOpcao?
        get() = cartoes.firstOrNull { it.ativo } ?: cartoes.firstOrNull()

    /** O cartão com [id], ou o [cartaoPreferido] se o id for nulo ou não existir. */
    fun cartaoOuPreferido(id: String?): CartaoOpcao? =
        cartoes.firstOrNull { it.id == id } ?: cartaoPreferido
}

/**
 * Lê a página de seleção e extrai o CPF (campo `documentoNacional`) e a lista
 * de cartões do `<select name="cartao">`.
 *
 * Cada option tem o formato "NUMERO - Tipo - Situação",
 * ex.: "0001234567 - Usuário - Ativo".
 */
object FormularioParser {

    fun parse(html: String): FormularioCartao? {
        val doc = Jsoup.parse(html)

        val documento = doc.selectFirst("input[name=documentoNacional]")
            ?.attr("value")
            ?.trim()
            .orEmpty()

        val options = doc.select("select[name=cartao] option")
        if (options.isEmpty()) return null

        val cartoes = options.mapNotNull { op ->
            val id = op.attr("value").trim()
            if (id.isEmpty()) return@mapNotNull null
            val descricao = op.text().trim()
            val numero = descricao.substringBefore(" - ").trim()
            val situacao = descricao.substringAfterLast(" - ", missingDelimiterValue = "").trim()
            CartaoOpcao(
                id = id,
                numero = numero,
                descricao = descricao,
                // Comparação exata: "Inativo" contém "ativo" e não pode contar.
                ativo = situacao.equals("Ativo", ignoreCase = true),
            )
        }

        if (cartoes.isEmpty()) return null
        return FormularioCartao(documentoNacional = documento, cartoes = cartoes)
    }
}
