#!/usr/bin/env bash
# The measurement scaffold never invokes a real model provider.
# Preserve inherited sensitive values only for redaction inside this process.
export MLS_C1_INHERITED_MODEL_KEY="${AI_API_KEY:-}"
export MLS_C1_INHERITED_LEGACY_KEY="${API_KEY:-}"
export AI_API_KEY= AI_ENDPOINT_URL= AI_MODEL= API_KEY= BASE_URL= MODEL=
redact() {
  python3 -c '
import os, sys
text = sys.stdin.read()
for name in ("DB_PASSWORD", "GRAFANA_PASSWORD", "AI_VISITOR_COOKIE_SIGNING_KEY",
             "MLS_C1_INHERITED_MODEL_KEY", "MLS_C1_INHERITED_LEGACY_KEY"):
    value = os.environ.get(name, "")
    if value:
        text = text.replace(value, "[REDACTED]")
sys.stdout.write(text)'
}
