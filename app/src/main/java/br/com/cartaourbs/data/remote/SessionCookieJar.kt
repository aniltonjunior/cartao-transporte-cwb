package br.com.cartaourbs.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar em memória que guarda os cookies de sessão do SBE (JSESSIONID e
 * o que mais o servidor mandar) durante UMA consulta.
 *
 * Regras, seguindo o comportamento de um navegador:
 *  - Cada cookie é identificado por (nome, domínio, caminho). Um cookie novo
 *    com a mesma identificação substitui o antigo; os demais são mantidos.
 *    A versão anterior trocava a lista inteira do host a cada resposta, então
 *    um redirect que mandasse só um cookie apagava os outros.
 *  - Cookie já expirado (ex.: `Max-Age=0`) remove o existente: é assim que o
 *    servidor "apaga" um cookie.
 *  - Na hora de enviar, só vão os cookies válidos para a URL (domínio,
 *    caminho e https), conferidos por [Cookie.matches].
 *  - Thread-safe: o OkHttp pode chamar de threads diferentes.
 *
 * [limpar] descarta tudo; o repositório chama antes de cada login.
 *
 * @param agora relógio em milissegundos (injetável para testes).
 */
class SessionCookieJar(
    private val agora: () -> Long = System::currentTimeMillis,
) : CookieJar {

    private data class Chave(val nome: String, val dominio: String, val caminho: String)

    private val armazenados = LinkedHashMap<Chave, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val instante = agora()
        for (cookie in cookies) {
            val chave = Chave(cookie.name, cookie.domain, cookie.path)
            if (cookie.expiresAt <= instante) {
                armazenados.remove(chave)
            } else {
                armazenados[chave] = cookie
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val instante = agora()
        armazenados.values.removeAll { it.expiresAt <= instante }
        return armazenados.values.filter { it.matches(url) }
    }

    @Synchronized
    fun limpar() = armazenados.clear()
}
