#!/usr/bin/env bash
# Instala el APK en el emulador, abre la app y comprueba que sigue viva y sin errores fatales.
# Uso: probar-apk.sh <apk>
set -u
apk="$1"
pkg="com.freedomplayer"
fail=0

adb logcat -c
adb install -r "$apk" || { echo "::error title=Emulador::No se pudo instalar el APK."; exit 1; }
adb shell am start -W -n "$pkg/com.freedomplayer.android.MainActivity"
sleep 20

if adb shell pidof "$pkg" > /dev/null; then
  echo "La app sigue abierta después de 20 s."
else
  echo "::error title=Emulador::La app se cerró sola al abrirla."
  fail=1
fi

# Girar la pantalla y volver (la app debe aguantar el cambio de tamaño).
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 5
adb shell settings put system user_rotation 0
sleep 5
if ! adb shell pidof "$pkg" > /dev/null; then
  echo "::error title=Emulador::La app se cerró al girar la pantalla."
  fail=1
fi

adb exec-out screencap -p > captura-emulador.png || true
adb logcat -d > logcat.txt || true
if grep -q "FATAL EXCEPTION" logcat.txt; then
  msg="$(grep -A 25 "FATAL EXCEPTION" logcat.txt | head -n 40 | sed -e 's/%/%25/g' | awk 'BEGIN { ORS = "%0A" } { print }')"
  echo "::error title=Emulador (error fatal)::$msg"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "::notice title=Emulador::La app abre, sigue viva a los 20 s y aguanta el giro de pantalla, sin errores fatales."
fi
exit "$fail"
