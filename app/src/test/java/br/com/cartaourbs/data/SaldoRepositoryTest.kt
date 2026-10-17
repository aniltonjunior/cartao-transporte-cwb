package br.com.cartaourbs.data

import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.MesAno
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.model.ResultadoExtrato
import br.com.cartaourbs.data.model.TipoErro
import br.com.cartaourbs.data.remote.SbeApi
import br.com.cartaourbs.data.remote.SbeSession
import br.com.cartaourbs.data.remote.SessionCookieJar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** Dados 100% fictícios. */
class SaldoRepositoryTest {

    private companion object {
        const val URL_LOGIN = "https://sbe.curitiba.pr.gov.br/sbe-web/login/login.html"
        const val URL_AREA_LOGADA = "https://sbe.curitiba.pr.gov.br/sbe-web/central/principal.html"
        val HTML = "text/html".toMediaType()

        /**
         * Resposta Retrofit cuja URL FINAL (depois dos redirects) é [url],
         * como o OkHttp entrega de verdade.
         */
        fun respostaEm(url: String, codigo: Int = 200, corpo: String = ""): Response<ResponseBody> {
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(codigo)
                .message(if (codigo in 200..299) "OK" else "Erro")
                .build()
            val body = corpo.toResponseBody(HTML)
            return if (codigo in 200..299) Response.success(body, raw) else Response.error(body, raw)
        }

        /** Página de seleção de cartão (CPF + cartões "id" to "situação"). */
        fun paginaFormulario(cpf: String, vararg cartoes: Pair<String, String>) = """
            <div class="dadosLogado">Fulano de Tal ($cpf)</div>
            <form method="post">
              <input name="documentoNacional" value="$cpf"/>
              <select name="cartao">
                ${cartoes.joinToString("\n") { (id, sit) -> "<option value=\"$id\">N$id - Usuário - $sit</option>" }}
              </select>
            </form>
        """.trimIndent()

        /** Página do saldo (depois do POST), com a estrutura da página real. */
        fun paginaSaldo(cpf: String, reais: String, idUsuario: String? = null) = """
            <div class="dadosLogado">Fulano de Tal ($cpf)</div>
            ${idUsuario?.let { "<input type=\"hidden\" name=\"filtro.idUsuario\" value=\"$it\"/>" }.orEmpty()}
            <table><tr>
              <td> R${'$'} 0,00 </td>
              <td> R${'$'} $reais </td>
            </tr></table>
        """.trimIndent()

        val EXTRATO_2_LINHAS = """
            <table class="reportResultNew">
              <thead><tr><td>Data</td><td>Número de Série</td><td>Operação da Conta</td><td>Linha</td>
                <td>Trecho</td><td>Veículo</td><td>Valor(R${'$'})</td><td>Saldo Cartão</td><td>Saldo Agendado</td></tr></thead>
              <tbody>
                <tr><td>01/10/2026 08:00:00</td><td>1</td><td>Débito</td><td title="LINHA A">001</td>
                  <td>PADRAO</td><td title="Empresa X">00001</td><td>- 6,00</td><td>10,00</td><td>--</td></tr>
                <tr><td>02/10/2026 08:00:00</td><td>1</td><td>Débito</td><td title="LINHA B">002</td>
                  <td>PADRAO</td><td title="Empresa X">00002</td><td>- 6,00</td><td>4,00</td><td>--</td></tr>
              </tbody>
            </table>
        """.trimIndent()
    }

