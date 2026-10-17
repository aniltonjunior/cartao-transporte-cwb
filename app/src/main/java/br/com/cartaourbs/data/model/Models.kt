package br.com.cartaourbs.data.model

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
    /** Saldo formatado em reais, ex.: "R$ 12,34". */
    val saldoFormatado: String
        get() {
            val reais = saldoCentavos / 100
            val centavos = saldoCentavos % 100
            return "R$ %d,%02d".format(reais, centavos)
        }
}

/**
 * Representa o resultado de uma operação que pode falhar, de forma explícita,
 * para a camada de UI tratar sucesso e erro sem exceções soltas.
 */
sealed interface ResultadoConsulta {
    data class Sucesso(val saldo: SaldoCartao) : ResultadoConsulta
    data class Erro(val mensagem: String) : ResultadoConsulta
}
