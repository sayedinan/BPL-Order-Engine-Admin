# v0.3 frontend — multi-stage production image.
#
# Build:
#   docker build \
#     --build-arg VITE_API_BASE_URL=https://bpl-admin.example.com \
#     --build-arg VITE_USE_MOCK=false \
#     -t bpl-frontend:prod ./frontend
#
# Vite substitutes these at build time. They are baked into the JS
# bundle; no runtime config injection.

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies with a cached layer.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Build with the deploy-time args.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_USE_MOCK=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_USE_MOCK=$VITE_USE_MOCK

COPY . .
RUN npm run build

# ---- Run stage (nginx) ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/healthz || exit 1
