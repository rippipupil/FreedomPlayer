# FreedomPlayer

Reproductor de música personal para Android y Windows, hecho con Kotlin y Compose Multiplatform.
Buscas en YouTube Music, guardas las canciones en tu biblioteca, las puntúas del 1 al 10 y armas
tus propios rankings.

> **Estado: hito 1.** La interfaz está completa en Android y en Windows, pero funciona con
> canciones de ejemplo: todavía no busca, descarga ni reproduce audio de verdad (la reproducción
> se simula). Ver [docs/decisiones.md](docs/decisiones.md) para las decisiones de diseño.

## Descargar

- **Versiones publicadas:** en [Releases](https://github.com/rippipupil/freedomplayer/releases)
  están el APK de Android y el instalador de Windows de cada versión.
- **Compilaciones de prueba:** cada push a `main` compila la app en
  [Actions](https://github.com/rippipupil/freedomplayer/actions/workflows/build.yml). Al abrir una
  ejecución terminada, abajo en «Artifacts» están:
  - `FreedomPlayer-android`: el APK (Android 8.0 o posterior).
  - `FreedomPlayer-windows`: el instalador `.msi` y una versión portable `.zip` (se descomprime y
    se abre `FreedomPlayer.exe`).

  Hay que haber iniciado sesión en GitHub para descargar los artefactos, y GitHub los borra a los
  90 días.

En Android hay que permitir instalar apps de origen desconocido. Windows puede avisar con
SmartScreen porque el instalador no está firmado: «Más información» › «Ejecutar de todas formas».

## Firma del APK

El workflow firma el APK así:

- **Con la clave de publicación**, si el repositorio tiene estos secretos (Settings › Secrets and
  variables › Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.
- **Con la clave de depuración estándar de Android**, si no los tiene. Esa clave se guarda en la
  caché de Actions para que todas las compilaciones lleven la misma y se instalen unas encima de
  otras. Si la caché caduca (7 días sin compilar), la siguiente compilación lleva otra clave y hay
  que desinstalar la app antes de instalarla.

Al pasar de la clave de depuración a la de publicación también hay que desinstalar la versión
anterior una vez. Para crear la clave de publicación (en tu equipo, nunca en el repositorio):

```bash
keytool -genkeypair -v -keystore freedomplayer.jks -alias freedomplayer \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 freedomplayer.jks   # esto va en KEYSTORE_BASE64
```

Guarda el archivo `.jks` y sus contraseñas en un sitio seguro: si se pierden, las versiones nuevas
ya no se podrán instalar encima de las antiguas.

## Publicar una versión

```bash
git tag v0.2.0
git push origin v0.2.0
```

El workflow compila esa versión, pasa las pruebas y crea la release con el APK, el `.msi` y el
`.zip`. Las versiones `v0.*` y las que llevan guion (`v1.0.0-beta.1`) salen como pre-release.
Las notas de la release se toman de `docs/versiones/<etiqueta>.md` si existe.

## Desarrollo

Hace falta JDK 17 o posterior. Para Android, también el SDK de Android (compileSdk 36).

```bash
./gradlew :desktopApp:run                       # abrir la app de escritorio
./gradlew :androidApp:installDebug              # instalar en un móvil conectado
./gradlew :shared:desktopTest                   # pruebas de la lógica y de la interfaz
./gradlew -Pfp.android=false :desktopApp:run    # sin el SDK de Android
./gradlew :desktopApp:packageMsi                # instalador de Windows (en Windows)
```

En el PC, la variable `FP_REDUCE_MOTION=1` quita las animaciones aunque el sistema no lo pida.

Las pruebas incluyen una prueba de humo de la interfaz (`AppSmokeTest`) que pinta la app sin
ventana, con tamaño de móvil y de PC, y recorre todas las pantallas. Para probar el APK en un
emulador de Android: Actions › «Probar en el emulador» › Run workflow.

### Estructura

- `shared/`: todo lo común.
  - `core/`: la lógica sin interfaz (biblioteca, cola, ranking, estadísticas, búsqueda, letras,
    limpieza de títulos, importación de Spotify) y los datos de ejemplo. Tiene pruebas en
    `commonTest`.
  - `ui/`: el estado de la app (`AppState`), el tema, los componentes (fantasmas, paneles,
    pestañas, avisos, reproductores…) y las pantallas.
- `androidApp/`: la actividad de Android.
- `desktopApp/`: la ventana de Windows (barra de título propia, bandeja del sistema y
  mini-reproductor).
- `docs/`: decisiones de diseño, notas de cada versión y licencias de las tipografías.

## Licencias de las tipografías

La app incluye las tipografías [Chakra Petch](docs/OFL-ChakraPetch.txt) y
[Nunito](docs/OFL-Nunito.txt), con licencia SIL Open Font License 1.1.
