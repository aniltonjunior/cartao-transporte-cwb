# Cartão URBS — consulta de saldo

App Android (Kotlin + Jetpack Compose) para consultar o saldo do Cartão
Transporte da URBS (Curitiba).

> Projeto de aprendizado. A URBS não oferece API pública oficial, então o app
> reproduz o acesso que o site do SBE (Sistema de Bilhetagem Eletrônica) faz.

## Como abrir e rodar

1. Abra o **Android Studio** (versão recente, Giraffe ou mais nova).
2. `File > Open` e selecione esta pasta (`Cartao Urbs`).
3. Na primeira vez, o Android Studio vai:
   - Gerar o **Gradle wrapper** (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`)
     automaticamente, se ainda não existirem.
   - Baixar as dependências e o Android SDK necessário.
4. Conecte um celular (com depuração USB) ou crie um emulador.
5. Clique em **Run** (▶). O app instala e abre.

Requisitos: Android Studio com JDK 17 e Android SDK 34 instalados (o próprio
Studio instala). O `minSdk` é 24 (Android 7.0+).

## Estado atual: integração REAL ligada

O app já consulta o SBE de verdade. Ao informar usuário (CPF) e senha e tocar
em "Consultar saldo", ele:

1. Faz login: `POST /sbe-web/login/login.html` (form url-encoded com
   `nomeUsuario`, `senha`, `resolucaoWidth`, `resolucaoHeight`, `x`, `y`).
   O SBE responde 302 e cria a sessão (cookie), que o OkHttp guarda.
2. Busca os dados do cartão:
   `POST /sbe-web/usuario/consultarInformacoesCartaoUsuario.html`.
   A resposta é um HTML com o saldo atual, o titular e a lista de cartões.
3. Extrai saldo, titular e número do cartão do HTML (ver `SaldoParser.kt`).

### Modo MOCK (opcional, para testar a UI sem rede)

Para desenvolver a tela sem bater no site, instancie o repositório com
`usarMock = true` em `app/.../data/SaldoRepository.kt`:

```kotlin
class SaldoRepository(
    private val api: SbeApi = SbeClient.create(),
    private val usarMock: Boolean = true, // devolve saldo fake
)
```

### Se a consulta parar de funcionar

Por não ser API oficial, o SBE pode mudar o HTML. Nesse caso, ajuste:

- `app/.../data/remote/SbeApi.kt` → URLs dos endpoints.
- `app/.../data/remote/SaldoParser.kt` → como o saldo/titular são localizados
  no HTML.

## Testes

Testes unitários JVM (rodam sem emulador), em `app/src/test/`:

- `SaldoParserTest` — extração de saldo (ignorando o agendado R$ 0,00),
  titular e número do cartão; valores com milhar; ausência de valor.
- `FormularioParserTest` — extração do CPF e da lista de cartões; escolha do
  cartão ativo (`cartaoPreferido`); options sem value.
- `SaldoCartaoTest` — formatação do saldo em reais.
- `SaldoRepositoryTest` — fluxo completo com um `SbeApi` falso, incluindo a
  regressão do bug de sessão (CPFs diferentes devem retornar saldos
  diferentes), login inválido, falha de autenticação e modo mock.

Rodar:

```bash
./gradlew :app:testDebugUnitTest
```

Pelo Android Studio: clique com o botão direito na pasta `test` e em "Run Tests".

## Arquitetura (resumo)

```
ui/
  MainActivity.kt      -> ponto de entrada (Compose)
  SaldoScreen.kt       -> tela: login + exibição do saldo
  SaldoViewModel.kt    -> estado da tela (MVVM) + chamadas ao repositório
data/
  SaldoRepository.kt   -> decide entre mock e real; fonte única de verdade
  CredenciaisStore.kt  -> guarda usuário/senha CRIPTOGRAFADOS no aparelho
  model/Models.kt      -> modelos (Credenciais, SaldoCartao, ResultadoConsulta)
  remote/
    SbeClient.kt       -> OkHttp/Retrofit + cookie de sessão + User-Agent
    SbeApi.kt          -> endpoints HTTP do SBE (login, extrato)
    SaldoParser.kt     -> extrai o saldo do HTML com Jsoup
```

Fluxo: a tela chama o ViewModel, que chama o repositório. No modo real, o
repositório faz login (o cookie de sessão é guardado automaticamente), busca o
extrato e o parser extrai o saldo do HTML.

## Segurança das credenciais

- As credenciais são digitadas por você no app e guardadas com
  `EncryptedSharedPreferences` (criptografia com chave do Android Keystore).
- **Nunca** há usuário/senha escritos no código-fonte.
- Se você desmarcar "Lembrar meus dados", nada é persistido.

## Aviso

Por não usar uma API oficial, se a URBS mudar o site do SBE a integração pode
quebrar e precisar de ajuste no `SbeApi.kt` / `SaldoParser.kt`.
