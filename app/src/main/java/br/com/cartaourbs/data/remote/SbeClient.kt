package br.com.cartaourbs.data.remote

import br.com.cartaourbs.BuildConfig
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Agrupa o [SbeApi] e o [CookieJar] da mesma instância de OkHttp, para que o
 * repositório possa limpar os cookies de sessão entre consultas.
 */
class SbeSession(
    val api: SbeApi,
    private val cookieJar: SessionCookieJar,
) {
    /** Zera a sessão (cookies). Chame antes de cada login para evitar reusar
     *  a sessão de um usuário anterior. */
    fun limparSessao() = cookieJar.limpar()
}

/**
 * Constrói a sessão (Retrofit/OkHttp) usada para falar com o SBE.
 *
 * Pontos de atenção para o SBE (site antigo, baseado em sessão):
 *  - Mantemos o cookie de sessão (JSESSIONID) do login para reusá-lo nas
 *    chamadas seguintes da MESMA consulta (ver [SessionCookieJar]).
 *  - A cada nova consulta o repositório chama [SbeSession.limparSessao] para
 *    não herdar a sessão de um usuário anterior.
 *  - Mandamos um User-Agent de navegador, porque alguns servidores recusam
 *    requisições sem ele.
 */
object SbeClient {

    const val BASE_URL = "https://sbe.curitiba.pr.gov.br/"

    fun create(): SbeSession {
        val userAgentInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android) AppCartaoUrbs/1.0",
                )
                .build()
            chain.proceed(request)
        }

        val cookieJar = SessionCookieJar()

        val okHttp = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(userAgentInterceptor)
            .apply {
                // Log de rede só no build de debug. No release não sai nada
                // no Logcat. BASIC mostra só método, URL e código, sem
                // cabeçalhos (que teriam os cookies de sessão). Para depurar,
                // suba temporariamente para Level.HEADERS.
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .build()
            .create(SbeApi::class.java)

        return SbeSession(api, cookieJar)
    }
}
