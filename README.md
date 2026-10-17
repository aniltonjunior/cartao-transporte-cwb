# Cartão URBS — consulta de saldo

App Android (Kotlin + Jetpack Compose) para consultar o saldo e o extrato do
Cartão Transporte da URBS (Curitiba).

> Projeto de aprendizado. A URBS não oferece API pública oficial, então o app
> reproduz o acesso que o site do SBE (Sistema de Bilhetagem Eletrônica) faz.

## O que o app faz

- Consulta o saldo atual do cartão com CPF e senha do SBE.
- Valida o CPF (dígitos verificadores) e mostra a máscara `000.000.000-00`.
- Lembra as credenciais no aparelho, cifradas (opcional).
- Se a conta tem mais de um cartão, permite escolher qual consultar.
- Mostra o extrato do cartão mês a mês (o mais recente primeiro).
- Tema claro/escuro automático e cores dinâmicas no Android 12+.

## Como abrir e rodar

1. Abra o **Android Studio** (versão recente).
2. `File > Open` e selecione esta pasta (`Cartao Urbs`).
3. Na primeira vez, o Android Studio baixa as dependências e o Android SDK.
4. Conecte um celular (com depuração USB) ou crie um emulador.
5. Clique em **Run** (▶). O app instala e abre.

Versões: Gradle 9.0, Android Gradle Plugin 8.13, Kotlin 2.1, `compileSdk` 35,
`minSdk` 24 (Android 7.0+). O Gradle precisa rodar com JDK 17 a 24; o JDK que
vem com o Android Studio serve.

## Como funciona a consulta

Cada consulta (inclusive trocar de cartão ou de mês no extrato) refaz o fluxo
inteiro, sempre com uma sessão nova:

1. Login: `POST /sbe-web/login/login.html` (form com `nomeUsuario`, `senha`,
   `resolucaoWidth`, `resolucaoHeight`, `x`, `y`). Com senha certa, os
   redirects terminam em `central/principal.html`; com senha errada, o SBE
   volta para a tela de login com HTTP 200.
2. `GET /sbe-web/central/principal.html` ("aquece" a sessão, como o navegador).
3. `GET /sbe-web/usuario/consultarInformacoesCartaoUsuario.html`: formulário
   com o CPF e a lista de cartões.
4. `POST` na mesma URL com `documentoNacional` + `cartao`: página com o saldo.
5. Só no extrato: `POST /sbe-web/v2/usuario/consultarInformacoesCartaoUsuarioContaCorrente.html`
   com `filtro.idUsuario`, `filtro.dataInicial` e `filtro.dataFinal`.

### Modo MOCK (opcional, para testar a UI sem rede)

Para desenvolver as telas sem bater no site, troque o repositório na `Factory`
de `ui/SaldoViewModel.kt`:

```kotlin
SaldoViewModel(
    consulta = SaldoRepository(usarMock = true), // saldo, cartões e extrato fake
    credenciaisStore = CredenciaisStore.criar(app),
)
```

### Se a consulta parar de funcionar

Por não ser API oficial, o SBE pode mudar o HTML. Os pontos de ajuste são:

- `data/remote/SbeApi.kt` → URLs dos endpoints.
- `data/remote/SaldoParser.kt` → como o saldo é localizado.
- `data/remote/FormularioCartao.kt` → CPF e lista de cartões.
- `data/remote/ExtratoParser.kt` → tabela do extrato e `idUsuario`.

No build de debug, o Logcat (tag `SaldoRepository`) mostra códigos HTTP,
exceções e onde o `idUsuario` foi encontrado, sem credenciais nem HTML.

## Verificar no aparelho

Partes que dependem de detalhes do site que os testes não alcançam:

- [ ] **Saldo atual × agendado**: o valor mostrado bate com o do site.
- [ ] **Senha errada** mostra "Usuário ou senha inválidos." (se aparecer
      "Não foi possível entrar no SBE…", o SBE não volta para a tela de login
      e a detecção precisa de ajuste).
- [ ] **Extrato**: abre e lista as viagens. Se aparecer "Não foi possível
      identificar o usuário para o extrato", veja no Logcat de debug a linha
      `idUsuario não encontrado` e ajuste `ExtratoParser.extrairIdUsuario`.
- [ ] **Troca de cartão** mostra o saldo do cartão escolhido.
- [ ] **Credenciais lembradas** depois de fechar e abrir o app (usa o
      Android Keystore, que só existe no aparelho).
- [ ] **Tema escuro**: sem flash branco ao abrir e textos legíveis.
- [ ] **TalkBack**: campos, erros e saldo são anunciados.

## Testes

Testes unitários JVM (rodam sem emulador), em `app/src/test/`:

- `SaldoParserTest` — saldo atual × agendado (por rótulo e pela posição da
  página real), titular, número do cartão, milhar, ausência de valor.
- `FormularioParserTest` — CPF e lista de cartões; cartão ativo (e
  "Inativo" não conta); escolha por id com volta ao preferido.
