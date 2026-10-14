"""Resume los resultados JUnit de las pruebas en una anotación de la ejecución.

Uso: python3 resumen-pruebas.py <carpeta con los XML>
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

folder = sys.argv[1] if len(sys.argv) > 1 else "."
total = failed = skipped = 0
lines = []
for path in sorted(glob.glob(os.path.join(folder, "*.xml"))):
    suite = ET.parse(path).getroot()
    tests = int(suite.get("tests", 0))
    bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    skip = int(suite.get("skipped", 0))
    total += tests
    failed += bad
    skipped += skip
    name = suite.get("name", "?").split(".")[-1]
    lines.append(f"{name}: {tests - bad - skip} de {tests} bien ({suite.get('time', '?')} s)")

if total == 0:
    print("::warning title=Pruebas::No se encontró ningún resultado de pruebas.")
else:
    head = f"{total} pruebas: {total - failed - skipped} bien, {failed} fallidas, {skipped} omitidas"
    level = "error" if failed else "notice"
    print(f"::{level} title=Pruebas::" + "%0A".join([head] + lines))
