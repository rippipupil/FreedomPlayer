#!/usr/bin/env bash
# Pruebas en el emulador (androidApp/src/androidTest): el audio de verdad con ExoPlayer, la sesión
# de Media3 y la notificación. La app ya tiene que estar instalada (probar-apk.sh).
# Uso: pruebas-android.sh <apk de las pruebas>
set -u
apk="$1"

adb install -r -t "$apk" || { echo "::error title=Pruebas en el emulador::No se pudo instalar el APK de las pruebas."; exit 1; }
adb logcat -c
adb shell am instrument -w com.freedomplayer.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee pruebas-android.txt
adb logcat -d > logcat-pruebas.txt || true

if grep -q "^OK (" pruebas-android.txt; then
  echo "::notice title=Pruebas en el emulador::$(grep '^OK (' pruebas-android.txt | head -n 1)"
  exit 0
fi

# Los fallos, en el resumen de la ejecución (las líneas en blanco no cortan el mensaje).
msg="$(head -n 80 pruebas-android.txt | sed -e 's/%/%25/g' -e 's/\r$//' | awk 'BEGIN { ORS = "%0A" } { print }')"
echo "::error title=Pruebas en el emulador::$msg"
if grep -q "FATAL EXCEPTION" logcat-pruebas.txt; then
  crash="$(grep -A 25 "FATAL EXCEPTION" logcat-pruebas.txt | head -n 40 | sed -e 's/%/%25/g' | awk 'BEGIN { ORS = "%0A" } { print }')"
  echo "::error title=Pruebas en el emulador (error fatal)::$crash"
fi
exit 1
