package br.com.cartaourbs.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.cartaourbs.data.model.Credenciais

/**
 * Guarda as credenciais do usuário de forma CRIPTOGRAFADA no aparelho,
 * usando EncryptedSharedPreferences (chave protegida pelo Android Keystore).
 *
 * As credenciais nunca aparecem no código-fonte: são digitadas pelo usuário
 * e persistidas apenas aqui, cifradas.
 *
 * Robustez: se o arquivo não puder ser decifrado (ex.: a chave do Keystore
 * foi perdida, o que acontece quando o arquivo é restaurado em outro aparelho
 * ou após certas atualizações do sistema), o arquivo é apagado e o app segue
 * como se não houvesse credenciais salvas, em vez de travar.
 * O backup do app também está desligado (ver AndroidManifest.xml).
 */
class CredenciaisStore(private val context: Context) {

    /** null quando nem recriando o arquivo foi possível abrir o armazenamento. */
    private val prefs: SharedPreferences? by lazy {
        try {
            criarPrefs()
        } catch (_: Exception) {
            // Arquivo corrompido ou chave perdida: descarta e tenta de novo.
            apagarArquivo()
            try {
                criarPrefs()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun criarPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ARQUIVO,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun apagarArquivo() {
        context.deleteSharedPreferences(ARQUIVO)
    }

    fun salvar(credenciais: Credenciais) {
        try {
            prefs?.edit()
                ?.putString(KEY_USUARIO, credenciais.usuario)
                ?.putString(KEY_SENHA, credenciais.senha)
                ?.apply()
        } catch (_: Exception) {
            // Não conseguir lembrar as credenciais não deve impedir a consulta.
        }
    }

    fun carregar(): Credenciais? {
        return try {
            val p = prefs ?: return null
            val usuario = p.getString(KEY_USUARIO, null) ?: return null
            val senha = p.getString(KEY_SENHA, null) ?: return null
            Credenciais(usuario, senha)
        } catch (_: Exception) {
            // Valor que não decifra: limpa para não falhar de novo na próxima vez.
            limpar()
            null
        }
    }

    fun limpar() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (_: Exception) {
            apagarArquivo()
        }
    }

    private companion object {
        const val ARQUIVO = "credenciais_urbs"
        const val KEY_USUARIO = "usuario"
        const val KEY_SENHA = "senha"
    }
}
