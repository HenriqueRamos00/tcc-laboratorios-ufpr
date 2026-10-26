# A compilacao acontece numa imagem com JDK e Maven, e so o jar atravessa para a
# imagem final, que tem apenas a JRE. Quem invadir o contentor em producao nao
# encontra compilador, nem codigo fonte, nem cache de dependencia.
#
# As duas bases sao fixadas por digest, e nao por tag. Medido no registro: as
# tags `21.0.12.1_1-jre-alpine` e `21.0.12.1_1-jre-alpine-3.24` apontam hoje
# para o mesmo digest, e quando o Alpine base virar 3.25 a primeira passa a
# apontar para outro conteudo sem o nome mudar.
FROM eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24@sha256:fd52084c35bbac78890d95d57707d31ffc9d7a3324199e89c06be2a9dc71bfa0 AS build

RUN addgroup -S -g 10001 builder \
 && adduser -S -u 10001 -G builder -h /home/builder builder \
 && mkdir -p /workspace && chown builder:builder /workspace

WORKDIR /workspace
USER 10001:10001
ENV HOME=/home/builder

COPY --chown=builder:builder .mvn .mvn
COPY --chown=builder:builder mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -C dependency:go-offline
COPY --chown=builder:builder src src
RUN ./mvnw -B -C -Dmaven.test.skip=true package

FROM eclipse-temurin:21.0.12.1_1-jre-alpine-3.24@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088

# O processo roda com uma conta sem shell e sem senha, criada so para isto. O
# jar continua pertencendo ao root e entra somente leitura: um processo que
# vire refem nao consegue reescrever a propria aplicacao.
RUN addgroup -S -g 10001 authservice \
 && adduser -S -u 10001 -G authservice -s /sbin/nologin -H authservice

WORKDIR /app
COPY --from=build --chown=root:root --chmod=444 /workspace/target/backend-auth2.jar app.jar

USER 10001:10001
EXPOSE 8081

# `JDK_JAVA_OPTIONS` vale so para o lancador `java`. O `JAVA_TOOL_OPTIONS`, que
# estava aqui antes, vale para toda ferramenta JVM e imprime uma linha de aviso
# no stderr, que sujaria o log estruturado em JSON.
#
# A porcentagem de memoria so significa alguma coisa porque o compose declara
# `mem_limit`: sem limite de cgroup a JVM dimensiona a heap pela memoria da
# maquina inteira.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.util.prefs.userRoot=/tmp"

# Sem HEALTHCHECK aqui de proposito: a sonda depende da porta e do caminho, que
# sao configuracao e nao propriedade da imagem, e sob `read_only` ela subiria um
# shell a cada intervalo. A verificacao vive no compose.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
