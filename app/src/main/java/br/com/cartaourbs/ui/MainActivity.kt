package br.com.cartaourbs.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.cartaourbs.ui.theme.CartaoUrbsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CartaoUrbsTheme {
                val vm: SaldoViewModel = viewModel(factory = SaldoViewModel.Factory)
                // Para de coletar quando o app vai para segundo plano.
                val state by vm.uiState.collectAsStateWithLifecycle()

                // Navegação simples por estado: duas telas não justificam uma
                // biblioteca de navegação.
                when (state.tela) {
                    Tela.SALDO -> SaldoScreen(
                        state = state,
                        onCpfChange = vm::onCpfChange,
                        onSenhaChange = vm::onSenhaChange,
                        onLembrarChange = vm::onLembrarChange,
                        onConsultar = vm::consultar,
                        onSelecionarCartao = vm::selecionarCartao,
                        onAbrirExtrato = vm::abrirExtrato,
                    )
                    Tela.EXTRATO -> {
                        // Botão/gesto "voltar" do sistema volta para o saldo.
                        BackHandler(onBack = vm::voltar)
                        ExtratoScreen(
                            state = state.extrato,
                            cartao = state.cartaoSelecionado,
                            onVoltar = vm::voltar,
                            onMesAnterior = vm::mesAnterior,
                            onMesSeguinte = vm::mesSeguinte,
                            onTentarNovamente = vm::tentarExtratoNovamente,
                        )
                    }
                }
            }
        }
    }
}
