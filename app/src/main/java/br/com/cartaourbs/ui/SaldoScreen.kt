package br.com.cartaourbs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.cartaourbs.data.model.SaldoCartao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaldoScreen(
    state: SaldoUiState,
    onUsuarioChange: (String) -> Unit,
    onSenhaChange: (String) -> Unit,
    onLembrarChange: (Boolean) -> Unit,
    onConsultar: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Cartão URBS") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Consulte o saldo do seu cartão transporte",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.usuario,
                onValueChange = onUsuarioChange,
                label = { Text("Usuário (CPF)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            var senhaVisivel by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.senha,
                onValueChange = onSenhaChange,
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = if (senhaVisivel) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                        Icon(
                            imageVector = if (senhaVisivel) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (senhaVisivel) {
                                "Ocultar senha"
                            } else {
                                "Mostrar senha"
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = state.lembrar, onCheckedChange = onLembrarChange)
                Text("Lembrar meus dados neste aparelho")
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onConsultar,
                enabled = !state.carregando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.carregando) "Consultando..." else "Consultar saldo")
            }

            Spacer(Modifier.height(24.dp))

            when {
                state.carregando -> CircularProgressIndicator()
                state.erro != null -> MensagemErro(state.erro)
                state.saldo != null -> CartaoSaldo(state.saldo)
            }
        }
    }
}

@Composable
private fun CartaoSaldo(saldo: SaldoCartao) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Saldo atual", style = MaterialTheme.typography.labelLarge)
            Text(
                saldo.saldoFormatado,
                style = MaterialTheme.typography.headlineLarge,
            )
            saldo.titular?.let { Text("Titular: $it") }
            saldo.numeroCartao?.let { Text("Cartão: $it") }
            saldo.atualizadoEm?.let {
                Text(
                    "Atualizado: $it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MensagemErro(mensagem: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            mensagem,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
