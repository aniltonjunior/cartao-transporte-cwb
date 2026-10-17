package br.com.cartaourbs.data

import android.util.Log
import br.com.cartaourbs.BuildConfig
import br.com.cartaourbs.data.model.CartaoOpcao
import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.ErroConsulta
import br.com.cartaourbs.data.model.MesAno
import br.com.cartaourbs.data.model.Movimentacao
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.model.ResultadoExtrato
import br.com.cartaourbs.data.model.SaldoCartao
import br.com.cartaourbs.data.model.TipoErro
import br.com.cartaourbs.data.remote.ExtratoParser
import br.com.cartaourbs.data.remote.FormularioCartao
import br.com.cartaourbs.data.remote.FormularioParser
import br.com.cartaourbs.data.remote.SaldoParser
import br.com.cartaourbs.data.remote.SbeClient
import br.com.cartaourbs.data.remote.SbeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

/**
 * O que a UI precisa do SBE. O ViewModel depende desta interface, não da
 * implementação: nos testes entra um fake, sem rede.
 */
interface ConsultaSbe {
    /**
     * @param cartaoId id interno do cartão a consultar; null (ou um id que não
     *   existe mais) consulta o cartão preferido (o primeiro ativo).
     */
    suspend fun consultarSaldo(credenciais: Credenciais, cartaoId: String? = null): ResultadoConsulta

    suspend fun consultarExtrato(
        credenciais: Credenciais,
        cartaoId: String?,
        periodo: MesAno,
    ): ResultadoExtrato
}

/**
 * Implementação real de [ConsultaSbe]: reproduz o site do SBE.
 *
 * Dois modos:
 *  - REAL (padrão): faz login no SBE e lê as páginas.
 *  - MOCK: devolve dados fake, útil para testar a UI sem rede. Para ativar,
 *    instancie com `usarMock = true`.
 *
 * Fluxo real do SBE (confirmado via captura + logs do app):
 *  0. limpa os cookies (cada consulta começa com sessão nova)
 *  1. POST login                                   -> cria sessão (cookie)
 *  2. GET  central/principal.html                  -> "aquece" a sessão
 *  3. GET  usuario/consultarInformacoesCartaoUsuario.html
 *                                                  -> formulário: CPF + cartões
 *  4. POST mesma URL (documentoNacional + cartao)  -> página com o SALDO
 *  5. (só extrato) POST v2/...ContaCorrente.html   -> movimentações
 *
 * Cada consulta (inclusive trocar de cartão ou de mês no extrato) refaz o
 * fluxo inteiro. É mais lento, mas evita depender de uma sessão que o SBE
 * pode ter encerrado e mantém a correção do bug de sessão entre usuários.
 *
 * Erros: o repositório devolve um [TipoErro]; o texto para o usuário fica em
 * strings.xml. Detalhes técnicos vão para [registrarLog], que no app só
 * escreve no Logcat do build de debug (nunca com credenciais).
 */
