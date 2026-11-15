#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

IMAGE="git.muriloscolari.me/murilo/unicep-portal"
TAG="${1:-$(git describe --tags --abbrev=0 2>/dev/null || true)}"
TAG="${TAG#v}"

if [ -n "$TAG" ]; then
  echo "Building ${IMAGE}:${TAG} and ${IMAGE}:latest ..."
  docker build -t "${IMAGE}:${TAG}" -t "${IMAGE}:latest" .
  docker push "${IMAGE}:${TAG}"
  docker push "${IMAGE}:latest"
else
  echo "Building ${IMAGE}:latest ..."
  docker build -t "${IMAGE}:latest" .
  docker push "${IMAGE}:latest"
fi

echo "Done! Watchtower no homelab atualizará em até 5 minutos."
