# Ambiente de build do app Android: JDK 21 + Android SDK, sem instalar nada no host.
# Base Ubuntu (nao Alpine): as ferramentas do SDK, como o aapt2, sao ligadas a glibc.
FROM eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS_VERSION=13114758
ARG CMDLINE_TOOLS_SHA256=7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee
# COMPILE_SDK acompanha o compileSdk de app/build.gradle.kts; BUILD_TOOLS e a versao que o AGP exige.
ARG COMPILE_SDK=36.1
ARG BUILD_TOOLS=36.0.0
# Casam com o usuario do WSL para que os artefatos gerados no bind mount nao fiquem como root.
ARG UID=1000
ARG GID=1000

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/dev/.gradle \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git unzip \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"; \
    echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c -; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools"; \
    unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools"; \
    mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/bootstrap"; \
    rm /tmp/cmdline-tools.zip

# O zip acima so serve de bootstrap: ele instala o cmdline-tools;latest do proprio repositorio.
RUN set -eux; \
    yes | "${ANDROID_HOME}/cmdline-tools/bootstrap/bin/sdkmanager" --licenses > /dev/null; \
    "${ANDROID_HOME}/cmdline-tools/bootstrap/bin/sdkmanager" --install \
        "cmdline-tools;latest" \
        "platform-tools" \
        "platforms;android-${COMPILE_SDK}" \
        "build-tools;${BUILD_TOOLS}"; \
    rm -rf "${ANDROID_HOME}/cmdline-tools/bootstrap"; \
    yes | sdkmanager --licenses > /dev/null

# A imagem Ubuntu ja traz um usuario proprio no UID 1000; ele sai para liberar o par UID/GID do host.
RUN set -eux; \
    existing_user="$(getent passwd "${UID}" | cut -d: -f1 || true)"; \
    if [ -n "${existing_user}" ]; then userdel -r "${existing_user}"; fi; \
    existing_group="$(getent group "${GID}" | cut -d: -f1 || true)"; \
    if [ -n "${existing_group}" ]; then groupdel "${existing_group}" || true; fi; \
    groupadd -g "${GID}" dev; \
    useradd -u "${UID}" -g "${GID}" -m -s /bin/bash dev; \
    chown -R dev:dev "${ANDROID_HOME}"

COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

USER dev:dev

# Precisa existir na imagem com o dono certo: o volume nomeado do compose e semeado a partir
# daqui, e um diretorio ausente faria o Docker cria-lo como root, sem permissao de escrita.
RUN mkdir -p /home/dev/.android

# Aquece o GRADLE_USER_HOME com a distribuicao do wrapper. O volume nomeado do compose e
# semeado a partir desta camada, entao o primeiro build ja comeca com o Gradle baixado.
COPY --chown=dev:dev gradlew /tmp/warmup/gradlew
COPY --chown=dev:dev gradle/wrapper /tmp/warmup/gradle/wrapper
RUN set -eux; \
    cd /tmp/warmup; \
    chmod +x gradlew; \
    ./gradlew --version; \
    rm -rf /tmp/warmup

WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["sleep", "infinity"]
