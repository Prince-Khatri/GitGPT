#!/bin/sh
# Runtime API origin so the image can be reused across hosts without a rebuild.
set -e
value=${GITGPT_API_BASE_URL:-}
escaped=$(printf '%s' "$value" | sed 's/\\/\\\\/g; s/"/\\"/g')
printf 'window.GITGPT_API_BASE_URL = "%s";\n' "$escaped" > /usr/share/nginx/html/config.js
