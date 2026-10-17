package br.com.cartaourbs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.cartaourbs.data.ArmazenamentoCredenciais
import br.com.cartaourbs.data.ConsultaSbe
import br.com.cartaourbs.data.CredenciaisStore
import br.com.cartaourbs.data.SaldoRepository
import br.com.cartaourbs.data.model.CartaoOpcao
import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.ErroConsulta
import br.com.cartaourbs.data.model.MesAno
import br.com.cartaourbs.data.model.Movimentacao
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.model.ResultadoExtrato
import br.com.cartaourbs.data.model.SaldoCartao
import br.com.cartaourbs.util.Cpf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tela { SALDO, EXTRATO }

/** Erro de preenchimento de um campo (texto em strings.xml). */
enum class ErroCampo { OBRIGATORIO, CPF_INVALIDO }

data class ExtratoUiState(
    val periodo: MesAno,
    val podeAvancar: Boolean = false,
    val carregando: Boolean = false,
    /** null = ainda não carregou; lista vazia = mês sem movimentação. */
    val movimentacoes: List<Movimentacao>? = null,
    val erro: ErroConsulta? = null,
)

/** Estado completo das telas. */
data class SaldoUiState(
    val tela: Tela = Tela.SALDO,
    /** CPF só com dígitos; a máscara é visual. */
    val cpf: String = "",
    val senha: String = "",
    val lembrar: Boolean = true,
    val erroCpf: ErroCampo? = null,
    val erroSenha: ErroCampo? = null,
    val carregando: Boolean = false,
    val saldo: SaldoCartao? = null,
    val cartoes: List<CartaoOpcao> = emptyList(),
    val cartaoSelecionado: CartaoOpcao? = null,
    val erro: ErroConsulta? = null,
    val extrato: ExtratoUiState,
)

/**
 * ViewModel das telas de saldo e extrato.
 *
 * As dependências chegam pelo construtor (injeção manual): no app, pela
 * [Factory]; nos testes, por fakes. Assim o ViewModel não sabe nada de rede,
 * disco ou Android.
 *
 * @param mesAtual relógio injetável (testes fixam o mês).
 */
