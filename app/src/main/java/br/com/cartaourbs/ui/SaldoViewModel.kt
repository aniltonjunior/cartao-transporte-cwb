package br.com.cartaourbs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.cartaourbs.data.CredenciaisStore
import br.com.cartaourbs.data.SaldoRepository
import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.model.SaldoCartao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado completo da tela de saldo. */
data class SaldoUiState(
    val usuario: String = "",
    val senha: String = "",
    val lembrar: Boolean = true,
    val carregando: Boolean = false,
    val saldo: SaldoCartao? = null,
    val erro: String? = null,
)

class SaldoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SaldoRepository()
    private val credenciaisStore = CredenciaisStore(app)

    private val _uiState = MutableStateFlow(SaldoUiState())
    val uiState: StateFlow<SaldoUiState> = _uiState.asStateFlow()

    init {
        // Recarrega credenciais salvas (se houver) ao abrir o app.
        credenciaisStore.carregar()?.let { cred ->
            _uiState.update { it.copy(usuario = cred.usuario, senha = cred.senha) }
        }
    }

    fun onUsuarioChange(valor: String) =
        _uiState.update { it.copy(usuario = valor, erro = null) }

    fun onSenhaChange(valor: String) =
        _uiState.update { it.copy(senha = valor, erro = null) }

    fun onLembrarChange(valor: Boolean) =
        _uiState.update { it.copy(lembrar = valor) }

    fun consultar() {
        val estado = _uiState.value
        if (estado.usuario.isBlank() || estado.senha.isBlank()) {
            _uiState.update { it.copy(erro = "Preencha usuário e senha.") }
            return
        }

        val credenciais = Credenciais(estado.usuario.trim(), estado.senha)

        if (estado.lembrar) {
            credenciaisStore.salvar(credenciais)
        } else {
            credenciaisStore.limpar()
        }

        viewModelScope.launch {
            _uiState.update { it.copy(carregando = true, erro = null, saldo = null) }
            when (val resultado = repository.consultarSaldo(credenciais)) {
                is ResultadoConsulta.Sucesso ->
                    _uiState.update {
                        it.copy(carregando = false, saldo = resultado.saldo)
                    }
                is ResultadoConsulta.Erro ->
                    _uiState.update {
                        it.copy(carregando = false, erro = resultado.mensagem)
                    }
            }
        }
    }
}
