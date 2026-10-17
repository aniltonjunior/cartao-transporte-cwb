package br.com.cartaourbs.data.seguranca

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * Cifra e decifra bytes. Fica atrás de uma interface para que o
 * armazenamento de credenciais possa ser testado na JVM, sem o Android
 * Keystore.
 */
interface Cifrador {
    /**
     * @param contexto dado associado (não secreto) amarrado ao texto cifrado.
     *   Usamos o nome do campo: assim um valor cifrado como "senha" não decifra
     *   se for colado no lugar do "usuário".
     */
    fun cifrar(texto: ByteArray, contexto: ByteArray): ByteArray

    /** @throws GeneralSecurityException se a chave não confere ou o dado foi alterado. */
    fun decifrar(cifrado: ByteArray, contexto: ByteArray): ByteArray
}

/** [Cifrador] baseado em uma primitiva AEAD do Tink (AES-256-GCM). */
class TinkCifrador(private val aead: Aead) : Cifrador {
    override fun cifrar(texto: ByteArray, contexto: ByteArray): ByteArray =
        aead.encrypt(texto, contexto)

    override fun decifrar(cifrado: ByteArray, contexto: ByteArray): ByteArray =
        aead.decrypt(cifrado, contexto)
}

/**
 * Cria o [Cifrador] do app no Android.
 *
 * Como funciona:
 *  - O Tink gera um conjunto de chaves AES-256-GCM (o "keyset").
 *  - Esse keyset é guardado em SharedPreferences, mas CIFRADO por uma chave
 *    mestra que mora no Android Keystore (hardware seguro quando disponível)
 *    e nunca sai do aparelho.
 *
 * Se o keyset não puder ser aberto (ex.: a chave mestra sumiu depois de uma
 * restauração ou de uma troca de bloqueio de tela em alguns aparelhos), o
 * keyset e a chave mestra são descartados e recriados. As credenciais
 * antigas ficam ilegíveis e o [br.com.cartaourbs.data.CredenciaisStore]
 * as apaga: o usuário só precisa digitar de novo.
 *
 * Faz I/O e acessa o Keystore: chame fora da thread principal.
 */
object CifradorAndroid {

    private const val ARQUIVO_KEYSET = "tink_keyset_credenciais"
    private const val NOME_KEYSET = "credenciais_keyset"
    private const val ALIAS_CHAVE_MESTRA = "cartaourbs_chave_mestra"
    private const val URI_CHAVE_MESTRA = "android-keystore://$ALIAS_CHAVE_MESTRA"

    /** @return o cifrador, ou null se o Keystore estiver indisponível. */
    fun criarOuNull(context: Context): Cifrador? {
        return try {
            AeadConfig.register()
            try {
                criar(context)
            } catch (_: Exception) {
                descartarChaves(context)
                criar(context)
            }
        } catch (_: Exception) {
            // Sem Keystore funcional não guardamos nada: melhor do que salvar
            // a senha em texto puro.
            null
        }
    }

    private fun criar(context: Context): Cifrador {
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, NOME_KEYSET, ARQUIVO_KEYSET)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(URI_CHAVE_MESTRA)
            .build()
            .keysetHandle
        val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        return TinkCifrador(aead)
    }

    private fun descartarChaves(context: Context) {
        context.deleteSharedPreferences(ARQUIVO_KEYSET)
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(ALIAS_CHAVE_MESTRA)
            }
        } catch (_: Exception) {
            // Se não der para apagar, a recriação ainda pode funcionar.
        }
    }
}
