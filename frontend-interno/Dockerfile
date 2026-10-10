# syntax=docker/dockerfile:1

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:24-alpine AS dev
WORKDIR /app
ENV NODE_ENV=development
COPY --from=deps /app/node_modules ./node_modules
COPY . .
EXPOSE 4201
CMD ["sh", "-c", "npx ng serve --host 0.0.0.0 --port 4201 --proxy-config proxy.conf.cjs"]

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM nginx:stable-alpine AS prod
ENV API_PROXY_TARGET=http://host.docker.internal:8080
ENV NGINX_ENVSUBST_FILTER=^API_PROXY_TARGET$
COPY docker/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/portal-interno/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1
