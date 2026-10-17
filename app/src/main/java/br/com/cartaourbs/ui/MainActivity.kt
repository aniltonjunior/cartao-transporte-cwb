package br.com.cartaourbs.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val vm: SaldoViewModel = viewModel()
                    val state by vm.uiState.collectAsState()
                    SaldoScreen(
                        state = state,
                        onUsuarioChange = vm::onUsuarioChange,
                        onSenhaChange = vm::onSenhaChange,
                        onLembrarChange = vm::onLembrarChange,
                        onConsultar = vm::consultar,
                    )
                }
            }
        }
    }
}
