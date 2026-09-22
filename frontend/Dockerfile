FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV BACKEND_UPSTREAM=backend:8080
ENV NGINX_ENVSUBST_FILTER=BACKEND_UPSTREAM
COPY nginx/00-forwarded.conf /etc/nginx/conf.d/00-forwarded.conf
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/15-api-config.sh /docker-entrypoint.d/15-api-config.sh
RUN chmod +x /docker-entrypoint.d/15-api-config.sh
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=8 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null
