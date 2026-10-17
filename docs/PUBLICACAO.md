# Publicação na Google Play

Passo a passo para publicar o **Saldo Transporte CWB**. A parte técnica do
projeto já está pronta; aqui ficam as etapas que dependem de você.

Comandos de terminal assumem a raiz do projeto e o JDK do Android Studio:

```bash
export JAVA_HOME="$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr"
```

## 0. Já configurado no projeto

- `targetSdk`/`compileSdk` 36 (exigido para apps novos desde 31/08/2026).
- Build de release com R8 (código e recursos não usados removidos).
- Assinatura lida de `keystore.properties`, fora do git.
- Nome **Saldo Transporte CWB**, ícone próprio e `applicationId`
  `br.com.saldotransportecwb`, sem "URBS".
- Aviso de app não oficial na tela inicial e tela **Sobre** com fonte dos
  dados, resumo de privacidade e licenças.
- Imagens da loja em `docs/loja/` e rascunho da política de privacidade em
  `docs/politica-de-privacidade.md`.

> **Decida o `applicationId` agora.** Ele vira o endereço do app na loja
> (`play.google.com/store/apps/details?id=...`) e **não pode mudar** depois do
> primeiro envio. Para trocar, edite `applicationId` em `app/build.gradle.kts`.

## 1. Criar a chave de assinatura (upload key)

A chave prova que as atualizações são suas. Com o **Play App Signing** (padrão
da Play Store), o Google guarda a chave final do app e você só usa esta
"chave de upload": se perdê-la, dá para pedir a troca ao suporte, mas o
processo é demorado. Guarde-a com cuidado.

1. Crie a pasta fora do projeto e gere a chave (o comando pergunta as senhas e
   seus dados):

   ```bash
   mkdir -p ~/chaves-android
   "$JAVA_HOME/bin/keytool" -genkeypair -v \
     -keystore ~/chaves-android/saldo-transporte-upload.jks \
     -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Crie `keystore.properties` na **raiz do projeto** (já está no `.gitignore`):

   ```properties
   storeFile=/home/SEU-USUARIO/chaves-android/saldo-transporte-upload.jks
   storePassword=senha-do-arquivo
   keyAlias=upload
   keyPassword=senha-da-chave
   ```

3. Faça **backup** do `.jks` e das senhas num gerenciador de senhas ou num
   lugar seguro fora do computador. Nunca envie esses arquivos ao git nem os
   compartilhe.

## 2. Publicar a política de privacidade

1. Revise `docs/politica-de-privacidade.md` e preencha os campos entre
   `[colchetes]` (nome, e-mail, data).
2. Publique num endereço público. Uma opção gratuita é o GitHub Pages:
   - crie um repositório **público** (pode ser só para a política, se não
     quiser abrir o código) e envie o arquivo;
   - em *Settings > Pages*, publique a partir da branch principal;
   - o endereço fica como
     `https://SEU-USUARIO.github.io/NOME-DO-REPO/politica-de-privacidade`.
3. Coloque o endereço e o e-mail em `gradle.properties` (as linhas já estão
   lá, comentadas):

   ```properties
   urlPoliticaPrivacidade=https://SEU-USUARIO.github.io/NOME-DO-REPO/politica-de-privacidade
   emailContato=seu-email@exemplo.com
   ```

   Eles passam a aparecer na tela **Sobre** do app.

## 3. Gerar e testar o release

