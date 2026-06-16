#!/bin/sh
set -e

# Replace build-time placeholder strings with actual runtime env var values.
# This lets Dokploy's Environment Variables (docker run -e) override VITE_* vars
# even though Vite bakes them in at build time.
for file in /usr/share/nginx/html/assets/*.js; do
  sed -i "s|__VITE_API_BASE_URL__|${VITE_API_BASE_URL:-http://localhost:8087/api/v1}|g" "$file"
  sed -i "s|__VITE_KEYCLOAK_URL__|${VITE_KEYCLOAK_URL:-http://localhost:1001}|g" "$file"
done

exec nginx -g "daemon off;"
