#!/bin/sh
set -eu

CONFIG_FILE=/tmp/alertmanager.yml
WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"
SEND_RESOLVED="${ALERT_WEBHOOK_SEND_RESOLVED:-true}"
FEISHU_BOT_WEBHOOK_URL="${FEISHU_BOT_WEBHOOK_URL:-}"

if [ -z "${WEBHOOK_URL}" ] && [ -n "${FEISHU_BOT_WEBHOOK_URL}" ]; then
  WEBHOOK_URL="http://alert-relay:8080/webhook"
fi

cat > "${CONFIG_FILE}" <<'EOF'
route:
  receiver: default
  group_by:
    - alertname
    - severity
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h

receivers:
  - name: default
EOF

if [ -n "${WEBHOOK_URL}" ]; then
  cat >> "${CONFIG_FILE}" <<EOF
    webhook_configs:
      - url: '${WEBHOOK_URL}'
        send_resolved: ${SEND_RESOLVED}
EOF
fi

exec /bin/alertmanager \
  --config.file="${CONFIG_FILE}" \
  --storage.path=/alertmanager
