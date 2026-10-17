package br.com.cartaourbs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.cartaourbs.R
import br.com.cartaourbs.data.model.CartaoOpcao
import br.com.cartaourbs.data.model.ErroConsulta
import br.com.cartaourbs.data.model.SaldoCartao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaldoScreen(
    state: SaldoUiState,
    onCpfChange: (String) -> Unit,
    onSenhaChange: (String) -> Unit,
    onLembrarChange: (Boolean) -> Unit,
    onConsultar: () -> Unit,
    onSelecionarCartao: (String) -> Unit,
    onAbrirExtrato: () -> Unit,
    onAbrirSobre: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onAbrirSobre) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.sobre_abrir),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.saldo_subtitulo),
                style = MaterialTheme.typography.bodyLarge,
            )
            // Aviso visível já na primeira tela (regra da Play Store para apps
            // não oficiais de serviços públicos). Detalhes na tela "Sobre".
            Text(
                stringResource(R.string.aviso_nao_oficial_curto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            CampoCpf(state.cpf, state.erroCpf, onCpfChange)
            Spacer(Modifier.height(12.dp))
            CampoSenha(state.senha, state.erroSenha, onSenhaChange, onConsultar)
            Spacer(Modifier.height(8.dp))

            LinhaLembrar(state.lembrar, onLembrarChange)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onConsultar,
                enabled = !state.carregando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.carregando) R.string.botao_consultando else R.string.botao_consultar
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            if (state.cartoes.size > 1) {
                SeletorCartao(
                    cartoes = state.cartoes,
                    selecionado = state.cartaoSelecionado,
                    habilitado = !state.carregando,
                    onSelecionar = onSelecionarCartao,
                )
                Spacer(Modifier.height(16.dp))
            }

            val carregandoTexto = stringResource(R.string.carregando)
            when {
                state.carregando -> CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = carregandoTexto },
                )
                state.erro != null -> MensagemErro(state.erro)
                state.saldo != null -> {
                    CartaoSaldo(state.saldo)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onAbrirExtrato, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text(stringResource(R.string.botao_extrato))
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoCpf(cpf: String, erro: ErroCampo?, onChange: (String) -> Unit) {
    val textoErro = erro?.mensagem()
    OutlinedTextField(
        value = cpf,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.campo_cpf)) },
        placeholder = { Text("000.000.000-00") },
        singleLine = true,
        isError = erro != null,
        supportingText = textoErro?.let { { Text(it) } },
        visualTransformation = CpfVisualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            // O leitor de tela anuncia o erro junto com o campo.
            .semantics { if (textoErro != null) error(textoErro) },
    )
}

@Composable
private fun CampoSenha(
    senha: String,
    erro: ErroCampo?,
    onChange: (String) -> Unit,
    onConcluir: () -> Unit,
) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val textoErro = erro?.mensagem()
    OutlinedTextField(
        value = senha,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.campo_senha)) },
        singleLine = true,
        isError = erro != null,
        supportingText = textoErro?.let { { Text(it) } },
        visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onConcluir() }),
        trailingIcon = {
            IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                Icon(
                    imageVector = if (senhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (senhaVisivel) R.string.ocultar_senha else R.string.mostrar_senha
                    ),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (textoErro != null) error(textoErro) },
    )
}

@Composable
private fun LinhaLembrar(lembrar: Boolean, onChange: (Boolean) -> Unit) {
    // A linha inteira é clicável (alvo de toque maior) e lida como uma
    // caixa de seleção pelo leitor de tela.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = lembrar, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = lembrar, onCheckedChange = null)
        Spacer(Modifier.padding(start = 8.dp))
        Text(stringResource(R.string.lembrar_dados))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorCartao(
    cartoes: List<CartaoOpcao>,
    selecionado: CartaoOpcao?,
    habilitado: Boolean,
    onSelecionar: (String) -> Unit,
) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = aberto && habilitado,
        onExpandedChange = { if (habilitado) aberto = it },
    ) {
        OutlinedTextField(
            value = selecionado?.let { rotuloCartao(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            label = { Text(stringResource(R.string.campo_cartao)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, habilitado)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = aberto && habilitado, onDismissRequest = { aberto = false }) {
            cartoes.forEach { cartao ->
                DropdownMenuItem(
                    text = { Text(rotuloCartao(cartao)) },
                    onClick = {
                        aberto = false
                        onSelecionar(cartao.id)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun rotuloCartao(cartao: CartaoOpcao): String =
    stringResource(R.string.cartao_opcao, cartao.numero, cartao.situacao.ifEmpty { cartao.descricao })

@Composable
private fun CartaoSaldo(saldo: SaldoCartao) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Avisa o leitor de tela quando o saldo aparece ou muda.
            .semantics { liveRegion = LiveRegionMode.Polite },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.saldo_atual),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(saldo.saldoFormatado, style = MaterialTheme.typography.headlineLarge)
            saldo.titular?.let { Text(stringResource(R.string.saldo_titular, it)) }
            saldo.numeroCartao?.let { Text(stringResource(R.string.saldo_cartao, it)) }
            saldo.atualizadoEm?.let {
                Text(
                    stringResource(R.string.saldo_atualizado, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun MensagemErro(erro: ErroConsulta, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            erro.mensagem(),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
