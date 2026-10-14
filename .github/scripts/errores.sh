#!/usr/bin/env bash
# Si una compilación falla, saca sus errores (de Kotlin, Gradle, lint y pruebas) del log y los
# publica también como anotaciones de la ejecución, para verlos sin abrir el log entero.
# Uso: errores.sh build.log
log="${1:-build.log}"
if [ ! -f "$log" ]; then
  echo "No hay log en $log"
  exit 0
fi

out="$(mktemp)"
awk '
  /^e: / || /: Error: / || /^ERROR:/ || /error: / { print; next }
  /^\* What went wrong:/ { inside = 1; print; next }
  /^\* Try:/ { inside = 0 }
  inside && NF { print; next }
  # Prueba fallida: el nombre, el mensaje del error y las líneas de nuestro código.
  / FAILED$/ { print; test = 1; shown = 0; next }
  test && /^[ \t]/ {
    if ($1 != "at" || $0 ~ /freedomplayer/ || shown < 6) { print; shown++ }
    next
  }
  { test = 0 }
' "$log" | cut -c1-500 | awk '!seen[$0]++' | head -n 300 > "$out"

if [ ! -s "$out" ]; then
  # Sin errores reconocibles: el final del log suele explicar el fallo.
  tail -n 60 "$log" | cut -c1-500 > "$out"
fi

echo "---------- Errores ----------"
cat "$out"

# GitHub admite como mucho 10 anotaciones de error por paso: 25 líneas en cada una.
split -l 25 "$out" "$out.part."
n=0
for part in "$out".part.*; do
  n=$((n + 1))
  [ "$n" -gt 10 ] && break
  msg="$(sed -e 's/%/%25/g' -e 's/\r/%0D/g' "$part" | awk 'BEGIN { ORS = "%0A" } { print }')"
  echo "::error title=Errores ($n)::$msg"
done
rm -f "$out" "$out".part.*