    /**
     * Fake de SbeApi configurável por usuário.
     *
     * Simula o SBE: com usuário conhecido e [senhaCorreta], o login termina na
     * área logada; caso contrário, volta para a tela de login com HTTP 200.
     */
    private class FakeApi(
        val formPorUsuario: Map<String, String>,
        val paginaPorCartao: Map<String, String>,
        val senhaCorreta: String = "x",
        val codigoLogin: Int = 200,
        val erroLogin: Exception? = null,
        val extratoPorId: Map<String, Response<ResponseBody>> = emptyMap(),
    ) : SbeApi {
        var ultimoUsuarioLogado: String? = null
        var ultimoCartaoSelecionado: String? = null
        var ultimoPedidoExtrato: Triple<String, String, String>? = null

        override suspend fun login(
            nomeUsuario: String, senha: String,
            resolucaoWidth: Int, resolucaoHeight: Int, x: Int, y: Int,
        ): Response<ResponseBody> {
            erroLogin?.let { throw it }
            if (codigoLogin != 200) return respostaEm(URL_LOGIN, codigoLogin)
            val valido = nomeUsuario in formPorUsuario && senha == senhaCorreta
            if (!valido) return respostaEm(URL_LOGIN, corpo = "<form>login</form>")
            ultimoUsuarioLogado = nomeUsuario
            return respostaEm(URL_AREA_LOGADA)
        }

        override suspend fun principal(): Response<ResponseBody> = respostaEm(URL_AREA_LOGADA)

        override suspend fun consultarInformacoesCartao(): Response<ResponseBody> {
            val usuario = ultimoUsuarioLogado ?: return respostaEm(URL_LOGIN, corpo = "tela de login")
            return respostaEm(URL_AREA_LOGADA, corpo = formPorUsuario.getValue(usuario))
        }

        override suspend fun selecionarCartao(
            documentoNacional: String, cartao: String,
        ): Response<ResponseBody> {
            ultimoCartaoSelecionado = cartao
            val pagina = paginaPorCartao[cartao] ?: return respostaEm(URL_AREA_LOGADA, 500)
            return respostaEm(URL_AREA_LOGADA, corpo = pagina)
        }

        override suspend fun extrato(
            idUsuario: String, dataInicial: String, dataFinal: String,
        ): Response<ResponseBody> {
            ultimoPedidoExtrato = Triple(idUsuario, dataInicial, dataFinal)
            return extratoPorId[idUsuario] ?: respostaEm(URL_AREA_LOGADA, 500)
        }
    }

    /** Erros registrados (o padrão do app escreve no Logcat, que não existe na JVM). */
    private val registrados = mutableListOf<Pair<String, Throwable?>>()

    private fun repo(api: SbeApi, usarMock: Boolean = false) = SaldoRepository(
        session = SbeSession(api, SessionCookieJar()),
        usarMock = usarMock,
        registrarLog = { msg, e -> registrados += msg to e },
    )

    private val cpf = "12345678909"
    private val credenciais = Credenciais(cpf, "x")
    private val outubro = MesAno(2026, 10)

    /** Conta com 2 cartões: A1 ativo (R$ 51,00) e B2 bloqueado (R$ 3,00). */
    private fun apiPadrao(extratoPorId: Map<String, Response<ResponseBody>> = emptyMap()) = FakeApi(
        formPorUsuario = mapOf(cpf to paginaFormulario(cpf, "B2" to "Bloqueado", "A1" to "Ativo")),
        paginaPorCartao = mapOf(
            "A1" to paginaSaldo(cpf, "51,00", idUsuario = "777"),
            "B2" to paginaSaldo(cpf, "3,00", idUsuario = "777"),
        ),
        extratoPorId = extratoPorId,
    )

    private fun ResultadoConsulta.tipoErro() = (this as ResultadoConsulta.Erro).erro.tipo

    // ---- Saldo -------------------------------------------------------------

    @Test
    fun `sem cartao escolhido consulta o ativo e devolve a lista de cartoes`() = runTest {
        val api = apiPadrao()
        val r = repo(api).consultarSaldo(credenciais) as ResultadoConsulta.Sucesso

        assertEquals(5100L, r.saldo.saldoCentavos)
        assertEquals("A1", api.ultimoCartaoSelecionado)
        assertEquals("A1", r.cartaoConsultado?.id)
        assertEquals(listOf("B2", "A1"), r.cartoes.map { it.id })
    }

