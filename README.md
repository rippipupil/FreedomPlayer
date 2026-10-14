# FreedomPlayer

Reproductor de música personal para Android y Windows, hecho con Kotlin y Compose Multiplatform.
Buscas en YouTube Music, guardas las canciones en tu biblioteca, las puntúas del 1 al 10 y armas
tus propios rankings.

> **Estado: hito 5.** Busca en YouTube Music y YouTube (con NewPipe Extractor), escucha antes de
> añadir, descarga el mejor audio con su carátula y su letra sincronizada (LRCLIB), guarda la
> biblioteca y suena de verdad: en Android con notificación y pantalla de bloqueo, y en Windows
> con FFmpeg dentro de la app. Tiene ecualizador, volumen igualado, fundido entre canciones, cola,
> temporizador, estadísticas por meses e importa playlists públicas de Spotify sin clave. En el
> PC responde a las teclas multimedia y añade tus propios archivos de audio. En Ajustes se elige
> la calidad de las descargas y, en el PC, su carpeta. Falta la sincronización entre el móvil y el
> PC (en curso, con Syncthing). Ver
> [docs/decisiones.md](docs/decisiones.md) para las decisiones de diseño y técnicas, y
> [docs/versiones/](docs/versiones/) para las notas de cada versión.

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

Las compilaciones de prueba se numeran `0.5.<n.º de ejecución>`. Si en Windows instalaste una con
un número mayor que la versión publicada que quieres instalar, desinstálala antes (el instalador
no deja volver a una versión menor).

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
git tag v0.5.0
git push origin v0.5.0
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
./gradlew -Pfp.android=false :shared:networkSmoke   # YouTube, LRCLIB y Spotify de verdad (necesita internet)
```

NewPipe Extractor se descarga de JitPack; el resto, de Maven Central y Google Maven.

En el PC, la variable `FP_REDUCE_MOTION=1` quita las animaciones aunque el sistema no lo pida.

Las pruebas incluyen una prueba de humo de la interfaz (`AppSmokeTest`) que pinta la app sin
ventana, con tamaño de móvil y de PC, y recorre todas las pantallas. También prueban la base de
datos y sus consultas del historial, las descargas, LRCLIB y las páginas de Spotify contra un
servidor local, y el audio del PC (ecualizador, volumen igualado y fundido) con FFmpeg de verdad.
En cada compilación, además, el APK se instala en un emulador de Android 14: se abre, se gira la
pantalla y se pasan las pruebas del audio de verdad (`androidApp/src/androidTest`).

`CapturasTest` pinta las pantallas principales al tamaño de las capturas del diseño; en `main`,
el job «Capturas de pantalla» las sube a la rama `capturas-ci` para compararlas.

Las pruebas no usan internet: YouTube a veces bloquea los servidores de GitHub. Aparte, el job
«YouTube, LRCLIB y Spotify (solo aviso)» busca, saca el audio, pide una letra y lee una playlist
de Spotify de verdad; si falla, solo deja un aviso en la ejecución.

### Estructura

- `shared/`: todo lo común.
  - `core/`: la lógica sin interfaz (biblioteca, cola, ranking, estadísticas, búsqueda, letras
    LRC, limpieza de títulos, elección del audio, importación de Spotify) y los datos de ejemplo.
    Tiene pruebas en `commonTest`.
  - `data/`: lo que la app necesita de fuera (`Services`: biblioteca, buscador, descargas, letras
    y audio), la biblioteca en SQLite (`SqlLibraryStore`, con el esquema en
    `commonMain/sqldelight`) y las versiones de ejemplo para las pruebas.
  - `jvmShared/`: lo que comparten Android y el PC: YouTube con NewPipe Extractor, descargas con
    OkHttp, letras de LRCLIB y las playlists públicas de Spotify.
  - `desktopMain/platform/`: el audio del PC (FFmpeg, con ecualizador y fundido), medir la
    sonoridad y dónde guarda la biblioteca.
  - `androidMain/platform/`: dónde guarda la biblioteca en Android y medir la sonoridad con
    MediaCodec.
  - `ui/`: el estado de la app (`AppState`), el tema, los componentes (fantasmas, paneles,
    pestañas, avisos, reproductores…) y las pantallas.
- `androidApp/`: la actividad de Android, la reproducción con Media3 (`playback/`, con el
  ecualizador del sistema y el fundido), la importación de archivos propios y las pruebas en el
  emulador (`src/androidTest`).
- `desktopApp/`: la ventana de Windows (barra de título propia, bandeja del sistema,
  mini-reproductor, teclas multimedia y el selector para añadir archivos propios).
- `docs/`: decisiones de diseño, notas de cada versión y licencias de las tipografías.

## Licencias

La app incluye las tipografías [Chakra Petch](docs/OFL-ChakraPetch.txt) y
[Nunito](docs/OFL-Nunito.txt), con licencia SIL Open Font License 1.1.

También lleva [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0) y,
en el PC, FFmpeg (LGPL) a través de [JavaCV](https://github.com/bytedeco/javacv). Las letras
vienen de [LRCLIB](https://lrclib.net).