- `ExtratoParserTest` — linhas do extrato (ignorando os totais do rodapé),
  campos, valores com sinal e "--", lista vazia e as formas de achar o
  `idUsuario`.
- `SaldoCartaoTest` — formatação em reais (milhar, negativo, espaço pt-BR).
- `MesAnoTest` — datas do período, bissextos, navegação, nome do mês.
- `CpfTest` — dígitos verificadores, limpeza, máscara e posição do cursor.
- `SessionCookieJarTest` — mescla de cookies, substituição, expiração,
  domínio, caminho, https e limpeza.
- `CredenciaisStoreTest` — salvar/carregar com DataStore e Tink reais, arquivo
  sem texto puro, chave perdida, limpar, Keystore indisponível.
- `SaldoRepositoryTest` — fluxo completo com um `SbeApi` falso: escolha de
  cartão, regressão do bug de sessão (CPFs diferentes), senha errada, sessão
  perdida, erro HTTP, falha de rede, cancelamento, extrato e modo mock.
- `SaldoViewModelTest` — validação dos campos, credenciais lembradas,
  consulta, troca de cartão e navegação/período do extrato, com fakes.

Rodar:

```bash
./gradlew :app:testDebugUnitTest
```

Pelo Android Studio: clique com o botão direito na pasta `test` e em "Run Tests".

## Arquitetura (resumo)

```
ui/
  MainActivity.kt          -> ponto de entrada; navegação simples por estado
  SaldoScreen.kt           -> tela de login + saldo + seletor de cartão
  ExtratoScreen.kt         -> tela do extrato, mês a mês
  SaldoViewModel.kt        -> estado das telas (MVVM) + Factory (injeção manual)
  Mensagens.kt             -> tipo de erro -> texto de strings.xml
  CpfVisualTransformation  -> máscara visual do CPF
  theme/                   -> cores, tipografia e tema claro/escuro/dinâmico
util/
  Cpf.kt                   -> validação, limpeza e máscara do CPF
data/
  SaldoRepository.kt       -> interface ConsultaSbe + implementação (real/mock)
  CredenciaisStore.kt      -> interface ArmazenamentoCredenciais + DataStore/Tink
  seguranca/Cifrador.kt    -> cifragem Tink com chave no Android Keystore
  model/Models.kt          -> Credenciais, SaldoCartao, CartaoOpcao, Movimentacao,
                              TipoErro, resultados e formatarReais
  model/MesAno.kt          -> período do extrato
  remote/
    SbeClient.kt           -> OkHttp/Retrofit + User-Agent; SbeSession
    SessionCookieJar.kt    -> cookies da sessão (mescla, expiração, limpar)
    SbeApi.kt              -> endpoints HTTP do SBE
    FormularioCartao.kt    -> lê CPF e lista de cartões do formulário
    SaldoParser.kt         -> extrai o saldo do HTML
    ExtratoParser.kt       -> extrai as movimentações e o idUsuario
```

- A tela chama o ViewModel, que depende só das interfaces `ConsultaSbe` e
  `ArmazenamentoCredenciais`. A `Factory` do ViewModel monta as
  implementações reais; os testes usam fakes.
- O repositório devolve erros tipados (`TipoErro`); o texto para o usuário fica
  em `res/values/strings.xml`. Todo texto visível está lá.

## Segurança das credenciais

- As credenciais são digitadas por você no app e guardadas com **Jetpack
  DataStore**. Cada campo é cifrado com AES-256-GCM via **Tink** antes de ir
  para o disco; a chave do Tink é protegida por uma chave mestra no
  **Android Keystore**, que nunca sai do aparelho.
- Se o dado salvo não puder ser decifrado (chave perdida, por exemplo), ele é
  apagado e o app pede usuário e senha de novo, em vez de travar. Sem Keystore
  funcional, nada é gravado.
- O backup do app está desligado, e as regras de backup e de transferência
  entre aparelhos (`res/xml/`) deixam as credenciais e a chave de fora.
- **Nunca** há usuário/senha escritos no código-fonte.
- Se você desmarcar "Lembrar meus dados", nada é persistido.
- O log de rede (OkHttp) só existe no build de debug e não mostra cabeçalhos.

## Limitações conhecidas

- **Sem logout no servidor.** O endpoint de saída do SBE não é conhecido, então
  cada consulta deixa uma sessão aberta, que expira sozinha. Para implementar,
  capture no navegador (F12 > Network) a requisição feita ao sair do SBE e
  siga o `TODO(logout)` em `data/remote/SbeApi.kt`.
- **`idUsuario` do extrato.** Ainda não confirmamos em que parte da página ele
  aparece; o app procura em campos, atributos e scripts (ver "Verificar no
  aparelho").
- Por não usar uma API oficial, se a URBS mudar o site do SBE a integração
  pode quebrar e precisar de ajuste nos arquivos de `data/remote/`.
