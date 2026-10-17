package br.com.cartaourbs.data.model

import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * Um mês de um ano, usado como período do extrato.
 *
 * Feito "na mão" (sem java.time) porque o minSdk é 24 e o java.time só existe
 * a partir do Android 8 (API 26) sem desugaring.
 *
 * @param mes 1 a 12.
 */
data class MesAno(val ano: Int, val mes: Int) {

    init {
        require(mes in 1..12) { "mês inválido: $mes" }
    }

    fun anterior(): MesAno = if (mes == 1) MesAno(ano - 1, 12) else MesAno(ano, mes - 1)

    fun seguinte(): MesAno = if (mes == 12) MesAno(ano + 1, 1) else MesAno(ano, mes + 1)

    val ultimoDia: Int
        get() = when (mes) {
            2 -> if (bissexto) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    private val bissexto: Boolean
        get() = (ano % 4 == 0 && ano % 100 != 0) || ano % 400 == 0

    /** Primeiro dia no formato que o SBE espera: "01/10/2026". */
    val dataInicial: String get() = "01/%02d/%04d".format(mes, ano)

    /** Último dia no formato que o SBE espera: "31/10/2026". */
    val dataFinal: String get() = "%02d/%02d/%04d".format(ultimoDia, mes, ano)

    /** Ex.: "outubro de 2026". */
    fun nomeCompleto(locale: Locale = Locale.forLanguageTag("pt-BR")): String {
        val nomeMes = DateFormatSymbols(locale).months[mes - 1]
        return "$nomeMes de $ano"
    }

    operator fun compareTo(outro: MesAno): Int =
        compareValuesBy(this, outro, MesAno::ano, MesAno::mes)

    companion object {
        fun atual(calendario: Calendar = Calendar.getInstance()): MesAno =
            MesAno(calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH) + 1)
    }
}
