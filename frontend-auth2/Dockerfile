# syntax=docker/dockerfile:1

FROM node:24-alpine AS dev
WORKDIR /app
ENV NODE_ENV=development

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
EXPOSE 4202
CMD ["npm", "start", "--", "--host", "0.0.0.0"]
