# Acompanhamento Nutricional (aplicativo Android)

Aplicativo de nutrição clínica esportiva do Dr. Rafael Assef para celular e tablet Android: cadastro de pacientes, anamnese, antropometria, bioimpedância e evolução.

- Ícone: monograma "RA" no verde do logo, sobre fundo branco (inclui ícone adaptativo e ícone temático do Android 13+).
- Funciona sem internet. A internet só é usada para carregar as fontes do texto; sem ela, o app usa as fontes do aparelho.
- Os dados dos pacientes ficam **somente no aparelho**, num arquivo privado do app.
- Requisitos: Android 7.0 ou mais novo. Feito para Android 16 (API 36), que é o exigido pela Play Store desde 31/08/2026.

---

## Jeito 1 — Gerar o APK pelo GitHub (sem instalar nada no computador)

O projeto já vem configurado para o GitHub compilar o aplicativo sozinho.

1. Crie uma conta gratuita em **github.com**.
2. Crie um repositório novo e marque como **Private** (privado). Isso é importante, porque o projeto contém a sua chave de assinatura.
3. Envie todos os arquivos desta pasta para o repositório, incluindo a pasta `.github`. No computador, use **Add file → Upload files** e arraste o conteúdo da pasta.
4. Abra a aba **Actions** do repositório. A compilação "Gerar APK" começa sozinha e leva cerca de 5 minutos.
5. Ao terminar, abra **Releases** (na página inicial do repositório, à direita). Lá estará o arquivo `AcompanhamentoNutricional-N.apk`.

Cada vez que você enviar uma alteração, o GitHub gera uma versão nova, com número maior, que instala por cima da anterior sem apagar os dados.

## Jeito 2 — Android Studio (computador)

1. Instale o **Android Studio** (gratuito): developer.android.com/studio
2. **File → Open** e escolha esta pasta. Aguarde a sincronização terminar; na primeira vez ela baixa vários componentes.
3. Menu **Build → Generate App Bundles or APKs → Generate APKs**.
4. O arquivo fica em `app/build/outputs/apk/release/app-release.apk`.

Pelo terminal, também funciona: `./gradlew assembleRelease`.

## Instalar o APK no celular ou no tablet

1. Abra o arquivo `.apk` no aparelho (baixado do GitHub, recebido pelo WhatsApp, Google Drive etc.).
2. Se aparecer o aviso de "fontes desconhecidas", toque em **Configurações** e ative **Permitir desta fonte**.
3. Toque em **Instalar**. O ícone "RA" aparece na tela inicial.

---

## Seus dados

- **Onde ficam:** num arquivo privado do app, no aparelho. Se você ativar o backup do Google no Android (Configurações → Google → Backup), o próprio Android faz cópia dos dados do app e os restaura num aparelho novo.
- **Backup manual:** na lista de pacientes, toque em **Exportar backup** e salve o arquivo no Google Drive. Para restaurar, use **Importar backup**.
- **Trazer os pacientes da versão do claude.ai:** abra a versão web, toque em **Exportar backup**, depois abra o aplicativo e toque em **Importar backup**.
- **Celular e tablet não sincronizam sozinhos.** Para levar pacientes de um para o outro, use Exportar e Importar backup.
- Desinstalar o app apaga os dados do aparelho. Exporte um backup antes.

## Assinatura do aplicativo — guarde com cuidado

A pasta `assinatura/` e o arquivo `keystore.properties` são a "assinatura digital" do app.

- Toda atualização precisa ser assinada com **essa mesma chave**. Sem ela, o Android não instala a versão nova por cima e seria preciso desinstalar, perdendo os dados.
- Guarde uma cópia do projeto inteiro num lugar seguro (por exemplo, no Google Drive).
- **Não publique esses arquivos** em repositório público.

## Publicar na Play Store (opcional)

1. Crie uma conta de desenvolvedor no **Google Play Console** (taxa única de US$ 25).
2. Envie o arquivo `.aab`, gerado junto com o APK no GitHub ou com `./gradlew bundleRelease`.
3. Use as imagens da pasta `play-store/`: ícone 512×512 e imagem de destaque 1024×500.
4. A Play Store exige uma **política de privacidade** (uma página simples dizendo que os dados ficam no aparelho e não são enviados a terceiros) e o preenchimento da seção "Segurança dos dados".

## Estrutura

```
app/src/main/assets/index.html     interface do app (a mesma da versão web)
app/src/main/java/.../MainActivity.java   abre a interface e grava os dados no aparelho
app/src/main/res/mipmap-*          ícones em todos os tamanhos
play-store/                        ícone e imagem de destaque para a loja
.github/workflows/gerar-apk.yml    compilação automática no GitHub
```

Identificador do app: `br.com.rafaelassef.acompanhamento`
