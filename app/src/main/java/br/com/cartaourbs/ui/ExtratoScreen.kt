package br.com.cartaourbs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.cartaourbs.R
import br.com.cartaourbs.data.model.CartaoOpcao
import br.com.cartaourbs.data.model.Movimentacao
import br.com.cartaourbs.data.model.formatarReais

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(
    state: ExtratoUiState,
    cartao: CartaoOpcao?,
    onVoltar: () -> Unit,
    onMesAnterior: () -> Unit,
    onMesSeguinte: () -> Unit,
    onTentarNovamente: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.extrato_titulo))
                        cartao?.let {
                            Text(
                                stringResource(R.string.extrato_cartao, it.numero),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.voltar),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SeletorMes(state, onMesAnterior, onMesSeguinte)
            HorizontalDivider()

            val carregandoTexto = stringResource(R.string.carregando)
            when {
                state.carregando -> Centro {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = carregandoTexto },
                    )
                }
                state.erro != null -> Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MensagemErro(state.erro)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onTentarNovamente) {
                        Text(stringResource(R.string.tentar_novamente))
                    }
                }
                state.movimentacoes.isNullOrEmpty() -> Centro {
                    if (state.movimentacoes != null) {
                        Text(
                            stringResource(R.string.extrato_vazio),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                else -> ListaMovimentacoes(state.movimentacoes.orEmpty())
            }
        }
    }
}

@Composable
private fun SeletorMes(
    state: ExtratoUiState,
    onMesAnterior: () -> Unit,
    onMesSeguinte: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onMesAnterior, enabled = !state.carregando) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.mes_anterior),
            )
        }
        Text(
            state.periodo.nomeCompleto().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        IconButton(onClick = onMesSeguinte, enabled = state.podeAvancar && !state.carregando) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.mes_seguinte),
            )
        }
    }
}

@Composable
private fun ListaMovimentacoes(movimentacoes: List<Movimentacao>) {
    // O SBE lista da mais antiga para a mais nova; mostramos a mais recente primeiro.
    val ordenadas = movimentacoes.asReversed()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(ordenadas) { mov ->
            ItemMovimentacao(mov)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ItemMovimentacao(mov: Movimentacao) {
    val linha = mov.linhaNome?.let { "${mov.linhaCodigo} · $it" } ?: mov.linhaCodigo
    ListItem(
        headlineContent = { Text(mov.operacao) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.mov_linha, linha))
                Text(mov.dataHora, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatarReais(mov.valorCentavos),
                    style = MaterialTheme.typography.titleSmall,
                    // A cor só reforça: o sinal "-" já indica débito.
                    color = if (mov.valorCentavos < 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                mov.saldoCartaoCentavos?.let {
                    Text(
                        stringResource(R.string.mov_saldo, formatarReais(it)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

@Composable
private fun Centro(conteudo: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { conteudo() }
}
