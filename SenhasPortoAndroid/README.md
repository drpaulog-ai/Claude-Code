# Senhas Porto – Android

Versão Android da app [Senhas Porto](../SenhasPorto/LEIA-ME.md): mostra quantas senhas estão em espera na
**Loja do Cidadão do Porto** e no **IEFP – Serviço de Emprego do Porto**, com botões para tirar senha digital.

- Lê as páginas oficiais (gov.pt e iefp.pt) num WebView, com o mesmo extrator da versão Windows
  (`SenhasPorto/js/extract.js`). Atualiza a cada 2 minutos enquanto a app está aberta, ou com **Atualizar**.
- **Ver página oficial na app** abre a página do local dentro da app; os botões de senha digital abrem o navegador.
- Requer Android 8.0 ou superior. Os locais estão em `Locations.kt`.

## Instalar

Descarregue o `.apk` da página **Releases** do repositório (`senhasporto-android-v…`) e abra-o no telemóvel.

## Compilar

```
./gradlew assembleRelease
```

Para publicar uma nova versão, altere `VERSION` e faça push: o workflow cria a release.