class SaldoRepository(
    private val session: SbeSession = SbeClient.create(),
    private val usarMock: Boolean = false,
    private val registrarLog: (mensagem: String, erro: Throwable?) -> Unit = ::registrarNoLogcat,
) : ConsultaSbe {

    private val api get() = session.api

    override suspend fun consultarSaldo(
        credenciais: Credenciais,
        cartaoId: String?,
    ): ResultadoConsulta = withContext(Dispatchers.IO) {
        if (usarMock) {
            saldoMock(cartaoId)
        } else {
            protegido({ ResultadoConsulta.Erro(it) }) { consultarSaldoReal(credenciais, cartaoId) }
        }
    }

    override suspend fun consultarExtrato(
        credenciais: Credenciais,
        cartaoId: String?,
        periodo: MesAno,
    ): ResultadoExtrato = withContext(Dispatchers.IO) {
        if (usarMock) {
            extratoMock()
        } else {
            protegido({ ResultadoExtrato.Erro(it) }) {
                consultarExtratoReal(credenciais, cartaoId, periodo)
            }
        }
    }

    // ---- Fluxo real --------------------------------------------------------

    private suspend fun consultarSaldoReal(
        credenciais: Credenciais,
        cartaoId: String?,
    ): ResultadoConsulta {
        val etapa = when (val e = abrirCartao(credenciais, cartaoId)) {
            is Etapa.Falhou -> return ResultadoConsulta.Erro(e.erro)
            is Etapa.Ok -> e
        }

        val saldo = SaldoParser.parse(etapa.htmlCartao)
            ?: return ResultadoConsulta.Erro(TipoErro.SALDO_NAO_ENCONTRADO)

        return ResultadoConsulta.Sucesso(
            // Completa o número do cartão com o do formulário, se faltar.
            saldo = saldo.copy(numeroCartao = saldo.numeroCartao ?: etapa.cartao.numero),
            cartoes = etapa.formulario.cartoes,
            cartaoConsultado = etapa.cartao,
        )
    }

    private suspend fun consultarExtratoReal(
        credenciais: Credenciais,
        cartaoId: String?,
        periodo: MesAno,
    ): ResultadoExtrato {
        val etapa = when (val e = abrirCartao(credenciais, cartaoId)) {
            is Etapa.Falhou -> return ResultadoExtrato.Erro(e.erro)
            is Etapa.Ok -> e
        }

        // O extrato pede um id interno do usuário que só aparece nas páginas.
        val id = ExtratoParser.extrairIdUsuario(etapa.htmlCartao)
            ?: ExtratoParser.extrairIdUsuario(etapa.htmlFormulario)
        if (id == null) {
            registrarLog(
                "idUsuario não encontrado. Páginas com " +
                    "${etapa.htmlCartao.length} e ${etapa.htmlFormulario.length} caracteres; " +
                    "menções a 'idUsuario': " +
                    "${contarMencoes(etapa.htmlCartao) + contarMencoes(etapa.htmlFormulario)}",
                null,
            )
            return ResultadoExtrato.Erro(TipoErro.USUARIO_DO_EXTRATO_NAO_ENCONTRADO)
        }
        registrarLog("idUsuario encontrado via ${id.origem}", null)

        val resp = api.extrato(id.valor, periodo.dataInicial, periodo.dataFinal)
        if (!resp.isSuccessful) {
            resp.errorBody()?.close()
            return ResultadoExtrato.Erro(erroDoSite("extrato", resp))
        }
        val html = resp.body()?.string().orEmpty()
        val movimentacoes = ExtratoParser.parse(html)
            ?: return ResultadoExtrato.Erro(TipoErro.EXTRATO_NAO_ENCONTRADO)
        return ResultadoExtrato.Sucesso(movimentacoes)
    }

    /** Resultado dos passos 0 a 4, compartilhados por saldo e extrato. */
    private sealed interface Etapa {
        data class Ok(
            val formulario: FormularioCartao,
            val cartao: CartaoOpcao,
            val htmlFormulario: String,
            val htmlCartao: String,
        ) : Etapa

        data class Falhou(val erro: ErroConsulta) : Etapa
    }

    private suspend fun abrirCartao(credenciais: Credenciais, cartaoId: String?): Etapa {
        // 0) Zera qualquer sessão anterior. Sem isso, o cookie de uma
        //    consulta antiga faria o SBE devolver os dados do usuário
        //    anterior, ignorando o novo CPF.
        session.limparSessao()

        // 1) Login. Cria a sessão (cookie), que o OkHttp guarda e reenvia.
        val login = api.login(
            nomeUsuario = credenciais.usuario,
            senha = credenciais.senha,
        )
        login.body()?.close()
        login.errorBody()?.close()
        if (!login.isSuccessful) {
            return Etapa.Falhou(erroDoSite("login", login))
        }

        // Senha errada NÃO gera erro HTTP: o SBE responde 200 mostrando de
        // novo a tela de login. Com login certo, os redirects terminam na
        // área logada (central/principal.html). Por isso olhamos a URL
        // final, depois que o OkHttp seguiu os redirects.
        if (terminouNaTelaDeLogin(login)) {
            return Etapa.Falhou(ErroConsulta(TipoErro.CREDENCIAIS_INVALIDAS))
        }

        // 2) "Aquece" a sessão abrindo a área logada, como o navegador faz.
        //    Falha aqui não impede a consulta. (Não usamos runCatching
        //    porque ele também engoliria o cancelamento da coroutine.)
        try {
            api.principal().body()?.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            registrarLog("Falha ao abrir principal.html (ignorada)", e)
        }

        // 3) Abre o formulário de seleção de cartão (CPF + lista de cartões).
        val formResp = api.consultarInformacoesCartao()
        if (!formResp.isSuccessful) {
            formResp.errorBody()?.close()
            return Etapa.Falhou(erroDoSite("formulário do cartão", formResp))
        }
        val htmlForm = formResp.body()?.string().orEmpty()

        // Sessão caiu entre o login e a consulta (voltou para o login).
        if (!htmlForm.contains("dadosLogado", ignoreCase = true)) {
            return Etapa.Falhou(ErroConsulta(TipoErro.SESSAO_PERDIDA))
        }

        val formulario = FormularioParser.parse(htmlForm)
            ?: return Etapa.Falhou(ErroConsulta(TipoErro.CARTOES_NAO_ENCONTRADOS))
        val cartao = formulario.cartaoOuPreferido(cartaoId)
            ?: return Etapa.Falhou(ErroConsulta(TipoErro.SEM_CARTAO))

        // 4) Envia o formulário escolhendo o cartão -> página com o saldo.
        val cartaoResp = api.selecionarCartao(
            documentoNacional = formulario.documentoNacional,
            cartao = cartao.id,
        )
        if (!cartaoResp.isSuccessful) {
            cartaoResp.errorBody()?.close()
            return Etapa.Falhou(erroDoSite("consulta do cartão", cartaoResp))
        }
        val htmlCartao = cartaoResp.body()?.string().orEmpty()

        return Etapa.Ok(formulario, cartao, htmlForm, htmlCartao)
    }

    /**
     * Converte exceções em erro tipado. O cancelamento da coroutine (ex.:
     * tela fechada no meio da consulta) NÃO é erro: precisa subir para o
     * chamador encerrar corretamente.
     */
    private inline fun <T> protegido(erro: (ErroConsulta) -> T, bloco: () -> T): T =
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Sem internet, timeout, DNS, conexão interrompida...
            registrarLog("Falha de rede", e)
            erro(ErroConsulta(TipoErro.SEM_CONEXAO))
        } catch (e: Exception) {
            registrarLog("Erro inesperado na consulta", e)
            erro(ErroConsulta(TipoErro.INESPERADO))
        }

    /** true quando, depois dos redirects, a resposta ainda é a tela de login. */
    private fun terminouNaTelaDeLogin(resposta: Response<ResponseBody>): Boolean =
        resposta.raw().request.url.encodedPath.contains("/login/", ignoreCase = true)

    private fun erroDoSite(etapa: String, resposta: Response<*>): ErroConsulta {
        registrarLog("HTTP ${resposta.code()} em $etapa", null)
        return ErroConsulta(TipoErro.ERRO_DO_SITE, resposta.code())
    }

    private fun contarMencoes(html: String): Int =
        Regex("idUsuario", RegexOption.IGNORE_CASE).findAll(html).count()

    // ---- Modo MOCK ---------------------------------------------------------

    private suspend fun saldoMock(cartaoId: String?): ResultadoConsulta {
        delay(900) // simula latência de rede
        val cartao = CARTOES_MOCK.firstOrNull { it.id == cartaoId } ?: CARTOES_MOCK.first()
        return ResultadoConsulta.Sucesso(
            saldo = SaldoCartao(
                numeroCartao = cartao.numero,
                saldoCentavos = if (cartao == CARTOES_MOCK.first()) 5100 else 0,
                titular = "USUÁRIO DE TESTE",
                atualizadoEm = "agora",
            ),
            cartoes = CARTOES_MOCK,
            cartaoConsultado = cartao,
        )
    }

    private suspend fun extratoMock(): ResultadoExtrato {
        delay(900)
        fun mov(data: String, valor: Long, saldo: Long) = Movimentacao(
            dataHora = data, numeroSerie = "0000000001", operacao = "Débito",
            linhaCodigo = "000", linhaNome = "LINHA DE TESTE", trecho = "PADRAO",
            veiculo = "00001", empresa = "000 - Empresa de Teste",
            valorCentavos = valor, saldoCartaoCentavos = saldo, saldoAgendadoCentavos = null,
        )
        return ResultadoExtrato.Sucesso(
            listOf(
                mov("03/10/2026 07:30:00", -600, 6300),
                mov("03/10/2026 18:10:00", -600, 5700),
                mov("14/10/2026 19:53:00", -600, 5100),
            )
        )
    }

    private companion object {
        val CARTOES_MOCK = listOf(
            CartaoOpcao("1000001", "0001234567", "0001234567 - Usuário - Ativo", ativo = true),
            CartaoOpcao(
                "1000002", "0007654321",
                "0007654321 - Usuário - Com solicitação de bloqueio", ativo = false,
            ),
        )
    }
}

private const val TAG = "SaldoRepository"

/** Detalhes técnicos só no Logcat do build de debug. Nunca loga credenciais. */
private fun registrarNoLogcat(mensagem: String, erro: Throwable?) {
    if (BuildConfig.DEBUG) Log.w(TAG, mensagem, erro)
}
