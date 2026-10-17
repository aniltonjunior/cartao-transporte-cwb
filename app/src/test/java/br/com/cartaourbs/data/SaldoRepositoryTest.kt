package br.com.cartaourbs.data

import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.remote.SbeApi
import br.com.cartaourbs.data.remote.SessionCookieJar
import br.com.cartaourbs.data.remote.SbeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SaldoRepositoryTest {

    private val html = "text/html".toMediaType()
    private fun ok(body: String): Response<ResponseBody> =
        Response.success(body.toResponseBody(html))
    private fun err(code: Int, body: String = ""): Response<ResponseBody> =
        Response.error(code, body.toResponseBody(html))

    /** Página de seleção de cartão (CPF + cartões). */
    private fun paginaFormulario(cpf: String, cartaoId: String, numero: String) = """
        <div class="dadosLogado">Fulano ($cpf)</div>
        <form method="post">
          <input name="documentoNacional" value="$cpf"/>
          <select name="cartao">
            <option value="$cartaoId">$numero - Usuário - Ativo</option>
          </select>
        </form>
    """.trimIndent()

    /** Página do saldo (depois do POST). */
    private fun paginaSaldo(cpf: String, reais: String, numero: String) = """
        <div class="dadosLogado">Fulano ($cpf)</div>
        <td> R${'$'} 0,00 </td>
        <td> R${'$'} $reais </td>
        <select name="cartao"><option value="x">$numero - Usuário - Ativo</option></select>
    """.trimIndent()

    /** Fake de SbeApi configurável por usuário. */
    private class FakeApi(
        val formPorUsuario: Map<String, String>,
        val saldoPorCartao: Map<String, String>,
        val loginOk: Boolean = true,
    ) : SbeApi {
        var ultimoUsuarioLogado: String? = null
        var ultimoCartaoSelecionado: String? = null

        override suspend fun login(
            nomeUsuario: String, senha: String,
            resolucaoWidth: Int, resolucaoHeight: Int, x: Int, y: Int,
        ): Response<ResponseBody> {
            ultimoUsuarioLogado = nomeUsuario
            return if (loginOk) Response.success("".toResponseBody())
            else Response.error(401, "".toResponseBody())
        }

        override suspend fun principal(): Response<ResponseBody> =
            Response.success("".toResponseBody())

        override suspend fun consultarInformacoesCartao(): Response<ResponseBody> {
            val usuario = ultimoUsuarioLogado ?: return Response.error(500, "".toResponseBody())
            val form = formPorUsuario[usuario] ?: return Response.success(
                "<html>tela de login</html>".toResponseBody("text/html".toMediaType())
            )
            return Response.success(form.toResponseBody("text/html".toMediaType()))
        }

        override suspend fun selecionarCartao(
            documentoNacional: String, cartao: String,
        ): Response<ResponseBody> {
            ultimoCartaoSelecionado = cartao
            val saldo = saldoPorCartao[cartao] ?: return Response.error(500, "".toResponseBody())
            return Response.success(saldo.toResponseBody("text/html".toMediaType()))
        }
    }

    private fun repo(api: SbeApi) =
        SaldoRepository(session = SbeSession(api, SessionCookieJar()), usarMock = false)

    @Test
    fun `consulta com sucesso retorna saldo do cartao ativo`() = runTest {
        val api = FakeApi(
            formPorUsuario = mapOf("11122233344" to paginaFormulario("11122233344", "A1", "0001")),
            saldoPorCartao = mapOf("A1" to paginaSaldo("11122233344", "51,00", "0001")),
        )
        val resultado = repo(api).consultarSaldo(Credenciais("11122233344", "x"))
        assertTrue(resultado is ResultadoConsulta.Sucesso)
        val saldo = (resultado as ResultadoConsulta.Sucesso).saldo
        assertEquals(5100L, saldo.saldoCentavos)
        assertEquals("A1", api.ultimoCartaoSelecionado)
    }

    @Test
    fun `usuarios diferentes recebem saldos diferentes`() = runTest {
        // Regressão do bug: trocar de CPF deve trazer o saldo correto de cada um.
        val api = FakeApi(
            formPorUsuario = mapOf(
                "11111111111" to paginaFormulario("11111111111", "C1", "0001"),
                "22222222222" to paginaFormulario("22222222222", "C2", "0002"),
            ),
            saldoPorCartao = mapOf(
                "C1" to paginaSaldo("11111111111", "51,00", "0001"),
                "C2" to paginaSaldo("22222222222", "12,34", "0002"),
            ),
        )
        val r = repo(api)

        val a = r.consultarSaldo(Credenciais("11111111111", "x"))
        val b = r.consultarSaldo(Credenciais("22222222222", "x"))

        assertEquals(5100L, (a as ResultadoConsulta.Sucesso).saldo.saldoCentavos)
        assertEquals(1234L, (b as ResultadoConsulta.Sucesso).saldo.saldoCentavos)
    }

    @Test
    fun `login invalido retorna erro`() = runTest {
        val api = FakeApi(emptyMap(), emptyMap(), loginOk = false)
        val resultado = repo(api).consultarSaldo(Credenciais("00000000000", "x"))
        assertTrue(resultado is ResultadoConsulta.Erro)
        assertTrue((resultado as ResultadoConsulta.Erro).mensagem.contains("login", ignoreCase = true))
    }

    @Test
    fun `sessao sem dadosLogado retorna erro de autenticacao`() = runTest {
        // form ausente para o usuário -> fake devolve "tela de login"
        val api = FakeApi(
            formPorUsuario = emptyMap(),
            saldoPorCartao = emptyMap(),
        )
        val resultado = repo(api).consultarSaldo(Credenciais("99999999999", "x"))
        assertTrue(resultado is ResultadoConsulta.Erro)
        assertTrue((resultado as ResultadoConsulta.Erro).mensagem.contains("autenticar", ignoreCase = true))
    }

    @Test
    fun `cancelamento interrompe o fluxo e nao segue fazendo requisicoes`() = runTest {
        // Cenário: a tela é fechada enquanto o "aquecimento" (principal) roda.
        // Se o cancelamento fosse engolido, o repositório continuaria e
        // abriria o formulário do cartão mesmo assim.
        val cpf = "12345678909"
        val base = FakeApi(
            formPorUsuario = mapOf(cpf to paginaFormulario(cpf, "A1", "0001")),
            saldoPorCartao = mapOf("A1" to paginaSaldo(cpf, "51,00", "0001")),
        )
        val principalComecou = CompletableDeferred<Unit>()
        var formularioChamado = false
        val api = object : SbeApi by base {
            override suspend fun principal(): Response<ResponseBody> {
                principalComecou.complete(Unit)
                awaitCancellation()
            }

            override suspend fun consultarInformacoesCartao(): Response<ResponseBody> {
                formularioChamado = true
                return base.consultarInformacoesCartao()
            }
        }

        var resultado: ResultadoConsulta? = null
        var cancelou = false
        val job = launch {
            try {
                resultado = repo(api).consultarSaldo(Credenciais(cpf, "x"))
            } catch (e: CancellationException) {
                cancelou = true
                throw e
            }
        }

        principalComecou.await()
        job.cancelAndJoin()

        assertTrue("o cancelamento deveria chegar ao chamador", cancelou)
        assertNull("não deveria produzir resultado (nem Erro)", resultado)
        assertTrue("não deveria seguir para o formulário", !formularioChamado)
    }

    @Test
    fun `modo mock devolve saldo fake sem rede`() = runTest {
        val api = FakeApi(emptyMap(), emptyMap())
        val repo = SaldoRepository(session = SbeSession(api, SessionCookieJar()), usarMock = true)
        val resultado = repo.consultarSaldo(Credenciais("1", "2"))
        assertTrue(resultado is ResultadoConsulta.Sucesso)
        assertEquals(5100L, (resultado as ResultadoConsulta.Sucesso).saldo.saldoCentavos)
    }
}
