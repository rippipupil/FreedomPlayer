#!/usr/bin/env bash
# Syncthing para empaquetarlo con la app: los binarios oficiales de GitHub (syncthing/syncthing),
# comprobados con la lista de sumas SHA-256 de esa versión. No se guardan en el repositorio.
#   android: linux-arm64 y linux-arm → androidApp/src/main/jniLibs/<abi>/libsyncthing.so
#            (Android los saca a la carpeta de bibliotecas nativas, desde donde se pueden ejecutar);
#            x86_64 se compila del código fuente oficial con el NDK (ver build_android)
#   windows: windows-amd64 → desktopApp/resources/windows/syncthing.exe
#   linux:   linux-amd64 → build/syncthing/syncthing (solo para las pruebas de la CI)
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

# Compila Syncthing para Android con el NDK, del código fuente oficial de la versión (comprobado con
# su suma SHA-256; trae sus dependencias en vendor/). Se usa en x86_64 (emulador y Chromebooks): el
# binario oficial para Linux amd64 llama a lstat() (la SQLite que lleva dentro), y el filtro seccomp
# de Android x86_64 no deja esa llamada a las apps, así que se cerraba al arrancar (SIGSYS). Para
# Android, SQLite va con la libc de Android (bionic), que no la usa. En ARM no hace falta: esas
# llamadas antiguas no existen allí.
#   $1: GOARCH  $2: ABI de Android  $3: prefijo del clang del NDK
build_android() {
  goarch="$1"
  abi="$2"
  triple="$3"
  if ! command -v go > /dev/null; then
    echo "::error title=Syncthing::Falta Go para compilar Syncthing para Android ($abi)"
    exit 1
  fi
  if [ ! -d "$WORK/syncthing" ]; then
    fetch "syncthing-source-$VERSION.tar.gz"
    tar -xzf "$WORK/syncthing-source-$VERSION.tar.gz" -C "$WORK"
  fi
  ndk="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
  if [ -z "$ndk" ] || [ ! -d "$ndk" ]; then
    ndk="$(ls -d "${ANDROID_HOME:-/usr/local/lib/android/sdk}"/ndk/*/ 2> /dev/null | sort -V | tail -n 1 || true)"
  fi
  # La versión mínima de Android de la app (minSdk 26).
  cc="${ndk%/}/toolchains/llvm/prebuilt/linux-x86_64/bin/${triple}26-clang"
  if [ ! -x "$cc" ]; then
    echo "::error title=Syncthing::No se encuentra el NDK de Android (falta $cc)"
    exit 1
  fi
  out="$PWD/androidApp/src/main/jniLibs/$abi/libsyncthing.so"
  mkdir -p "$(dirname "$out")"
  # Las etiquetas de las compilaciones oficiales, sin netgo: en Android los nombres (DNS) los
  # resuelve el sistema. -checklinkname=0: lo pide github.com/wlynxg/anet (interfaces de red en
  # Android) desde Go 1.23.
  (
    cd "$WORK/syncthing"
    CGO_ENABLED=1 BUILD_USER=freedomplayer BUILD_HOST=github-actions EXTRA_LDFLAGS="-checklinkname=0" \
      go run build.go -goos android -goarch "$goarch" -cc "$cc" -no-upgrade \
      -tags "sqlite_omit_load_extension sqlite_dbstat" -build-out "$out" build syncthing
  )
  chmod 755 "$out"
  echo "$abi: Syncthing $VERSION compilado para Android con $(basename "$cc") ($(go version))"
}

case "${1:-}" in
  android)
    for pair in arm64:arm64-v8a arm:armeabi-v7a; do
      arch="${pair%%:*}"
      abi="${pair#*:}"
      name="syncthing-linux-$arch-$VERSION"
      fetch "$name.tar.gz"
      tar -xzf "$WORK/$name.tar.gz" -C "$WORK" "$name/syncthing"
      mkdir -p "androidApp/src/main/jniLibs/$abi"
      install -m 755 "$WORK/$name/syncthing" "androidApp/src/main/jniLibs/$abi/libsyncthing.so"
    done
    build_android amd64 x86_64 x86_64-linux-android
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
  linux)
    # Solo para las pruebas (dos Syncthing de verdad en el mismo equipo): build/syncthing/syncthing
    name="syncthing-linux-amd64-$VERSION"
    fetch "$name.tar.gz"
    tar -xzf "$WORK/$name.tar.gz" -C "$WORK" "$name/syncthing"
    mkdir -p build/syncthing
    install -m 755 "$WORK/$name/syncthing" build/syncthing/syncthing
    if [ -n "${GITHUB_ENV:-}" ]; then echo "SYNCTHING_BIN=$PWD/build/syncthing/syncthing" >> "$GITHUB_ENV"; fi
    ;;
  *)
    echo "Uso: syncthing.sh android|windows|linux" >&2
    exit 2
    ;;
esac
echo "::notice title=Syncthing::$VERSION ($1) descargado y comprobado"