    @Test
    fun `cartao escolhido e consultado mesmo nao sendo o ativo`() = runTest {
        val api = apiPadrao()
        val r = repo(api).consultarSaldo(credenciais, cartaoId = "B2") as ResultadoConsulta.Sucesso

        assertEquals("B2", api.ultimoCartaoSelecionado)
        assertEquals(300L, r.saldo.saldoCentavos)
        assertEquals("B2", r.cartaoConsultado?.id)
    }

    @Test
    fun `cartao que nao existe mais cai para o preferido`() = runTest {
        val api = apiPadrao()
        val r = repo(api).consultarSaldo(credenciais, cartaoId = "SUMIU") as ResultadoConsulta.Sucesso
        assertEquals("A1", r.cartaoConsultado?.id)
    }

    @Test
    fun `usuarios diferentes recebem saldos diferentes`() = runTest {
        // Regressão do bug: trocar de CPF deve trazer o saldo correto de cada um.
        val api = FakeApi(
            formPorUsuario = mapOf(
                "11111111111" to paginaFormulario("11111111111", "C1" to "Ativo"),
                "22222222222" to paginaFormulario("22222222222", "C2" to "Ativo"),
            ),
            paginaPorCartao = mapOf(
                "C1" to paginaSaldo("11111111111", "51,00"),
                "C2" to paginaSaldo("22222222222", "12,34"),
            ),
        )
        val r = repo(api)

        val a = r.consultarSaldo(Credenciais("11111111111", "x"))
        val b = r.consultarSaldo(Credenciais("22222222222", "x"))

        assertEquals(5100L, (a as ResultadoConsulta.Sucesso).saldo.saldoCentavos)
        assertEquals(1234L, (b as ResultadoConsulta.Sucesso).saldo.saldoCentavos)
    }

    @Test
    fun `senha errada - SBE volta para a tela de login com HTTP 200`() = runTest {
        val api = apiPadrao()
        val r = repo(api).consultarSaldo(Credenciais(cpf, "senha-errada"))

        assertEquals(TipoErro.CREDENCIAIS_INVALIDAS, r.tipoErro())
        assertNull("não deveria tentar consultar cartão", api.ultimoCartaoSelecionado)
    }

    @Test
    fun `sessao perdida apos o login`() = runTest {
        // Login "passa", mas o formulário volta sem a área logada.
        val base = apiPadrao()
        val api = object : SbeApi by base {
            override suspend fun consultarInformacoesCartao() =
                respostaEm(URL_LOGIN, corpo = "<html>tela de login</html>")
        }
        assertEquals(TipoErro.SESSAO_PERDIDA, repo(api).consultarSaldo(credenciais).tipoErro())
    }

    @Test
    fun `erro HTTP do site traz o codigo e registra o detalhe`() = runTest {
        val api = FakeApi(emptyMap(), emptyMap(), codigoLogin = 503)
        val erro = (repo(api).consultarSaldo(credenciais) as ResultadoConsulta.Erro).erro

        assertEquals(TipoErro.ERRO_DO_SITE, erro.tipo)
        assertEquals(503, erro.codigoHttp)
        assertTrue(registrados.any { it.first.contains("503") })
    }

    @Test
    fun `falha de rede vira SEM_CONEXAO e o detalhe vai so para o log`() = runTest {
        val falha = IOException("unexpected end of stream")
        val api = FakeApi(emptyMap(), emptyMap(), erroLogin = falha)

        assertEquals(TipoErro.SEM_CONEXAO, repo(api).consultarSaldo(credenciais).tipoErro())
        assertTrue(registrados.any { it.second === falha })
    }

    @Test
    fun `erro inesperado vira INESPERADO`() = runTest {
        val api = FakeApi(emptyMap(), emptyMap(), erroLogin = IllegalStateException("detalhe interno"))
        assertEquals(TipoErro.INESPERADO, repo(api).consultarSaldo(credenciais).tipoErro())
    }

