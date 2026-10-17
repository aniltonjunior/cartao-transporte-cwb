package br.com.cartaourbs.data.remote

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieJarTest {

    private val sbe = "https://sbe.curitiba.pr.gov.br/sbe-web/central/principal.html".toHttpUrl()
    private val outroSite = "https://exemplo.com.br/".toHttpUrl()

    /** Relógio controlado pelo teste. */
    private var agora = 1_000_000L
    private val jar = SessionCookieJar { agora }

    private fun cookie(
        nome: String,
        valor: String,
        caminho: String = "/",
        expiraEm: Long? = null,
        seguro: Boolean = false,
    ): Cookie = Cookie.Builder()
        .name(nome)
        .value(valor)
        .hostOnlyDomain("sbe.curitiba.pr.gov.br")
        .path(caminho)
        .apply { if (expiraEm != null) expiresAt(expiraEm) }
        .apply { if (seguro) secure() }
        .build()

    private fun nomes(url: okhttp3.HttpUrl = sbe) =
        jar.loadForRequest(url).associate { it.name to it.value }

    @Test
    fun `mescla cookies de respostas diferentes`() {
        // Regressão: a versão antiga trocava a lista toda a cada resposta.
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "abc")))
        jar.saveFromResponse(sbe, listOf(cookie("ROUTE", "n1")))

        assertEquals(mapOf("JSESSIONID" to "abc", "ROUTE" to "n1"), nomes())
    }

    @Test
    fun `cookie com mesmo nome dominio e caminho substitui o anterior`() {
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "velho")))
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "novo")))

        assertEquals(mapOf("JSESSIONID" to "novo"), nomes())
    }

    @Test
    fun `mesmo nome em caminhos diferentes convivem`() {
        jar.saveFromResponse(sbe, listOf(cookie("ID", "raiz", caminho = "/")))
        jar.saveFromResponse(sbe, listOf(cookie("ID", "web", caminho = "/sbe-web")))

        val enviados = jar.loadForRequest(sbe).map { it.value }.sorted()
        assertEquals(listOf("raiz", "web"), enviados)
    }

    @Test
    fun `cookie expirado na resposta apaga o existente`() {
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "abc")))
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "", expiraEm = agora - 1)))

        assertTrue(nomes().isEmpty())
    }

    @Test
    fun `cookie que expira com o tempo deixa de ser enviado`() {
        jar.saveFromResponse(sbe, listOf(cookie("TEMP", "x", expiraEm = agora + 60_000)))
        assertEquals(mapOf("TEMP" to "x"), nomes())

        agora += 60_001
        assertTrue(nomes().isEmpty())
    }

    @Test
    fun `nao envia cookie para outro dominio`() {
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "abc")))
        assertTrue(nomes(outroSite).isEmpty())
    }

    @Test
    fun `respeita o caminho do cookie`() {
        jar.saveFromResponse(sbe, listOf(cookie("ADMIN", "1", caminho = "/admin")))
        assertTrue(nomes().isEmpty())
    }

    @Test
    fun `cookie seguro nao vai por http`() {
        jar.saveFromResponse(sbe, listOf(cookie("S", "1", seguro = true)))
        val http = "http://sbe.curitiba.pr.gov.br/sbe-web/".toHttpUrl()

        assertTrue(nomes(http).isEmpty())
        assertEquals(mapOf("S" to "1"), nomes())
    }

    @Test
    fun `limpar descarta tudo`() {
        jar.saveFromResponse(sbe, listOf(cookie("JSESSIONID", "abc"), cookie("ROUTE", "n1")))
        jar.limpar()

        assertTrue(nomes().isEmpty())
    }
}
