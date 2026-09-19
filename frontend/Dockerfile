# syntax=docker/dockerfile:1

# Build multi-stage do portal-web (Angular 19).
#
# Alvos:
#   dev   -> ng serve com hot reload (serviço `web` do compose)
#   prod  -> bundle de produção servido por nginx (serviço `web-prod`)
#
# Alpine do começo ao fim, para builder e runtime ficarem na mesma base.
# Todas as dependências nativas do build (esbuild, rollup, lightningcss,
# @tailwindcss/oxide, @parcel/watcher, @napi-rs/nice) publicam variante
# linux-x64-musl, então rodam em Alpine.
ARG NODE_VERSION=24

# ---------- deps: instala node_modules uma vez, em camada cacheável ----------
FROM node:${NODE_VERSION}-alpine AS deps
WORKDIR /app
# Só os manifestos aqui: enquanto eles não mudarem, o npm ci vem do cache.
COPY package.json package-lock.json ./
# Sem --no-optional: os binários do esbuild/rollup/lightningcss são
# optionalDependencies e o build quebra sem eles.
#
# Nota: `lmdb` e `msgpackr-extract` (cache em disco do @angular/build) não têm
# prebuild musl. São opcionais, o npm os pula e o Angular cai no cache em
# memória — o build funciona, só não reaproveita cache entre execuções.
RUN npm ci --no-audit --no-fund

# ---------- dev: servidor de desenvolvimento ----------
FROM node:${NODE_VERSION}-alpine AS dev
WORKDIR /app
ENV NODE_ENV=development
COPY --from=deps /app/node_modules ./node_modules
COPY . .
EXPOSE 4200
# --host 0.0.0.0 para o servidor aceitar conexões de fora do contêiner.
CMD ["npx", "ng", "serve", "--host", "0.0.0.0", "--port", "4200"]

# ---------- build: gera o bundle de produção ----------
FROM node:${NODE_VERSION}-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---------- prod: nginx servindo os estáticos ----------
FROM nginx:stable-alpine AS prod
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
# `ng build` gera dist/portal-web/browser (builder `application` do Angular).
COPY --from=build /app/dist/portal-web/browser /usr/share/nginx/html
EXPOSE 80
# 127.0.0.1 e não `localhost`: dentro do contêiner o localhost resolve primeiro
# para ::1, e o nginx aqui só escuta em IPv4 — daria "connection refused".
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1
