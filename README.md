# Testemunha

App Android para **registrar provas de forma rápida e segura** em situações de assédio moral, perseguição, ameaça ou abuso.
Áudio, vídeo e fotos com data, hora, local e uma "impressão digital" (SHA-256) que comprova que o arquivo não foi alterado.

Tudo fica **só no celular**: o app não tem permissão de acesso à internet.

## Funcionalidades

- **Gravar em 1 toque**: áudio, vídeo ou foto. Áudio e vídeo continuam com a tela apagada.
- **Câmera traseira, frontal ou as duas**: na foto, uma de cada; no vídeo, as duas ao mesmo tempo quando o celular permite.
- **Atalhos sem abrir o app**: segure o ícone na tela inicial, ou use o bloco "Gravar áudio" nas Configurações rápidas.
- **Cofre protegido por PIN**: as provas ficam numa área privada do app, fora da Galeria.
- **Integridade**: data, hora, localização e código SHA-256 de cada arquivo.
- **Casos**: agrupe as provas por situação, com relato, local, pessoas envolvidas e testemunhas.
- **Dossiê em PDF**: exporta o caso com a linha do tempo das provas, pronto para entregar a um advogado ou às autoridades.
- **Ajuda**: o que você pode ou não gravar e telefones úteis (190, 180, 100, 188).
- **Liberar tudo**: um botão só para conceder câmera, microfone, localização, notificações, bateria e manter as permissões.

## O que pode e o que não pode

| Situação | Vale como prova? |
|---|---|
| Gravar uma conversa **da qual você participa**, mesmo sem avisar | ✅ Sim (STF, Tema 237) |
| Fotografar ou filmar o que acontece com você | ✅ Em geral, sim |
| Deixar o celular gravando um ambiente **sem você presente** | ❌ Não, pode ser interceptação ilegal (Lei 9.296/96) |
| Publicar a gravação nas redes sociais | ⚠️ Arriscado. Entregue a um advogado ou às autoridades |

Este app ajuda a reunir provas, mas **não substitui orientação jurídica**. Procure um advogado ou a Defensoria Pública.

## Instalar

1. Baixe o `Testemunha.apk` na página de [Releases](../../releases).
2. Abra o arquivo no celular e permita "instalar apps desta fonte".
3. Na abertura, toque em **Liberar tudo** e crie um PIN.

Requer **Android 10** ou mais novo.

> **Importante:** desinstalar o app apaga todas as provas. Exporte o dossiê antes.

## Compilar

Precisa do JDK 17+ e do Android SDK (API 36).

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

O projeto não usa bibliotecas externas: só a API do Android.

## Limitações conhecidas

- O PIN bloqueia o acesso pelo app, mas os arquivos não são criptografados.
- A localização usa a última posição conhecida do celular e pode estar desatualizada.
- Vídeo com as duas câmeras ao mesmo tempo depende do aparelho.
