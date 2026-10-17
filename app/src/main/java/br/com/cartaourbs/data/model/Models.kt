package br.com.cartaourbs.data.model

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Credenciais de acesso ao Sistema de Bilhetagem Eletrônica (SBE) da URBS.
 *
 * - [usuario]: normalmente o CPF usado no login do site.
 * - [senha]: a senha pessoal do usuário.
 *
 * Estes valores são digitados pelo usuário no app e guardados de forma
 * criptografada no aparelho. Eles nunca ficam escritos no código-fonte.
 */
data class Credenciais(
    val usuario: String,
    val senha: String,
)

/**
 * Resultado da consulta de saldo.
 *
 * - [numeroCartao]: identificação do cartão consultado (quando disponível).
 * - [saldoCentavos]: saldo em centavos, para evitar erros de ponto flutuante.
 * - [titular]: nome do titular, quando o site retorna.
 * - [atualizadoEm]: texto da data/hora informada pela URBS, quando disponível.
 */
data class SaldoCartao(
    val numeroCartao: String?,
    val saldoCentavos: Long,
    val titular: String?,
    val atualizadoEm: String?,
) {
    /** Saldo formatado em reais, ex.: "R$ 1.234,56" ou "-R$ 1,50". */
    val saldoFormatado: String
        get() = formatarReais(saldoCentavos)
}

private val LOCALE_PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/**
 * Formata centavos como moeda brasileira, com separador de milhar e sinal
 * negativo corretos.
 *
 * Usa BigDecimal com escala 2 para não passar por ponto flutuante. Cria um
 * NumberFormat por chamada porque ele não é thread-safe.
 *
 * Atenção: o padrão pt-BR coloca um espaço NÃO separável (U+00A0) entre
 * "R$" e o número. Na tela isso é invisível, mas em testes faz diferença.
 */
fun formatarReais(centavos: Long): String =
    NumberFormat.getCurrencyInstance(LOCALE_PT_BR)
        .format(BigDecimal.valueOf(centavos, 2))

/**
 * Um cartão listado no formulário de seleção do SBE.
 *
 * - [id]: value interno do <option> (ex.: "1000001"), usado no POST.
 * - [numero]: número impresso do cartão (ex.: "0001234567").
 * - [descricao]: texto completo do option (ex.: "0001234567 - Usuário - Ativo").
 * - [ativo]: true quando a descrição indica "Ativo".
 */
data class CartaoOpcao(
    val id: String,
    val numero: String,
    val descricao: String,
    val ativo: Boolean,
) {
    /** Situação do cartão, ex.: "Ativo" ou "Com solicitação de bloqueio". */
    val situacao: String
        get() = descricao.substringAfterLast(" - ", missingDelimiterValue = "").trim()
}

/**
 * Por que uma consulta falhou. A camada de dados só diz O QUE aconteceu; o
 * texto mostrado ao usuário fica em `res/values/strings.xml` (ver
 * `ui/Mensagens.kt`). Isso mantém o repositório livre de textos de tela e
 * facilita traduzir ou ajustar mensagens.
 */
enum class TipoErro {
    /** O SBE voltou para a tela de login: usuário ou senha errados. */
    CREDENCIAIS_INVALIDAS,

    /** Login passou, mas a sessão caiu antes da consulta. */
    SESSAO_PERDIDA,

    /** O site respondeu com erro HTTP (ver [ErroConsulta.codigoHttp]). */
    ERRO_DO_SITE,

    /** Sem internet, timeout, DNS... */
    SEM_CONEXAO,

    /** Qualquer outra falha não prevista. */
    INESPERADO,

    /** A página não tinha a lista de cartões (layout mudou?). */
    CARTOES_NAO_ENCONTRADOS,

    /** A conta não tem nenhum cartão. */
    SEM_CARTAO,

    /** A página do cartão não tinha o saldo (layout mudou?). */
    SALDO_NAO_ENCONTRADO,

    /** Não achamos o identificador do usuário exigido pelo extrato. */
    USUARIO_DO_EXTRATO_NAO_ENCONTRADO,

    /** A resposta do extrato não tinha a tabela de movimentações. */
    EXTRATO_NAO_ENCONTRADO,
}

data class ErroConsulta(
    val tipo: TipoErro,
    val codigoHttp: Int? = null,
)

/**
 * Resultado da consulta de saldo, de forma explícita, para a camada de UI
 * tratar sucesso e erro sem exceções soltas.
 */
sealed interface ResultadoConsulta {
    /**
     * @param cartoes todos os cartões da conta (para o seletor da tela).
     * @param cartaoConsultado o cartão cujo saldo foi lido.
     */
    data class Sucesso(
        val saldo: SaldoCartao,
        val cartoes: List<CartaoOpcao> = emptyList(),
        val cartaoConsultado: CartaoOpcao? = null,
    ) : ResultadoConsulta

    data class Erro(val erro: ErroConsulta) : ResultadoConsulta {
        constructor(tipo: TipoErro, codigoHttp: Int? = null) : this(ErroConsulta(tipo, codigoHttp))
    }
}

/**
 * Uma linha do extrato do cartão.
 *
 * Valores em centavos; [valorCentavos] é negativo para débitos ("- 6,00").
 * [saldoAgendadoCentavos] é null quando o SBE mostra "--".
 */
data class Movimentacao(
    val dataHora: String,
    val numeroSerie: String,
    val operacao: String,
    val linhaCodigo: String,
    val linhaNome: String?,
    val trecho: String,
    val veiculo: String,
    val empresa: String?,
    val valorCentavos: Long,
    val saldoCartaoCentavos: Long?,
    val saldoAgendadoCentavos: Long?,
)

sealed interface ResultadoExtrato {
    data class Sucesso(val movimentacoes: List<Movimentacao>) : ResultadoExtrato
    data class Erro(val erro: ErroConsulta) : ResultadoExtrato {
        constructor(tipo: TipoErro, codigoHttp: Int? = null) : this(ErroConsulta(tipo, codigoHttp))
    }
}
