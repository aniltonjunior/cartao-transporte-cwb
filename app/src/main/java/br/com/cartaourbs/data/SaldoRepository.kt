package br.com.cartaourbs.data

import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.model.SaldoCartao
import br.com.cartaourbs.data.remote.FormularioParser
import br.com.cartaourbs.data.remote.SaldoParser
import br.com.cartaourbs.data.remote.SbeClient
import br.com.cartaourbs.data.remote.SbeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Fonte única de verdade para consultar o saldo.
 *
 * Dois modos:
 *  - REAL (padrão): faz login no SBE e lê a página com o saldo.
 *  - MOCK: devolve um saldo fake, útil para testar a UI sem rede. Para ativar,
 *    instancie com `usarMock = true`.
 *
 * Fluxo real do SBE (confirmado via captura + logs do app):
 *  1. POST login                                   -> cria sessão (cookie)
 *  2. GET  central/principal.html                  -> "aquece" a sessão
 *  3. GET  usuario/consultarInformacoesCartaoUsuario.html
 *                                                  -> FORMULÁRIO de seleção:
 *                                                     traz o CPF e a lista de
 *                                                     cartões (<select>)
 *  4. POST usuario/consultarInformacoesCartaoUsuario.html
 *          (documentoNacional + cartao escolhido)  -> HTML com o SALDO
 *
 * A UI não sabe desses passos: ela só chama [consultarSaldo].
 */
class SaldoRepository(
    private val session: SbeSession = SbeClient.create(),
    private val usarMock: Boolean = false,
) {

    private val api get() = session.api

    suspend fun consultarSaldo(credenciais: Credenciais): ResultadoConsulta =
        withContext(Dispatchers.IO) {
            if (usarMock) consultarMock() else consultarReal(credenciais)
        }

    // ---- Modo MOCK -------------------------------------------------------

    private suspend fun consultarMock(): ResultadoConsulta {
        delay(900) // simula latência de rede
        return ResultadoConsulta.Sucesso(
            SaldoCartao(
                numeroCartao = "0001234567",
                saldoCentavos = 5100,
                titular = "USUÁRIO DE TESTE",
                atualizadoEm = "agora",
            )
        )
    }

    // ---- Modo REAL -------------------------------------------------------

    private suspend fun consultarReal(credenciais: Credenciais): ResultadoConsulta {
        return try {
            // 0) Zera qualquer sessão anterior. Sem isso, o cookie de uma
            //    consulta antiga faria o SBE devolver os dados do usuário
            //    anterior, ignorando o novo CPF.
            session.limparSessao()

            // 1) Login. Cria a sessão (cookie), que o OkHttp guarda e reenvia.
            val login = api.login(
                nomeUsuario = credenciais.usuario,
                senha = credenciais.senha,
            )
            if (!login.isSuccessful) {
                return ResultadoConsulta.Erro(
                    "Falha no login (HTTP ${login.code()}). Verifique usuário e senha."
                )
            }
            login.body()?.close()

            // 2) "Aquece" a sessão abrindo a área logada, como o navegador faz.
            //    Falha aqui não impede a consulta. (Não usamos runCatching
            //    porque ele também engoliria o cancelamento da coroutine.)
            try {
                api.principal().body()?.close()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ignorado de propósito
            }

            // 3) Abre o formulário de seleção de cartão (CPF + lista de cartões).
            val formResp = api.consultarInformacoesCartao()
            if (!formResp.isSuccessful) {
                return ResultadoConsulta.Erro(
                    "Login OK, mas falha ao abrir a consulta (HTTP ${formResp.code()})."
                )
            }
            val htmlForm = formResp.body()?.string().orEmpty()

            // Sessão não vingou? (voltou para a tela de login)
            if (!htmlForm.contains("dadosLogado", ignoreCase = true)) {
                return ResultadoConsulta.Erro(
                    "Não foi possível autenticar. Confira usuário e senha."
                )
            }

            val formulario = FormularioParser.parse(htmlForm)
                ?: return ResultadoConsulta.Erro(
                    "Login OK, mas não encontrei a lista de cartões na página."
                )
            val cartao = formulario.cartaoPreferido
                ?: return ResultadoConsulta.Erro("Nenhum cartão disponível na conta.")

            // 4) Envia o formulário escolhendo o cartão -> página com o saldo.
            val saldoResp = api.selecionarCartao(
                documentoNacional = formulario.documentoNacional,
                cartao = cartao.id,
            )
            if (!saldoResp.isSuccessful) {
                val corpoErro = runCatching { saldoResp.errorBody()?.string() }
                    .getOrNull()?.take(200)?.replace(Regex("\\s+"), " ").orEmpty()
                return ResultadoConsulta.Erro(
                    "Falha ao consultar o cartão (HTTP ${saldoResp.code()}). $corpoErro"
                )
            }
            val htmlSaldo = saldoResp.body()?.string().orEmpty()

            val saldo = SaldoParser.parse(htmlSaldo)
                ?: return ResultadoConsulta.Erro(
                    "Consulta feita, mas não localizei o saldo na resposta. " +
                        "O layout do SBE pode ter mudado."
                )

            // Completa o número do cartão com o que veio do formulário, caso o
            // parser do saldo não o tenha encontrado.
            val saldoCompleto = saldo.copy(
                numeroCartao = saldo.numeroCartao ?: cartao.numero,
            )
            ResultadoConsulta.Sucesso(saldoCompleto)
        } catch (e: CancellationException) {
            // Cancelamento de coroutine (ex.: tela fechada no meio da consulta)
            // NÃO é erro: precisa subir para o chamador encerrar corretamente.
            throw e
        } catch (e: Exception) {
            ResultadoConsulta.Erro("Erro de conexão: ${e.message ?: "desconhecido"}")
        }
    }
}
