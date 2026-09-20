#!/usr/bin/env bash
# O local.properties e gerado pelo Android Studio e fica fora do versionamento, entao pode
# chegar aqui apontando para um SDK do Windows. O sdk.dir dele sobrepoe o ANDROID_HOME, e o
# build quebraria. Reescrevemos apenas quando o caminho nao existe dentro do container.
set -euo pipefail

local_props="${PWD}/local.properties"
sdk_dir=""

if [ -f "${local_props}" ]; then
    sdk_dir=$(sed -n 's/^sdk\.dir=//p' "${local_props}" | tail -n 1 | sed 's/\\\(.\)/\1/g')
fi

if [ ! -d "${sdk_dir}" ]; then
    printf 'sdk.dir=%s\n' "${ANDROID_HOME}" > "${local_props}"
    echo "[entrypoint] local.properties apontado para ${ANDROID_HOME}"
fi

# O wrapper vem sem bit de execucao quando o repositorio foi clonado pelo Windows.
if [ -f "${PWD}/gradlew" ] && [ ! -x "${PWD}/gradlew" ]; then
    chmod +x "${PWD}/gradlew"
fi

# O aparelho fala adb sobre TCP na LAN, entao o container chega nele sem passar pelo Windows.
# Falha aqui nao derruba o container: o build local continua valendo sem aparelho conectado.
if [ -n "${ADB_DEVICE:-}" ]; then
    adb start-server > /dev/null 2>&1 || true
    connected=""
    # O aparelho costuma levar alguns segundos para aceitar conexao depois de um `adb tcpip`.
    for _ in 1 2 3 4 5; do
        if adb connect "${ADB_DEVICE}" 2>/dev/null | grep -qE '^(connected|already connected)'; then
            connected="sim"
            break
        fi
        sleep 2
    done
    if [ -n "${connected}" ]; then
        echo "[entrypoint] adb conectado em ${ADB_DEVICE}"
    else
        echo "[entrypoint] aviso: nao foi possivel conectar em ${ADB_DEVICE}" >&2
    fi
fi

exec "$@"
