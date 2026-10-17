package br.com.cartaourbs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.cartaourbs.R
import br.com.cartaourbs.data.model.ErroConsulta
import br.com.cartaourbs.data.model.TipoErro

/**
 * Traduz erros da camada de dados para textos de strings.xml.
 *
 * O repositório e o ViewModel só lidam com tipos ([TipoErro], [ErroCampo]);
 * o texto é escolhido aqui, na UI. Assim todo texto visível fica num lugar
 * só (strings.xml) e pode ser revisado ou traduzido sem mexer na lógica.
 */
@Composable
fun ErroConsulta.mensagem(): String = when (tipo) {
    TipoErro.CREDENCIAIS_INVALIDAS -> stringResource(R.string.erro_credenciais_invalidas)
    TipoErro.SESSAO_PERDIDA -> stringResource(R.string.erro_sessao_perdida)
    TipoErro.ERRO_DO_SITE -> codigoHttp
        ?.let { stringResource(R.string.erro_do_site, it) }
        ?: stringResource(R.string.erro_do_site_sem_codigo)
    TipoErro.SEM_CONEXAO -> stringResource(R.string.erro_sem_conexao)
    TipoErro.INESPERADO -> stringResource(R.string.erro_inesperado)
    TipoErro.CARTOES_NAO_ENCONTRADOS -> stringResource(R.string.erro_cartoes_nao_encontrados)
    TipoErro.SEM_CARTAO -> stringResource(R.string.erro_sem_cartao)
    TipoErro.SALDO_NAO_ENCONTRADO -> stringResource(R.string.erro_saldo_nao_encontrado)
    TipoErro.USUARIO_DO_EXTRATO_NAO_ENCONTRADO -> stringResource(R.string.erro_usuario_extrato)
    TipoErro.EXTRATO_NAO_ENCONTRADO -> stringResource(R.string.erro_extrato_nao_encontrado)
}

@Composable
fun ErroCampo.mensagem(): String = when (this) {
    ErroCampo.OBRIGATORIO -> stringResource(R.string.erro_campo_obrigatorio)
    ErroCampo.CPF_INVALIDO -> stringResource(R.string.erro_cpf_invalido)
}
