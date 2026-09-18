#!/usr/bin/env bash
# Builds and starts the complete demo environment: PostgreSQL, Kafka and the
# service with sample data. Stop it with: docker compose --profile demo down
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
demo/generate-keys.sh
docker compose --profile demo up --build --detach --wait
cat << 'EOF'

The demo is running.
  API          http://localhost:8080   (Swagger UI: http://localhost:8080/swagger-ui.html)
  Management   http://localhost:8081/actuator/health/readiness, /actuator/prometheus

Try:
  demo/idempotency.sh
  demo/insufficient-balance.sh
  demo/fps-timeout.sh
  demo/four-eyes-withdrawal.sh
EOF
