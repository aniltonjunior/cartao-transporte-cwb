package br.com.cartaourbs.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Interface Retrofit que descreve as chamadas HTTP ao Sistema de Bilhetagem
 * Eletrônica (SBE) da URBS.
 *
 * IMPORTANTE: a URBS não publica uma API oficial. Estas chamadas reproduzem
 * exatamente o que o site web faz quando você consulta o saldo. Por isso,
 * retornamos [ResponseBody] bruto (HTML) e extraímos os dados com Jsoup.
 *
 * Fluxo real capturado do site:
 *   1. POST /sbe-web/login/login.html           -> 302, cria sessão (cookie)
 *   2. GET  /sbe-web/usuario/consultarInformacoesCartaoUsuario.html
 *                                                -> HTML com saldo atual,
 *                                                   titular e lista de cartões
 *   (O POST na mesma URL só é usado para TROCAR o cartão selecionado, e exige
 *    documentoNacional + id interno do cartão. Por isso o padrão é o GET.)
 *
 * A sessão é mantida por cookie (JSESSIONID). O OkHttp guarda o cookie do
 * login automaticamente (ver SbeClient) e o reenvia na consulta.
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
     * Busca a página com o saldo atual, o titular e a lista de cartões.
     *
     * Este é o GET que o site faz ao abrir a tela logo após o login. Já traz
     * o saldo do cartão padrão, sem precisar enviar CPF nem id do cartão.
     * Exige estar logado (cookie de sessão).
     */
    /**
     * Página inicial da área logada. O navegador a acessa logo após o login
     * (passando por alguns redirects). Chamá-la "aquece" a sessão antes de
     * consultar o cartão, reproduzindo o comportamento do site.
     */
    @GET("sbe-web/central/principal.html")
    suspend fun principal(): Response<ResponseBody>

    @GET("sbe-web/usuario/consultarInformacoesCartaoUsuario.html")
    suspend fun consultarInformacoesCartao(): Response<ResponseBody>

    /**
     * Troca o cartão selecionado e devolve a página com o saldo desse cartão.
     *
     * Usado apenas quando o usuário tem mais de um cartão e quer consultar um
     * específico. Exige o CPF (`documentoNacional`) e o id interno do cartão
     * (`cartao`), que vêm do `<select name="cartao">` da página.
     */
    @FormUrlEncoded
    @POST("sbe-web/usuario/consultarInformacoesCartaoUsuario.html")
    suspend fun selecionarCartao(
        @Field("documentoNacional") documentoNacional: String,
        @Field("cartao") cartao: String,
    ): Response<ResponseBody>
}
