package br.com.cartaourbs.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Interface Retrofit que descreve as chamadas HTTP ao Sistema de Bilhetagem
 * Eletrônica (SBE) da URBS.
 *
 * IMPORTANTE: a URBS não publica uma API oficial. Estas chamadas reproduzem
 * exatamente o que o site web faz. Por isso retornamos [ResponseBody] bruto
 * (HTML) e extraímos os dados com Jsoup.
 *
 * Fluxo real capturado do site:
 *   1. POST /sbe-web/login/login.html           -> 302, cria sessão (cookie)
 *   2. GET  /sbe-web/central/principal.html     -> área logada
 *   3. GET  /sbe-web/usuario/consultarInformacoesCartaoUsuario.html
 *                                                -> formulário: CPF + cartões
 *   4. POST (mesma URL) documentoNacional + cartao
 *                                                -> página com o saldo
 *   5. POST /sbe-web/v2/usuario/consultarInformacoesCartaoUsuarioContaCorrente.html
 *                                                -> extrato do período
 *
 * A sessão é mantida por cookie (JSESSIONID). O OkHttp guarda o cookie do
 * login automaticamente (ver SbeClient) e o reenvia nas chamadas seguintes.
 *
 * TODO(logout): o app não encerra a sessão no servidor, porque ainda não
 * conhecemos o endpoint de saída do SBE (o botão "Sair" da página só fecha a
 * janela). Cada consulta deixa uma sessão aberta, que expira sozinha. Para
 * implementar: capture no navegador (F12 > Network) a requisição feita ao
 * sair do SBE, adicione aqui e chame no fim de SaldoRepository.abrirCartao.
 */
interface SbeApi {

    /**
     * Efetua o login no SBE. Reproduz o formulário enviado pelo site.
     *
     * O site envia a resolução de tela e as coordenadas (x, y) do clique no
     * botão de login. Mandamos valores fixos plausíveis — o servidor aceita.
     *
     * Responde 302 (redireciona para a área logada). O OkHttp segue o
     * redirect e guarda o cookie de sessão.
     */
    @FormUrlEncoded
    @POST("sbe-web/login/login.html")
    suspend fun login(
        @Field("nomeUsuario") nomeUsuario: String,
        @Field("senha") senha: String,
        @Field("resolucaoWidth") resolucaoWidth: Int = 1920,
        @Field("resolucaoHeight") resolucaoHeight: Int = 1080,
        @Field("x") x: Int = 20,
        @Field("y") y: Int = 10,
    ): Response<ResponseBody>

    /**
     * Página inicial da área logada. O navegador a acessa logo após o login
     * (passando por alguns redirects). Chamá-la "aquece" a sessão antes de
     * consultar o cartão, reproduzindo o comportamento do site.
     */
    @GET("sbe-web/central/principal.html")
    suspend fun principal(): Response<ResponseBody>

    /**
     * Abre o formulário de seleção de cartão: traz o CPF (campo
     * `documentoNacional`) e a lista de cartões (`<select name="cartao">`).
     * Esta página NÃO tem o saldo. Exige estar logado (cookie de sessão).
     */
    @GET("sbe-web/usuario/consultarInformacoesCartaoUsuario.html")
    suspend fun consultarInformacoesCartao(): Response<ResponseBody>

    /**
     * Envia o formulário escolhendo um cartão e devolve a página com o saldo
     * dele. Exige o CPF (`documentoNacional`) e o id interno do cartão
     * (`cartao`), que vêm do formulário acima. Mandar campos vazios gera
     * HTTP 500.
     */
    @FormUrlEncoded
    @POST("sbe-web/usuario/consultarInformacoesCartaoUsuario.html")
    suspend fun selecionarCartao(
        @Field("documentoNacional") documentoNacional: String,
        @Field("cartao") cartao: String,
    ): Response<ResponseBody>

    /**
     * Extrato (movimentações) do período. O site faz esta chamada via
     * JavaScript (jQuery), por isso mandamos o cabeçalho X-Requested-With.
     *
     * @param idUsuario identificador interno do usuário no SBE (não é o CPF
     *   nem o id do cartão); vem da página do cartão (ver ExtratoParser).
     * @param dataInicial "dd/MM/yyyy".
     * @param dataFinal "dd/MM/yyyy".
     */
    @FormUrlEncoded
    @Headers("X-Requested-With: XMLHttpRequest")
    @POST("sbe-web/v2/usuario/consultarInformacoesCartaoUsuarioContaCorrente.html")
    suspend fun extrato(
        @Field("filtro.idUsuario") idUsuario: String,
        @Field("filtro.dataInicial") dataInicial: String,
        @Field("filtro.dataFinal") dataFinal: String,
    ): Response<ResponseBody>
}