1. Teste o build de release **no seu celular** antes de enviar: o R8 só age
   nele, e um problema de regras aparece só aqui.

   ```bash
   ./gradlew :app:assembleRelease
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

   O `applicationId` mudou, então ele instala como um app novo, ao lado da
   versão de desenvolvimento. Confira login, saldo, troca de cartão, extrato,
   "Lembrar meus dados" e a tela Sobre.

2. Gere o pacote para a loja (AAB):

   ```bash
   ./gradlew :app:bundleRelease
   ```

   Arquivo: `app/build/outputs/bundle/release/app-release.aab`.

3. A cada nova versão enviada, aumente `versionCode` (1, 2, 3...) e ajuste
   `versionName` em `app/build.gradle.kts`.

## 4. Conta de desenvolvedor

1. Crie a conta no [Play Console](https://play.google.com/console): taxa única
   de US$ 25 e verificação de identidade com documento.
2. Contas pessoais criadas depois de 13/11/2023 precisam de um **teste fechado
   com pelo menos 12 testadores** inscritos por **14 dias seguidos** antes de
   liberar a produção. Comece a juntar testadores (amigos, família) cedo.

## 5. Criar o app no Play Console

- **Nome:** Saldo Transporte CWB
- **Idioma padrão:** Português (Brasil)
- **Tipo:** App, **Gratuito**

### Ficha da loja

- **Descrição curta** (até 80 caracteres):

  > Saldo e extrato do cartão transporte de Curitiba. App não oficial.

- **Descrição completa** (sugestão):

  > Consulte o saldo e o extrato do seu cartão transporte de Curitiba de forma
  > rápida, com o mesmo CPF e senha que você usa no site do SBE.
  >
  > • Saldo atual do cartão
  > • Extrato mês a mês, com as linhas usadas
  > • Escolha entre os cartões da sua conta
  > • Opção de lembrar seus dados, guardados cifrados só no seu aparelho
  > • Tema claro e escuro
  >
  > APP NÃO OFICIAL: este aplicativo é independente e não tem vínculo com a
  > URBS (Urbanização de Curitiba S.A.), com a Prefeitura de Curitiba nem com
  > nenhum órgão de governo.
  >
  > Fonte dos dados: Sistema de Bilhetagem Eletrônica (SBE) da URBS —
  > https://sbe.curitiba.pr.gov.br. Em caso de dúvida, confira no site oficial:
  > https://www.urbs.curitiba.pr.gov.br/portal/cartao-transporte/
  >
  > Privacidade: seus dados vão apenas para o site oficial do SBE. O app não
  > tem servidor próprio, anúncios nem rastreamento.

- **Ícone:** `docs/loja/icone-512.png`
- **Imagem de destaque:** `docs/loja/banner-1024x500.png`
- **Capturas de tela:** tire de 2 a 8 no celular (tela inicial, saldo,
  extrato, Sobre). **Esconda ou troque o CPF e o nome** antes de usar, ou use
  o modo mock (README > Modo MOCK) para gerar telas com dados fictícios.
- **Categoria:** Mapas e navegação (ou Viagens e local).
- **E-mail de contato** e **política de privacidade** (endereço do passo 2).

Para gerar as imagens de novo depois de editar os SVGs:
`sh docs/loja/gerar-imagens.sh` (precisa do `rsvg-convert`).

### Conteúdo do app (Política e programas > Conteúdo do app)

Respostas sugeridas para o comportamento atual. Confira as definições de cada
formulário no próprio Play Console antes de enviar.

- **Política de privacidade:** o endereço do passo 2.
- **Anúncios:** não contém anúncios.
- **Acesso ao app:** o app exige login no SBE. Informe que é preciso um
  cadastro no SBE da URBS e, se possível, forneça uma conta de teste para a
  revisão. Sem conta, explique que o revisor pode ver a tela inicial, a
  validação de CPF e a tela Sobre.
- **Classificação do conteúdo:** responda o questionário (app utilitário,
  sem violência, sem interação entre usuários). Deve resultar em "Livre".
- **Público-alvo:** 13 anos ou mais. Não inclua menores de 13 anos, para não
  entrar nas regras do programa Famílias.
- **Apps de governo:** **não**, o app não é desenvolvido por um órgão de
  governo nem em nome dele.
- **Recursos financeiros:** o app não oferece serviços financeiros (só mostra o
  saldo do cartão transporte).
- **Segurança dos dados** (sugestão):
  - Coleta dados? **Sim**: o CPF e a senha saem do aparelho para o login no
    SBE. Para a Play Store, enviar dados para fora do aparelho já conta como
    "coleta", mesmo sem servidor próprio.
  - Tipos: **Informações pessoais > Outras informações** (CPF) e o que o
    formulário indicar para credenciais de login.
  - Finalidade: **Funcionalidade do app**. Obrigatório: **sim**.
  - Processamento: **efêmero** (usado só durante a consulta).
  - Compartilhamento: **não**. O envio ao SBE é feito a pedido do usuário,
    para o serviço que ele espera usar.
  - Criptografados em trânsito: **sim** (HTTPS).
  - Exclusão: o usuário apaga os dados guardados desmarcando "Lembrar meus
    dados" ou desinstalando o app. O desenvolvedor não guarda dados.
- **Exclusão de conta:** não se aplica, porque o app não cria contas.

## 6. Testes e lançamento

1. **Teste interno** (opcional, sem revisão demorada): envie o AAB e instale
   pelo link de teste para conferir a versão da loja.
2. **Teste fechado:** crie a faixa, adicione os 12+ testadores (por e-mail ou
   Grupo do Google), envie o AAB e compartilhe o link de inscrição. Mantenha
   todos inscritos por 14 dias seguidos e corrija o que aparecer.
3. **Acesso à produção:** depois dos 14 dias, peça o acesso no Play Console e
   responda o questionário sobre o teste.
4. **Produção:** crie a versão de produção com o mesmo AAB (ou um mais novo) e
   envie para revisão.

## 7. Antes de cada envio

- [ ] `versionCode` aumentado.
- [ ] `./gradlew :app:testDebugUnitTest` passando.
- [ ] Release testado no aparelho (passo 3).
- [ ] Política de privacidade e tela Sobre coerentes com o que o app faz.
- [ ] Nada de dados pessoais reais em capturas de tela, código ou testes.

## Distribuir sem a Play Store

Para usar só com família e amigos, a conta de **distribuição limitada** do
Android Developer Console é gratuita, não pede documento e permite distribuir
para até 20 aparelhos. No seu próprio celular, instalar pelo Android Studio ou
por `adb install` continua funcionando sem cadastro.
