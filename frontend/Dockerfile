# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Frontend image: Vite build → static files served by nginx, which also
# reverse-proxies /api to the backend (same origin, so no CORS in production).
#   docker build -t examprep-frontend ./frontend
# ---------------------------------------------------------------------------

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://localhost/healthz || exit 1
