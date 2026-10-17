package br.com.cartaourbs.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.cartaourbs.BuildConfig
import br.com.cartaourbs.R

private const val URL_SBE = "https://sbe.curitiba.pr.gov.br/sbe-web/"
private const val URL_URBS_CARTAO = "https://www.urbs.curitiba.pr.gov.br/portal/cartao-transporte/"

/**
 * Tela "Sobre": aviso de app não oficial, fonte dos dados, resumo de
 * privacidade, contato e licenças.
 *
 * A Play Store exige que apps não oficiais que tratam de serviços públicos
 * deixem claro, dentro do app, que não representam o órgão e de onde vêm os
 * dados. Apps que lidam com dados sensíveis (senha) também precisam mostrar a
 * política de privacidade dentro do app: o resumo fica aqui e o texto
 * completo no link configurado em gradle.properties.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobreScreen(onVoltar: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sobre_titulo)) },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.sobre_versao, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Titulo(stringResource(R.string.sobre_aviso_titulo))
                    Text(stringResource(R.string.sobre_aviso_texto))
                }
            }

            Secao(stringResource(R.string.sobre_fonte_titulo)) {
                Text(stringResource(R.string.sobre_fonte_texto))
                LinkExterno(stringResource(R.string.link_sbe), URL_SBE)
                LinkExterno(stringResource(R.string.link_urbs), URL_URBS_CARTAO)
            }

            Secao(stringResource(R.string.privacidade_titulo)) {
                stringArrayResource(R.array.privacidade_itens).forEach { item ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•  ")
                        Text(item)
                    }
                }
                if (BuildConfig.URL_POLITICA_PRIVACIDADE.isNotBlank()) {
                    LinkExterno(
                        stringResource(R.string.privacidade_online),
                        BuildConfig.URL_POLITICA_PRIVACIDADE,
                    )
                }
            }

            if (BuildConfig.EMAIL_CONTATO.isNotBlank()) {
                LinkExterno(
                    stringResource(R.string.sobre_contato, BuildConfig.EMAIL_CONTATO),
                    "mailto:${BuildConfig.EMAIL_CONTATO}",
                )
            }

            Secao(stringResource(R.string.licencas_titulo)) {
                Text(stringResource(R.string.licencas_texto), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Titulo(titulo)
        conteudo()
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Botão de texto que abre o link no navegador (ou no app de e-mail). */
@Composable
private fun LinkExterno(texto: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val erro = stringResource(R.string.erro_abrir_link)
    TextButton(
        onClick = {
            try {
                uriHandler.openUri(url)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, erro, Toast.LENGTH_SHORT).show()
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, erro, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Text(texto)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null, // o texto do botão já descreve o link
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
