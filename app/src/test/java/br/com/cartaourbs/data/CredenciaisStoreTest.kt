package br.com.cartaourbs.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.seguranca.Cifrador
import br.com.cartaourbs.data.seguranca.TinkCifrador
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Testa o armazenamento na JVM: DataStore real num arquivo temporário e
 * criptografia Tink real, com a chave em memória (no lugar do Android Keystore).
 */
class CredenciaisStoreTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private lateinit var escopo: CoroutineScope
    private lateinit var arquivo: File
    private lateinit var dataStore: DataStore<Preferences>

    private val credenciais = Credenciais(usuario = "12345678909", senha = "senha-super-secreta")

    private fun novoCifrador(): Cifrador {
        AeadConfig.register()
        val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        return TinkCifrador(handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
    }

    private fun store(cifrador: Cifrador?) =
        CredenciaisStore(dataStore, fornecerCifrador = { cifrador }, io = Dispatchers.IO)

    @Before
    fun prepara() {
        escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
        arquivo = File(pasta.root, "teste.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = escopo, produceFile = { arquivo })
    }

    @After
    fun encerra() {
        escopo.cancel()
    }

    @Test
    fun `salva e carrega as credenciais`() = runTest {
        val s = store(novoCifrador())
        s.salvar(credenciais)
        assertEquals(credenciais, s.carregar())
    }

    @Test
    fun `nada salvo retorna nulo`() = runTest {
        assertNull(store(novoCifrador()).carregar())
    }

    @Test
    fun `arquivo em disco nao contem a senha nem o usuario em texto puro`() = runTest {
        store(novoCifrador()).salvar(credenciais)

        val bytes = arquivo.readBytes()
        val conteudo = String(bytes, Charsets.ISO_8859_1)
        assertTrue("o arquivo deveria existir", bytes.isNotEmpty())
        assertFalse(conteudo.contains(credenciais.senha))
        assertFalse(conteudo.contains(credenciais.usuario))
    }

    @Test
    fun `chave diferente - dado ilegivel e apagado em vez de travar`() = runTest {
        // Simula a chave do Keystore perdida: outro cifrador não decifra.
        store(novoCifrador()).salvar(credenciais)

        val comOutraChave = store(novoCifrador())
        assertNull(comOutraChave.carregar())

        // Foi apagado: o arquivo não tem mais as chaves de credenciais.
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `limpar remove as credenciais`() = runTest {
        val s = store(novoCifrador())
        s.salvar(credenciais)
        s.limpar()
        assertNull(s.carregar())
    }

    @Test
    fun `sem Keystore disponivel nada e gravado`() = runTest {
        val s = store(cifrador = null)
        s.salvar(credenciais)

        assertNull(s.carregar())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `usuario e senha nao podem ser trocados de lugar no arquivo`() = runTest {
        // O nome do campo entra como dado associado na cifragem.
        val c = novoCifrador()
        val cifradoComoSenha = c.cifrar("abc".toByteArray(), "cartaourbs:senha".toByteArray())
        val falhou = runCatching {
            c.decifrar(cifradoComoSenha, "cartaourbs:usuario".toByteArray())
        }.isFailure
        assertTrue(falhou)
    }
}
