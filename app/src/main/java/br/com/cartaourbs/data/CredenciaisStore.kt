package br.com.cartaourbs.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.seguranca.Cifrador
import br.com.cartaourbs.data.seguranca.CifradorAndroid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Arquivo do DataStore (fica em files/datastore/credenciais_seguras.preferences_pb).
 * O delegate garante UMA instância por processo, como o DataStore exige.
 */
private val Context.credenciaisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "credenciais_seguras",
)

/** O que o ViewModel precisa para lembrar as credenciais (fake nos testes). */
interface ArmazenamentoCredenciais {
    suspend fun salvar(credenciais: Credenciais)
    suspend fun carregar(): Credenciais?
    suspend fun limpar()
}

/**
 * Guarda as credenciais do usuário CIFRADAS no aparelho.
 *
 * - Persistência: Jetpack DataStore (substitui o EncryptedSharedPreferences,
 *   que o Google descontinuou).
 * - Criptografia: cada campo é cifrado com AES-256-GCM via Tink antes de ir
 *   para o disco; a chave fica protegida pelo Android Keystore (ver
 *   [CifradorAndroid]). No arquivo só existem bytes cifrados.
 * - Tudo roda fora da thread principal (funções suspend em [io]).
 *
 * Robustez: se um valor salvo não decifrar (chave perdida, arquivo
 * restaurado de outro aparelho, dado corrompido), as credenciais são apagadas
 * e o app segue como se não houvesse nada salvo. Se o Keystore estiver
 * indisponível, nada é gravado (nunca salvamos a senha em texto puro).
 *
 * @param fornecerCifrador cria o cifrador; chamado uma única vez, em [io].
 */
class CredenciaisStore(
    private val dataStore: DataStore<Preferences>,
    fornecerCifrador: () -> Cifrador?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ArmazenamentoCredenciais {

    private val cifrador: Cifrador? by lazy(fornecerCifrador)

    override suspend fun salvar(credenciais: Credenciais) {
        withContext(io) {
            val c = cifrador ?: return@withContext
            dataStore.edit { prefs ->
                prefs[CHAVE_USUARIO] = c.cifrar(credenciais.usuario.toByteArray(), CONTEXTO_USUARIO)
                prefs[CHAVE_SENHA] = c.cifrar(credenciais.senha.toByteArray(), CONTEXTO_SENHA)
            }
        }
    }

    override suspend fun carregar(): Credenciais? = withContext(io) {
        val prefs = dataStore.data.first()
        val usuarioCifrado = prefs[CHAVE_USUARIO] ?: return@withContext null
        val senhaCifrada = prefs[CHAVE_SENHA] ?: return@withContext null
        val c = cifrador ?: return@withContext null

        try {
            Credenciais(
                usuario = String(c.decifrar(usuarioCifrado, CONTEXTO_USUARIO)),
                senha = String(c.decifrar(senhaCifrada, CONTEXTO_SENHA)),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Não decifra: descarta para não falhar de novo na próxima vez.
            dataStore.edit { it.limparCredenciais() }
            null
        }
    }

    override suspend fun limpar() {
        withContext(io) {
            dataStore.edit { it.limparCredenciais() }
        }
    }

    private fun MutablePreferences.limparCredenciais() {
        remove(CHAVE_USUARIO)
        remove(CHAVE_SENHA)
    }

    companion object {
        private val CHAVE_USUARIO = byteArrayPreferencesKey("usuario")
        private val CHAVE_SENHA = byteArrayPreferencesKey("senha")
        private val CONTEXTO_USUARIO = "cartaourbs:usuario".toByteArray()
        private val CONTEXTO_SENHA = "cartaourbs:senha".toByteArray()

        /** Arquivo da versão anterior (EncryptedSharedPreferences). */
        private const val ARQUIVO_LEGADO = "credenciais_urbs"

        /**
         * Cria o armazenamento do app.
         *
         * Na primeira vez, apaga o arquivo da versão antiga
         * (EncryptedSharedPreferences). Os dados antigos não são migrados:
         * o usuário digita usuário e senha mais uma vez.
         */
        fun criar(context: Context): CredenciaisStore {
            val app = context.applicationContext
            return CredenciaisStore(
                dataStore = app.credenciaisDataStore,
                fornecerCifrador = {
                    app.deleteSharedPreferences(ARQUIVO_LEGADO)
                    CifradorAndroid.criarOuNull(app)
                },
            )
        }
    }
}
