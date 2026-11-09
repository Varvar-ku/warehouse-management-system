# Сборка
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Раздача: nginx отдаёт SPA и проксирует /api на gateway
FROM nginx:1.27-alpine
# Адрес gateway подставляется при старте из переменной окружения (шаблон обрабатывает образ nginx)
ENV GATEWAY_URL=http://gateway:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
