# Lab-mobile (app-android)

App Android do TCC. Todo o ambiente de build vive em Docker: **nao e preciso instalar JDK,
Gradle ou Android SDK no host**.

| Componente | Versao |
| --- | --- |
| JDK | 21 (Temurin) |
| Gradle | 9.2.1 (via wrapper) |
| AGP | 9.0.1 |
| build-tools | 36.0.0 |
| compileSdk / targetSdk | 36.1 / 36 |
| minSdk | 29 |

## Uso

```bash
docker compose up -d           # sobe o ambiente (primeira vez: build da imagem)
docker compose exec app-android ./gradlew assembleDebug
docker compose exec app-android ./gradlew installDebug   # compila e instala no aparelho
docker compose exec app-android ./gradlew test
docker compose exec app-android bash    # shell dentro do ambiente
docker compose down            # encerra
```

Rebuild e reinstalacao automaticos a cada arquivo salvo:

```bash
docker compose exec app-android ./gradlew -t installDebug
```

Nao e hot reload: cada ciclo recompila e reinstala o APK. O "Apply Changes" e o debugger com
breakpoints dependem da IDE e nao existem neste fluxo; `adb logcat` continua disponivel.

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`, no proprio diretorio do
projeto, porque a pasta e montada dentro do container.

## Aparelho fisico via Wi-Fi

O container fala adb sobre TCP direto com o aparelho pela LAN — sem passar pelo Windows e sem
regra de firewall. O `docker compose up` ja reconecta sozinho no endereco de `ADB_DEVICE`.

Configuracao inicial (uma vez, com o aparelho no cabo USB):

```bash
ADB='/mnt/c/Users/angel/AppData/Local/Android/Sdk/platform-tools/adb.exe'

"$ADB" devices                                    # autorize o prompt na tela do aparelho
"$ADB" shell ip -o -4 addr show wlan0             # descubra o IP na Wi-Fi
"$ADB" tcpip 5555                                 # poe o aparelho em modo TCP
```

Depois copie `.env.example` para `.env` e ajuste o `ADB_DEVICE` com o IP descoberto:

```bash
cp .env.example .env
docker compose up -d
docker compose exec app-android adb devices       # deve listar o aparelho como `device`
```

### Quando parar de funcionar

- **O aparelho reiniciou.** O modo TCP nao sobrevive a um boot: refaca o `adb tcpip 5555` pelo
  cabo USB.
- **O IP mudou** (DHCP). Atualize `ADB_DEVICE` no `.env` e rode `docker compose up -d`.
- **Aparece `unauthorized`.** O aparelho precisa autorizar a chave RSA do container. Desbloqueie
  a tela e aceite o prompt "Permitir depuracao USB". A chave fica no volume
  `app-android-adb-home`, entao a autorizacao sobrevive a recriacoes do container.
- **O prompt de autorizacao nao aparece.** Em *Opcoes do desenvolvedor*, use **Revogar
  autorizacoes de depuracao USB** e reconecte o cabo.

## UID/GID

Os arquivos criados pelo build pertencem ao usuario do host (`1000:1000` por padrao). Se o seu
usuario tiver outro par, exporte antes do build da imagem:

```bash
HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose build
```

## Por que Docker e nao o host

O projeto fica no sistema de arquivos do WSL (`ext4`). Abrir esta pasta pelo Android Studio do
Windows, via `\\wsl.localhost\...`, faz o Gradle rodar sobre um compartilhamento 9p, que nao
implementa lock de arquivo. O build morre com `java.io.IOException: Incorrect function` ao tentar
travar `.gradle/<versao>/fileHashes`. Rodando dentro do container, o bind mount e ext4 nativo e o
lock funciona.

Por isso o `local.properties` **nao deve** apontar para um SDK do Windows. O entrypoint reescreve
o arquivo automaticamente quando o `sdk.dir` nao existe dentro do container.

Emulador nao roda aqui: esta maquina e Windows 10, que nao oferece virtualizacao aninhada ao
WSL2, entao nao existe `/dev/kvm`. Dai o uso de aparelho fisico.
