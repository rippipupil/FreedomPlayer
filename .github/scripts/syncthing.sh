#!/usr/bin/env bash
# Syncthing para empaquetarlo con la app: los binarios oficiales de GitHub (syncthing/syncthing),
# comprobados con la lista de sumas SHA-256 de esa versión. No se guardan en el repositorio.
#   android: linux-arm64, linux-arm y linux-amd64 → androidApp/src/main/jniLibs/<abi>/libsyncthing.so
#            (Android los saca a la carpeta de bibliotecas nativas, desde donde se pueden ejecutar)
#   windows: windows-amd64 → desktopApp/resources/windows/syncthing.exe
set -euo pipefail
VERSION="${SYNCTHING_VERSION:-v2.1.6}"
BASE="https://github.com/syncthing/syncthing/releases/download/$VERSION"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl -fsSL --retry 4 --retry-delay 2 -o "$WORK/sumas.txt" "$BASE/sha256sum.txt.asc"

# Descarga un archivo de la versión y comprueba su suma SHA-256.
fetch() {
  curl -fsSL --retry 4 --retry-delay 2 -o "$WORK/$1" "$BASE/$1"
  expected=$(grep -E "^[0-9a-f]{64}[ *]+$1\$" "$WORK/sumas.txt" | head -n 1 | cut -c1-64 || true)
  if [ -z "$expected" ]; then
    echo "::error title=Syncthing::No hay suma SHA-256 para $1 en $VERSION"
    exit 1
  fi
  actual=$(sha256sum "$WORK/$1" | cut -c1-64)
  if [ "$expected" != "$actual" ]; then
    echo "::error title=Syncthing::La suma SHA-256 de $1 no coincide"
    exit 1
  fi
  echo "$1: suma SHA-256 correcta ($actual)"
}

case "${1:-}" in
  android)
    for pair in arm64:arm64-v8a arm:armeabi-v7a amd64:x86_64; do
      arch="${pair%%:*}"
      abi="${pair#*:}"
      name="syncthing-linux-$arch-$VERSION"
      fetch "$name.tar.gz"
      tar -xzf "$WORK/$name.tar.gz" -C "$WORK" "$name/syncthing"
      mkdir -p "androidApp/src/main/jniLibs/$abi"
      install -m 755 "$WORK/$name/syncthing" "androidApp/src/main/jniLibs/$abi/libsyncthing.so"
    done
    ;;
  windows)
    name="syncthing-windows-amd64-$VERSION"
    fetch "$name.zip"
    mkdir -p desktopApp/resources/windows
    python - "$WORK/$name.zip" "$name/syncthing.exe" desktopApp/resources/windows/syncthing.exe <<'PY'
import shutil, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z, z.open(sys.argv[2]) as src, open(sys.argv[3], "wb") as dst:
    shutil.copyfileobj(src, dst)
PY
    ;;
  *)
    echo "Uso: syncthing.sh android|windows" >&2
    exit 2
    ;;
esac
echo "::notice title=Syncthing::$VERSION ($1) descargado y comprobado"