    @Test
    fun `cancelamento interrompe o fluxo e nao segue fazendo requisicoes`() = runTest {
        // Cenário: a tela é fechada enquanto o "aquecimento" (principal) roda.
        // Se o cancelamento fosse engolido, o repositório continuaria e
        // abriria o formulário do cartão mesmo assim.
        val base = apiPadrao()
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
                resultado = repo(api).consultarSaldo(credenciais)
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

    // ---- Extrato -----------------------------------------------------------

    @Test
    fun `extrato usa o idUsuario da pagina e o periodo pedido`() = runTest {
        val api = apiPadrao(extratoPorId = mapOf("777" to respostaEm(URL_AREA_LOGADA, corpo = EXTRATO_2_LINHAS)))
        val r = repo(api).consultarExtrato(credenciais, cartaoId = "B2", periodo = outubro)

        assertEquals(Triple("777", "01/10/2026", "31/10/2026"), api.ultimoPedidoExtrato)
        assertEquals("B2", api.ultimoCartaoSelecionado)
        val movs = (r as ResultadoExtrato.Sucesso).movimentacoes
        assertEquals(2, movs.size)
        assertEquals(-600L, movs[0].valorCentavos)
    }

    @Test
    fun `sem idUsuario na pagina o extrato da erro em vez de adivinhar`() = runTest {
        val api = FakeApi(
            formPorUsuario = mapOf(cpf to paginaFormulario(cpf, "A1" to "Ativo")),
            paginaPorCartao = mapOf("A1" to paginaSaldo(cpf, "51,00", idUsuario = null)),
        )
        val r = repo(api).consultarExtrato(credenciais, null, outubro)

        assertEquals(TipoErro.USUARIO_DO_EXTRATO_NAO_ENCONTRADO, (r as ResultadoExtrato.Erro).erro.tipo)
        assertNull("não deveria chamar o extrato", api.ultimoPedidoExtrato)
    }

    @Test
    fun `extrato com erro HTTP`() = runTest {
        val api = apiPadrao() // sem resposta configurada -> HTTP 500
        val r = repo(api).consultarExtrato(credenciais, null, outubro) as ResultadoExtrato.Erro
        assertEquals(TipoErro.ERRO_DO_SITE, r.erro.tipo)
        assertEquals(500, r.erro.codigoHttp)
    }

    @Test
    fun `extrato com senha errada nao chega a pedir o extrato`() = runTest {
        val api = apiPadrao()
        val r = repo(api).consultarExtrato(Credenciais(cpf, "errada"), null, outubro)
        assertEquals(TipoErro.CREDENCIAIS_INVALIDAS, (r as ResultadoExtrato.Erro).erro.tipo)
        assertNull(api.ultimoPedidoExtrato)
    }

    @Test
    fun `resposta do extrato sem tabela vira EXTRATO_NAO_ENCONTRADO`() = runTest {
        val api = apiPadrao(extratoPorId = mapOf("777" to respostaEm(URL_AREA_LOGADA, corpo = "<p>oi</p>")))
        val r = repo(api).consultarExtrato(credenciais, null, outubro)
        assertEquals(TipoErro.EXTRATO_NAO_ENCONTRADO, (r as ResultadoExtrato.Erro).erro.tipo)
    }

    // ---- Mock --------------------------------------------------------------

    @Test
    fun `modo mock devolve dados fake sem rede`() = runTest {
        val r = repo(FakeApi(emptyMap(), emptyMap()), usarMock = true)
        val saldo = r.consultarSaldo(Credenciais("1", "2")) as ResultadoConsulta.Sucesso
        assertEquals(5100L, saldo.saldo.saldoCentavos)
        assertTrue(saldo.cartoes.size > 1)
        assertTrue(r.consultarExtrato(Credenciais("1", "2"), null, outubro) is ResultadoExtrato.Sucesso)
    }
}
