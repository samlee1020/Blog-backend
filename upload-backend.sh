#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="root@39.106.15.85"
REMOTE_DIR="/opt/Blog-backend"

DRY_RUN="${DRY_RUN:-0}"

RSYNC_ARGS=(
  -avz
  --delete
  --exclude 'target/'
  --exclude 'data/'
  --exclude '.DS_Store'
  --exclude '.idea/'
  --exclude '.vscode/'
  --exclude '*.log'
)

if [ "$DRY_RUN" = "1" ]; then
  RSYNC_ARGS+=(--dry-run)
  echo "Running upload dry-run..."
else
  echo "Uploading backend files..."
fi

rsync "${RSYNC_ARGS[@]}" ./ "$REMOTE_HOST:$REMOTE_DIR/"

if [ "$DRY_RUN" = "1" ]; then
  echo "Dry-run finished. No files were changed."
else
  echo "Upload finished. Please build/restart on the server manually."
fi
