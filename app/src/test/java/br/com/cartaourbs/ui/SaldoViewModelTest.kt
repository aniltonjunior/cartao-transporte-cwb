package br.com.cartaourbs.ui

import br.com.cartaourbs.data.ArmazenamentoCredenciais
import br.com.cartaourbs.data.ConsultaSbe
import br.com.cartaourbs.data.model.CartaoOpcao
import br.com.cartaourbs.data.model.Credenciais
import br.com.cartaourbs.data.model.MesAno
import br.com.cartaourbs.data.model.Movimentacao
import br.com.cartaourbs.data.model.ResultadoConsulta
import br.com.cartaourbs.data.model.ResultadoExtrato
import br.com.cartaourbs.data.model.SaldoCartao
import br.com.cartaourbs.data.model.TipoErro
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaldoViewModelTest {

    private val cpfValido = "12345678909"
    private val outubro = MesAno(2026, 10)

    private val cartaoA = CartaoOpcao("A1", "0001", "0001 - Usuário - Ativo", ativo = true)
    private val cartaoB = CartaoOpcao("B2", "0002", "0002 - Usuário - Bloqueado", ativo = false)

    private fun saldo(centavos: Long, numero: String) =
        SaldoCartao(numeroCartao = numero, saldoCentavos = centavos, titular = "Fulano", atualizadoEm = null)

    // ---- Fakes -------------------------------------------------------------

    private class FakeConsulta : ConsultaSbe {
        val pedidosSaldo = mutableListOf<Pair<Credenciais, String?>>()
        val pedidosExtrato = mutableListOf<Triple<Credenciais, String?, MesAno>>()
        var respostaSaldo: (String?) -> ResultadoConsulta = { error("não configurado") }
        var respostaExtrato: ResultadoExtrato = ResultadoExtrato.Sucesso(emptyList())

        override suspend fun consultarSaldo(credenciais: Credenciais, cartaoId: String?): ResultadoConsulta {
            pedidosSaldo += credenciais to cartaoId
            return respostaSaldo(cartaoId)
        }

        override suspend fun consultarExtrato(
            credenciais: Credenciais, cartaoId: String?, periodo: MesAno,
        ): ResultadoExtrato {
            pedidosExtrato += Triple(credenciais, cartaoId, periodo)
            return respostaExtrato
        }
    }

    private class FakeArmazenamento(
        private val carregamento: CompletableDeferred<Credenciais?> = CompletableDeferred(null),
    ) : ArmazenamentoCredenciais {
        var salvas: Credenciais? = null
        var limpou = false

        override suspend fun salvar(credenciais: Credenciais) { salvas = credenciais }
        override suspend fun carregar(): Credenciais? = carregamento.await()
        override suspend fun limpar() { limpou = true; salvas = null }
    }

    private val consulta = FakeConsulta().apply {
        respostaSaldo = { id ->
            when (id) {
                "B2" -> ResultadoConsulta.Sucesso(saldo(300, "0002"), listOf(cartaoA, cartaoB), cartaoB)
                else -> ResultadoConsulta.Sucesso(saldo(5100, "0001"), listOf(cartaoA, cartaoB), cartaoA)
            }
        }
    }

    private fun vm(armazenamento: ArmazenamentoCredenciais = FakeArmazenamento()) =
        SaldoViewModel(consulta, armazenamento, mesAtual = { outubro })

    private fun SaldoViewModel.preencherEConsultar(cpf: String = cpfValido, senha: String = "s3nha") {
        onCpfChange(cpf)
        onSenhaChange(senha)
        consultar()
    }

    @Before
    fun prepara() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun encerra() = Dispatchers.resetMain()

    // ---- Credenciais salvas ------------------------------------------------

    @Test
    fun `carrega as credenciais salvas ao abrir`() = runTest {
        val armazenamento = FakeArmazenamento(CompletableDeferred(Credenciais(cpfValido, "salva")))
        val estado = vm(armazenamento).uiState.value
        assertEquals(cpfValido, estado.cpf)
        assertEquals("salva", estado.senha)
    }

    @Test
    fun `nao sobrescreve o que o usuario ja digitou`() = runTest {
        val carregamento = CompletableDeferred<Credenciais?>()
        val vm = vm(FakeArmazenamento(carregamento))

        vm.onCpfChange("111")
        carregamento.complete(Credenciais(cpfValido, "salva"))

        assertEquals("111", vm.uiState.value.cpf)
        assertEquals("", vm.uiState.value.senha)
    }

    @Test
    fun `lembrar salva as credenciais`() = runTest {
        val armazenamento = FakeArmazenamento()
        vm(armazenamento).preencherEConsultar()
        assertEquals(Credenciais(cpfValido, "s3nha"), armazenamento.salvas)
    }

    @Test
    fun `nao lembrar apaga as credenciais`() = runTest {
        val armazenamento = FakeArmazenamento()
        val vm = vm(armazenamento)
        vm.onLembrarChange(false)
        vm.preencherEConsultar()
        assertTrue(armazenamento.limpou)
        assertNull(armazenamento.salvas)
    }

    // ---- Campos e validação ------------------------------------------------

    @Test
    fun `CPF guarda so digitos ate 11`() = runTest {
        val vm = vm()
        vm.onCpfChange("123.456.789-0999")
        assertEquals(cpfValido, vm.uiState.value.cpf)
    }

    @Test
    fun `campos vazios mostram erro e nao consultam`() = runTest {
        val vm = vm()
        vm.consultar()
        assertEquals(ErroCampo.OBRIGATORIO, vm.uiState.value.erroCpf)
        assertEquals(ErroCampo.OBRIGATORIO, vm.uiState.value.erroSenha)
        assertTrue(consulta.pedidosSaldo.isEmpty())
    }

    @Test
    fun `CPF invalido mostra erro e nao consulta`() = runTest {
        val vm = vm()
        vm.preencherEConsultar(cpf = "12345678900")
        assertEquals(ErroCampo.CPF_INVALIDO, vm.uiState.value.erroCpf)
        assertNull(vm.uiState.value.erroSenha)
        assertTrue(consulta.pedidosSaldo.isEmpty())
    }

    @Test
    fun `digitar de novo apaga o erro do campo`() = runTest {
        val vm = vm()
        vm.consultar()
        vm.onCpfChange("1")
        assertNull(vm.uiState.value.erroCpf)
    }

    // ---- Saldo e cartões ---------------------------------------------------

    @Test
    fun `consulta com sucesso mostra saldo e cartoes`() = runTest {
        val vm = vm()
        vm.preencherEConsultar()

        val e = vm.uiState.value
        assertFalse(e.carregando)
        assertEquals(5100L, e.saldo?.saldoCentavos)
        assertEquals(listOf(cartaoA, cartaoB), e.cartoes)
        assertEquals(cartaoA, e.cartaoSelecionado)
        assertEquals(Credenciais(cpfValido, "s3nha") to null, consulta.pedidosSaldo.single())
    }

    @Test
    fun `erro da consulta aparece no estado`() = runTest {
        consulta.respostaSaldo = { ResultadoConsulta.Erro(TipoErro.CREDENCIAIS_INVALIDAS) }
        val vm = vm()
        vm.preencherEConsultar()

        assertEquals(TipoErro.CREDENCIAIS_INVALIDAS, vm.uiState.value.erro?.tipo)
        assertNull(vm.uiState.value.saldo)
        assertFalse(vm.uiState.value.carregando)
    }

    @Test
    fun `trocar de cartao consulta o novo usando as credenciais da consulta anterior`() = runTest {
        val vm = vm()
        vm.preencherEConsultar()
        vm.onSenhaChange("editada-depois") // não deve afetar a troca de cartão

        vm.selecionarCartao("B2")

        assertEquals(Credenciais(cpfValido, "s3nha") to "B2", consulta.pedidosSaldo.last())
        assertEquals(300L, vm.uiState.value.saldo?.saldoCentavos)
        assertEquals(cartaoB, vm.uiState.value.cartaoSelecionado)
    }

    @Test
    fun `escolher o cartao que ja esta selecionado nao consulta de novo`() = runTest {
        val vm = vm()
        vm.preencherEConsultar()
        vm.selecionarCartao("A1")
        assertEquals(1, consulta.pedidosSaldo.size)
    }

    // ---- Extrato -----------------------------------------------------------

    private val movimentacao = Movimentacao(
        dataHora = "01/10/2026 08:00:00", numeroSerie = "1", operacao = "Débito",
        linhaCodigo = "001", linhaNome = null, trecho = "PADRAO", veiculo = "1",
        empresa = null, valorCentavos = -600, saldoCartaoCentavos = 4500, saldoAgendadoCentavos = null,
    )

    @Test
    fun `extrato so abre depois de uma consulta`() = runTest {
        val vm = vm()
        vm.abrirExtrato()
        assertEquals(Tela.SALDO, vm.uiState.value.tela)
        assertTrue(consulta.pedidosExtrato.isEmpty())
    }

    @Test
    fun `abrir extrato busca o mes atual do cartao selecionado`() = runTest {
        consulta.respostaExtrato = ResultadoExtrato.Sucesso(listOf(movimentacao))
        val vm = vm()
        vm.preencherEConsultar()
        vm.selecionarCartao("B2")

        vm.abrirExtrato()

        val e = vm.uiState.value
        assertEquals(Tela.EXTRATO, e.tela)
        assertEquals(Triple(Credenciais(cpfValido, "s3nha"), "B2", outubro), consulta.pedidosExtrato.single())
        assertEquals(listOf(movimentacao), e.extrato.movimentacoes)
        assertFalse(e.extrato.podeAvancar)
    }

    @Test
    fun `nao avanca para alem do mes atual`() = runTest {
        val vm = vm()
        vm.preencherEConsultar()
        vm.abrirExtrato()

        vm.mesSeguinte()

        assertEquals(outubro, vm.uiState.value.extrato.periodo)
        assertEquals(1, consulta.pedidosExtrato.size)
    }

    @Test
    fun `mes anterior e depois o seguinte`() = runTest {
        val vm = vm()
        vm.preencherEConsultar()
        vm.abrirExtrato()

        vm.mesAnterior()
        assertEquals(MesAno(2026, 9), vm.uiState.value.extrato.periodo)
        assertTrue(vm.uiState.value.extrato.podeAvancar)
        assertEquals(MesAno(2026, 9), consulta.pedidosExtrato.last().third)

        vm.mesSeguinte()
        assertEquals(outubro, vm.uiState.value.extrato.periodo)
        assertFalse(vm.uiState.value.extrato.podeAvancar)
    }

    @Test
    fun `erro do extrato e tentar novamente`() = runTest {
        consulta.respostaExtrato = ResultadoExtrato.Erro(TipoErro.USUARIO_DO_EXTRATO_NAO_ENCONTRADO)
        val vm = vm()
        vm.preencherEConsultar()
        vm.abrirExtrato()
        assertEquals(TipoErro.USUARIO_DO_EXTRATO_NAO_ENCONTRADO, vm.uiState.value.extrato.erro?.tipo)

        consulta.respostaExtrato = ResultadoExtrato.Sucesso(listOf(movimentacao))
        vm.tentarExtratoNovamente()
        assertNull(vm.uiState.value.extrato.erro)
        assertEquals(1, vm.uiState.value.extrato.movimentacoes?.size)
    }

    @Test
    fun `voltar retorna para o saldo e reabrir nao busca de novo`() = runTest {
        val vm = vm()
        vm.preencherEConsultar()
        vm.abrirExtrato()
        vm.voltar()
        assertEquals(Tela.SALDO, vm.uiState.value.tela)

        vm.abrirExtrato()
        assertEquals(1, consulta.pedidosExtrato.size)
    }

    @Test
    fun `trocar de cartao descarta o extrato antigo`() = runTest {
        consulta.respostaExtrato = ResultadoExtrato.Sucesso(listOf(movimentacao))
        val vm = vm()
        vm.preencherEConsultar()
        vm.abrirExtrato()
        vm.voltar()

        vm.selecionarCartao("B2")

        assertNull(vm.uiState.value.extrato.movimentacoes)
    }
}