class SaldoViewModel(
    private val consulta: ConsultaSbe,
    private val credenciaisStore: ArmazenamentoCredenciais,
    private val mesAtual: () -> MesAno = { MesAno.atual() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SaldoUiState(extrato = ExtratoUiState(periodo = mesAtual()))
    )
    val uiState: StateFlow<SaldoUiState> = _uiState.asStateFlow()

    /**
     * Credenciais da última consulta que deu certo. Trocar de cartão e o
     * extrato usam estas, para bater com os dados mostrados na tela, mesmo
     * que o usuário tenha editado os campos depois.
     */
    private var credenciaisConsultadas: Credenciais? = null

    private var trabalhoSaldo: Job? = null
    private var trabalhoExtrato: Job? = null

    init {
        // Recarrega credenciais salvas (se houver). A leitura e a decifragem
        // rodam fora da thread principal, então a tela abre sem travar.
        viewModelScope.launch {
            val salvas = credenciaisStore.carregar() ?: return@launch
            _uiState.update { atual ->
                // Se o usuário já começou a digitar, não sobrescreve.
                if (atual.cpf.isEmpty() && atual.senha.isEmpty()) {
                    atual.copy(cpf = Cpf.limpar(salvas.usuario), senha = salvas.senha)
                } else {
                    atual
                }
            }
        }
    }

    // ---- Campos ------------------------------------------------------------

    fun onCpfChange(texto: String) =
        _uiState.update { it.copy(cpf = Cpf.limpar(texto), erroCpf = null, erro = null) }

    fun onSenhaChange(valor: String) =
        _uiState.update { it.copy(senha = valor, erroSenha = null, erro = null) }

    fun onLembrarChange(valor: Boolean) =
        _uiState.update { it.copy(lembrar = valor) }

    // ---- Saldo -------------------------------------------------------------

    fun consultar() {
        val estado = _uiState.value
        val erroCpf = when {
            estado.cpf.isEmpty() -> ErroCampo.OBRIGATORIO
            !Cpf.valido(estado.cpf) -> ErroCampo.CPF_INVALIDO
            else -> null
        }
        val erroSenha = if (estado.senha.isBlank()) ErroCampo.OBRIGATORIO else null
        if (erroCpf != null || erroSenha != null) {
            _uiState.update { it.copy(erroCpf = erroCpf, erroSenha = erroSenha) }
            return
        }

        val credenciais = Credenciais(estado.cpf, estado.senha)

        // Grava (ou apaga) em paralelo, para não atrasar a consulta.
        viewModelScope.launch {
            if (estado.lembrar) credenciaisStore.salvar(credenciais) else credenciaisStore.limpar()
        }

        buscarSaldo(credenciais, cartaoId = null, limparTela = true)
    }

    /** Consulta outro cartão da mesma conta. */
    fun selecionarCartao(id: String) {
        val credenciais = credenciaisConsultadas ?: return
        if (id == _uiState.value.cartaoSelecionado?.id) return
        buscarSaldo(credenciais, cartaoId = id, limparTela = false)
    }

    private fun buscarSaldo(credenciais: Credenciais, cartaoId: String?, limparTela: Boolean) {
        trabalhoSaldo?.cancel()
        trabalhoSaldo = viewModelScope.launch {
            _uiState.update {
                if (limparTela) {
                    it.copy(carregando = true, erro = null, saldo = null, cartoes = emptyList(),
                        cartaoSelecionado = null)
                } else {
                    it.copy(carregando = true, erro = null)
                }
            }
            when (val r = consulta.consultarSaldo(credenciais, cartaoId)) {
                is ResultadoConsulta.Sucesso -> {
                    credenciaisConsultadas = credenciais
                    _uiState.update {
                        it.copy(
                            carregando = false,
                            saldo = r.saldo,
                            cartoes = r.cartoes,
                            cartaoSelecionado = r.cartaoConsultado,
                            // Extrato antigo é de outro cartão/usuário.
                            extrato = it.extrato.copy(movimentacoes = null, erro = null),
                        )
                    }
                }
                is ResultadoConsulta.Erro ->
                    _uiState.update { it.copy(carregando = false, erro = r.erro) }
            }
        }
    }

    // ---- Extrato -----------------------------------------------------------

    fun abrirExtrato() {
        if (credenciaisConsultadas == null) return
        _uiState.update { it.copy(tela = Tela.EXTRATO) }
        if (_uiState.value.extrato.movimentacoes == null) buscarExtrato()
    }

    fun voltar() {
        trabalhoExtrato?.cancel()
        _uiState.update {
            it.copy(tela = Tela.SALDO, extrato = it.extrato.copy(carregando = false))
        }
    }

    fun mesAnterior() = mudarPeriodo(_uiState.value.extrato.periodo.anterior())

    fun mesSeguinte() {
        val seguinte = _uiState.value.extrato.periodo.seguinte()
        if (seguinte > mesAtual()) return // não existe extrato do futuro
        mudarPeriodo(seguinte)
    }

    fun tentarExtratoNovamente() = buscarExtrato()

    private fun mudarPeriodo(periodo: MesAno) {
        _uiState.update {
            it.copy(extrato = it.extrato.copy(periodo = periodo, podeAvancar = periodo < mesAtual()))
        }
        buscarExtrato()
    }

    private fun buscarExtrato() {
        val credenciais = credenciaisConsultadas ?: return
        val estado = _uiState.value
        val periodo = estado.extrato.periodo
        val cartaoId = estado.cartaoSelecionado?.id

        trabalhoExtrato?.cancel()
        trabalhoExtrato = viewModelScope.launch {
            _uiState.update {
                it.copy(extrato = it.extrato.copy(carregando = true, erro = null, movimentacoes = null))
            }
            val r = consulta.consultarExtrato(credenciais, cartaoId, periodo)
            _uiState.update {
                it.copy(
                    extrato = when (r) {
                        is ResultadoExtrato.Sucesso ->
                            it.extrato.copy(carregando = false, movimentacoes = r.movimentacoes)
                        is ResultadoExtrato.Erro ->
                            it.extrato.copy(carregando = false, erro = r.erro)
                    }
                )
            }
        }
    }

    companion object {
        /** Monta o ViewModel com as dependências reais do app. */
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SaldoViewModel(
                    consulta = SaldoRepository(),
                    credenciaisStore = CredenciaisStore.criar(app),
                )
            }
        }
    }
}
