package br.com.cartaourbs.data.remote

import org.jsoup.Jsoup

/**
 * Um cartão listado no formulário de seleção do SBE.
 *
 * - [id]: value interno do <option> (ex.: "1000001"), usado no POST.
 * - [numero]: número impresso do cartão (ex.: "0001234567").
 * - [descricao]: texto completo do option (ex.: "0001234567 - Usuário - Ativo").
 * - [ativo]: true quando a descrição indica "Ativo".
 */
data class CartaoOpcao(
    val id: String,
    val numero: String,
    val descricao: String,
    val ativo: Boolean,
)

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
}

/**
 * Lê a página de seleção e extrai o CPF (campo `documentoNacional`) e a lista
 * de cartões do `<select name="cartao">`.
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
            CartaoOpcao(
                id = id,
                numero = numero,
                descricao = descricao,
                ativo = descricao.contains("Ativo", ignoreCase = true),
            )
        }

        if (cartoes.isEmpty()) return null
        return FormularioCartao(documentoNacional = documento, cartoes = cartoes)
    }
}
