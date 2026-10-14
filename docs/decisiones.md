# Decisiones de diseño

Respuestas a las dudas del diseño, decididas antes del hito 1. Mandan sobre todo lo anterior
(incluido `freedomplayer.md`). Donde el diseño y estas decisiones no dicen nada, manda el diseño.

En el hito 1 se hace ya todo lo que toca a la interfaz y a la lógica. Lo que depende de funciones
que llegan después (escuchar de verdad, descargar, Spotify) deja la interfaz preparada para ese
comportamiento, con datos de ejemplo.

## Navegación y listas

1. **Menú.** El menú de 5 iconos (Inicio, Buscar, Ranking, Estadísticas, Ajustes) vive solo
   dentro de los paneles laterales. No hay barra inferior.
2. **Tocar una canción** la pone a sonar, y la cola sigue con el resto de esa lista.
   **Tocar una playlist** abre su detalle, con los botones «Reproducir» y «Aleatorio».
3. **Inicio.** «Recientes» son las canciones escuchadas hace menos tiempo.
   «Más escuchadas» cuenta los últimos 30 días.

## Ranking y notas

4. **«Sin nota»** enseña solo canciones escuchadas al menos una vez y sin nota, ordenadas por
   número de escuchas.
5. **Cambiar una nota:** se vuelve a tocar las estrellas en Ranking. Después de puntuar aparece
   «Deshacer» durante unos segundos.

## Buscar, escuchar antes y añadir

6. **Escuchar antes** (preview) reproduce la canción entera y pausa lo que estaba sonando. Al
   cerrar la escucha, vuelve lo que sonaba antes.
7. **El botón «+»** siempre abre un selector: elegir una playlist o solo la biblioteca. El progreso
   de la descarga se ve en la fila y en el aviso. En un álbum, el aviso dice «Descargando 3 de 12».
8. **Vídeos.** También se pueden escuchar antes. Su título se limpia solo (se quita «Official
   Video» y parecidos) y se puede editar después.
9. **Spotify.** «Pegar playlist de Spotify» e «Importar de Spotify» son la misma función: se pega
   el enlace, se crea la playlist y las coincidencias dudosas se confirman en una lista.

## Avisos y estados

10. Los avisos dependen de su tipo:
    - Los positivos salen abajo y se van solos.
    - Los errores se quedan hasta que se tocan, con «Reintentar».
    - Los estados vacíos llenan la pantalla, con botones. «Biblioteca vacía» lleva «Buscar» y
      «Vincular PC».
    - «Sin conexión» se queda arriba hasta que vuelve la red.

## Aspecto y movimiento

11. **Fantasmas.** Cada pantalla usa los fantasmas exactamente como en su diseño. La tabla de usos
    de la Paleta solo se aplica donde ninguna pantalla lo define.
12. **Parpadeo del neón** del fondo: solo en Inicio, y nunca si el sistema tiene activado reducir
    el movimiento.

## PC

13. **Paneles del PC** como en el diseño: se abren dejando el ratón 1 s en el borde o con un clic
    en la pestaña, y se cierran cuando el ratón sale. No se pueden fijar. El PC tiene igualmente
    todas las pantallas del móvil, adaptadas a una ventana grande: Buscar, Ranking, Reproductor
    con letra, Importar de Spotify y Ver todo.

## Reproductor

14. **Repetir** tiene tres modos: apagado, todo y una. Un punto indica que está activo y una
    insignia «1» indica repetir una.

## Buscar

15. Se mantiene «SIN LÍMITE» en la cabecera de Buscar.

## Accesibilidad

16. Las zonas táctiles miden al menos 44 px sin cambiar el aspecto. La pestaña lateral derecha no
    debe tapar los botones «+» de Buscar.

## Cómo se ha aplicado en el hito 1

- Las acciones de las decisiones 6, 7, 8 y 9 funcionan con datos de ejemplo: la escucha, la
  descarga y la búsqueda de coincidencias se simulan, pero las pantallas, los avisos y los
  estados son los definitivos.
- Las funciones que llegan más tarde (vincular con QR, ecualizador, temporizador, editar datos,
  ver la cola, vista de álbum y archivos propios) avisan con «llega en una próxima versión».
- Todas las animaciones son cortas (150–300 ms) y se quitan si el sistema pide reducir el
  movimiento.

## Hito 2: decisiones técnicas

Lo que el diseño no decía y se ha decidido al hacer la búsqueda, las descargas, la biblioteca y el
audio de verdad. No cambian ninguna de las 16 decisiones de arriba: las concretan.

### Buscar

- **NewPipe Extractor v0.26.5**, la última versión. Solo se publica en JitPack, así que
  `settings.gradle.kts` solo deja sacar de JitPack los grupos de TeamNewPipe (ninguna otra
  librería puede llegar desde allí).
- **Canciones y Álbumes** buscan en YouTube Music; **Vídeos**, en YouTube. La búsqueda sale sola
  350 ms después de dejar de escribir. Mientras carga se ven filas de esqueleto.
- **Si falla**, la pestaña lo dice en grande con «Reintentar» y el motivo: sin conexión, YouTube
  pide comprobar que no eres un robot, el vídeo no está disponible, o «YouTube ha cambiado algo».
- **Artista:** el del canal, sin «- Topic», «VEVO» ni «Official».
- **«EN BIBLIO»:** el resultado ya está en la biblioteca si hay una canción del mismo vídeo
  (`yt:<id>`) o con el mismo título y artista (sin contar tildes ni mayúsculas).
- **Carátulas:** la imagen más grande que da YouTube. Las de YouTube Music se piden a 800 px para
  guardarlas y a 226 px para las filas.
- **Álbumes:** al verlos en la lista se piden sus canciones (3 álbumes a la vez como mucho) para
  poner «N canciones» y saber cuáles ya tienes.

### Escuchar antes y descargar

- **Vista previa en streaming:** suena la canción entera sin descargarla, en un reproductor aparte.
  La cola principal se queda donde estaba y vuelve al cerrar (decisión 6).
- **El mejor audio:** de las pistas de solo audio que se bajan de una vez (no DASH ni HLS), la
  original (no la doblada) con más bitrate; si empatan, m4a. Suele ser Opus en WebM (unos
  160 kbps) o AAC en M4A (128 kbps). Se guarda tal cual, sin recodificar.
- **Por trozos de 1 MB** con «Range», porque YouTube frena las descargas de golpe. Si se corta,
  sigue donde se quedó.
- **Reintentos:** cada canción se reintenta sola hasta 3 veces si falla la red. Si YouTube pide
  verificación o el vídeo no está disponible, no se insiste: sale el error con «Reintentar»
  (decisión 10).
- **De una en una:** las canciones de un álbum (o de varias peticiones) se bajan de una en una, y
  el aviso cuenta «Descargando 3 de 12». Sin conexión se pausan y siguen al volver la red.
- **Títulos de vídeo:** se quitan «(Official Video)», «[Lyric Video]», «| Official Audio»,
  «M/V», «HD», «4K», las comillas y el artista de delante («Artista - Título», también con varios
  artistas: «x», «&», «feat.»). Se queda lo que informa: «(en directo)», «(remix…)», «cover»,
  «feat.». Se pueden corregir al añadir y después, en «⋯» › «Editar datos de la canción»
  (decisión 8).

### Letras (LRCLIB)

- **Sin clave.** Primero se pide la canción exacta (`/api/get`, con título, artista, álbum y
  duración). Si no tiene letra sincronizada, se busca (`/api/search`) y se elige la del mismo
  artista con la duración más parecida (±3 s).
- **Sin conexión:** la letra se guarda en la biblioteca al descargar. Si una canción no tiene
  letra, se recuerda, y no se vuelve a preguntar hasta pasada una semana.
- **Sincronizada:** el reproductor resalta la línea que suena (formato LRC, también con varias
  marcas por línea, `[offset:]` y marcas por palabra). Si solo hay letra sin tiempos, se ve
  entera; si es instrumental, lo dice.

### Biblioteca

- **SQLDelight**, y no Room: el proyecto ya es Kotlin Multiplatform con Android y JVM, y
  SQLDelight funciona igual en los dos, con el SQL escrito a mano y comprobado al compilar.
- **Una base de datos SQLite** (`freedomplayer.db`) con canciones, playlists, notas, rankings,
  historial de escuchas, letras y ajustes. Cada cambio se guarda en cuanto pasa.
- **Escuchas:** una escucha cuenta a los 30 s (o a la mitad, si la canción dura menos de un
  minuto). Cada escucha se guarda con su hora. De ahí salen «Recientes», «Más escuchadas»
  (últimos 30 días) y «Sin nota» (escuchadas al menos una vez y sin nota) (decisiones 3 y 4).
- **Dónde se guarda:**
  - En Android, en el almacenamiento privado de la app (`biblioteca/`).
  - En Windows, en `%LOCALAPPDATA%\FreedomPlayer`. No se usa la carpeta de itinerancia porque la
    música ocupa mucho.
  - En Linux, en `~/.local/share/FreedomPlayer`.
- **Copia de seguridad de Android:** a la nube no va la biblioteca, porque la música no cabe en
  los 25 MB de Google y la base de datos sin la música no sirve. Al pasar a un móvil nuevo por
  cable o wifi, sí.
- **La cola se recuerda:** al volver a abrir la app sale lo que sonaba, por donde iba, en pausa.

### Reproducción

- **Android: Media3** (ExoPlayer con MediaSessionService):
  - Sigue sonando con la pantalla apagada.
  - Tiene notificación, controles en la pantalla de bloqueo y botones de los auriculares.
  - Respeta el foco de audio: se pausa en una llamada y al quitar los auriculares.
  - La cola y el aleatorio los decide la app. Desde fuera (notificación, coche, reloj) se puede
    tocar play, pausa, siguiente, anterior y la barra, pero no cambiar el orden.
- **PC: FFmpeg** (JavaCV, que va dentro de la app, así que no hay que instalar nada) con la
  salida de audio de Java. Suena lo que se descarga (Opus, AAC) y también MP3, FLAC, WAV y OGG.
  Las teclas multimedia del teclado todavía no.
- **Si un archivo falla** (borrado, formato raro), sale el error y se pasa a la siguiente.
- **Volumen:** en el móvil manda el del sistema; en el PC, la barra de la app.
- **Repetir:** apagado para al final de la cola, en el principio de la última; «todo» vuelve a la
  primera; «una» repite la misma, y cada vuelta cuenta como otra escucha.

### Archivos propios

- En Android, «Añadir tus archivos de audio» (en Ajustes y abajo del panel de canciones) abre el
  selector del sistema.
- Los archivos se copian a la biblioteca y se leen sus etiquetas: título, artista, álbum,
  duración y carátula.
- El mismo archivo (mismo nombre y tamaño) no se duplica.
- En el PC llega más adelante.

### Lo que quedó para después del hito 2

- **Importar de Spotify de verdad** (hecho en el hito 3).
- **Sonido:** «Volumen igualado», «Fundido entre canciones» y el ecualizador guardaban el ajuste,
  pero aún no cambiaban el sonido (hecho en el hito 3).
- **Otras pantallas:** ver la cola y el temporizador (hechos en el hito 3); vincular con QR y
  elegir carpeta y calidad (siguen pendientes).

### Licencias

NewPipe Extractor es GPL-3.0, y FFmpeg es LGPL. Al llevarlos dentro, la app se tiene que
distribuir con una licencia compatible (GPL-3.0). El repositorio todavía no tiene archivo de
licencia: esa elección queda pendiente.

## Hito 3: decisiones técnicas

Lo que se ha decidido al hacer el sonido, la cola, el temporizador, las estadísticas y Spotify. Como
las del hito 2, concretan las 16 decisiones de arriba sin cambiarlas.

### Pruebas

- **Audio de Android en un emulador:** pruebas de instrumentación con WAV hechos en la propia
  prueba y ExoPlayer de verdad, contra el mismo APK que se publica. Cubren el final de la cola,
  repetir, un archivo que falta, aleatorio, la vista previa, los mandos de fuera
  (MediaController, como la notificación), el temporizador, el fundido, el volumen igualado y el
  ecualizador.
- **Capturas para compararlas con el diseño:** `CapturasTest` pinta las pantallas principales con
  los datos de ejemplo a 390×844 (móvil) y 1280×800 (PC). En `main`, el job «Capturas de
  pantalla» (el único con permiso de escritura) las sube a la rama huérfana `capturas-ci`, siempre
  con un solo commit.
  - Diferencias que quedan a propósito: las capturas del móvil no tienen barra de estado (todo
    sale unos 24 px más arriba) y los datos de ejemplo no son idénticos a los del diseño.
- **Red (solo aviso):** además de YouTube y LRCLIB, se lee una playlist pública de Spotify y se
  emparejan unas canciones con YouTube Music.

### Cola y temporizador

- **La cola** enseña lo que ya ha sonado, la que suena y lo que viene. Se reordena arrastrando ≡
  (y con las acciones de accesibilidad «Subir» y «Bajar») y se quita con × (con «Deshacer»). La
  que suena no se corta nunca. Con aleatorio se reordena el orden aleatorio, y al quitarlo vuelve
  el de la lista.
- **Temporizador:** 15, 30, 45 o 60 minutos, o «al acabar esta canción». Con esto último, el motor
  se queda en pausa al final de la que suena (en Android, `pauseAtEndOfMediaItems` de ExoPlayer),
  y con play sigue la siguiente.

### Sonido

- **Ecualizador de 5 bandas** (60 Hz, 230 Hz, 910 Hz, 3,6 kHz y 14 kHz; ±12 dB), con
  ecualizaciones hechas y bandas a mano. Se guarda en los ajustes.
  - **Android:** el del sistema (`android.media.audiofx.Equalizer`) sobre la sesión de audio del
    reproductor. Cada banda del móvil toma la ganancia de nuestras 5 bandas a su frecuencia
    (interpolando en escala logarítmica). Si el móvil no lo tiene, la hoja lo dice.
  - **PC:** filtros biquad propios (picos de la RBJ Audio EQ Cookbook) en nuestro propio camino del
    audio, y no los filtros de FFmpeg: así se cambian las bandas al momento, sin rehacer un grafo
    de filtros ni cortar el sonido, y los cálculos son los mismos que se prueban en `commonTest`.
    Para no saturar, se baja la entrada la mitad de la subida más alta.
- **Volumen igualado:** cada canción se mide una vez, en segundo plano, con la sonoridad integrada
  de EBU R128 (LUFS): con FFmpeg en el PC y con MediaCodec en Android. Las que pasan de −16 LUFS
  se bajan hasta ahí; las flojas no se suben, para no saturar. La medida se guarda en los ajustes
  (`loudness.<id>`), así que no hace falta cambiar la base de datos.
- **Fundido entre canciones:** 3, 6, 9 o 12 s (6 de serie), con curvas de igual potencia. En el
  PC se solapan dos lectores. En Android, un segundo reproductor (con la misma sesión de audio,
  para que le llegue el ecualizador, y sin pedir el foco) sigue con la que se va mientras el
  principal, el de la notificación, empieza la siguiente subiendo.

### Estadísticas

- Salen del historial de escuchas (cada escucha con su hora): **30 días**, **por mes** (con la
  hora local, y las barras de los últimos 12 meses) y **desde siempre**. Si una biblioteca antigua
  no tiene historial, «Siempre» usa los contadores de cada canción.
- **Lo más destacado:** tu n.º 1 del ranking, la que más suena con nota, la que más suena sin nota,
  tu artista mejor puntuado (con al menos dos canciones con nota) y, por meses, el descubrimiento
  (la que más suena de las que sonaron por primera vez ese mes).
- Los números suben contando y las barras crecen al aparecer; nada de eso si se pide reducir el
  movimiento.

### Spotify

- **Sin cuenta, clave ni secreto.** Se lee la página pública para insertar la playlist en otra web
  (`open.spotify.com/embed/playlist/<id>`), que trae el nombre, el dueño y las canciones (título,
  artistas y duración) en un JSON. Si no las trae, se prueba con la página normal de la playlist.
  La prueba de red lo comprueba en cada compilación.
- **Solo playlists de usuario** (como dice `freedomplayer.md`): las de id «37i9dQZ…» o cuyo dueño
  es «Spotify» no se importan. La playlist tiene que ser pública; si no, Spotify no la enseña y el
  aviso lo explica.
- **Cada canción se busca en YouTube Music** (tres a la vez) con el título, sin «- Remastered…», y
  el primer artista. Con lo encontrado:
  - **Segura:** el mismo título (o con solo «feat.», «with», «Remastered» o «From…» de
    diferencia), algún artista en común y no más de 10 s de diferencia. Entra en la playlist y se
    descarga.
  - **Dudosa:** otra versión («en directo», «acústica»…), otra duración, un título parecido, o el
    mismo título y duración con otro artista. Se confirma en la lista (decisión 9).
  - **Sin coincidencia:** con «Buscar» para encontrarla a mano.
- **La playlist nueva** lleva el nombre de la de Spotify (con « (2)» si ya hay una igual).
- **Si algo falla,** se dice por qué: la playlist es privada o no existe, Spotify no deja leerla
  ahora mismo, su página ha cambiado, o no se ha podido buscar en YouTube Music. Si la página
  enseña menos canciones de las que tiene la playlist, el resultado lo avisa.

### Lo que todavía no

- Vincular con QR y sincronizar (Syncthing).
- Elegir carpeta y calidad de las descargas.
- Añadir archivos propios en el PC.
- Las teclas multimedia del teclado en el PC.

## Hito 4: decisiones técnicas

### CI

- Los trabajos de Linux usan `ubuntu-24.04` fijo: el cambio de `ubuntu-latest` a Ubuntu 26
  (19 de octubre de 2026) no puede romper la compilación. Windows sigue en `windows-latest`.
- `actions/download-artifact` pasa a v8, que ya funciona con Node 24.

### PC

- **Teclas multimedia:** se registran como teclas rápidas de Windows (`RegisterHotKey`, con JNA)
  en un hilo propio que espera sus mensajes, así funcionan aunque la ventana no tenga el foco o
  esté en la bandeja. Si otra app ya las tiene, Windows no deja registrarlas y la app sigue sin
  ellas (sin errores). Fuera de Windows no se registra nada.
- **Archivos propios:** el selector de archivos del sistema (`FileDialog`, el de Windows). Los
  archivos se copian a la biblioteca (como en Android) y FFmpeg lee sus etiquetas y su carátula.
  El id sale del nombre y el tamaño del archivo, igual que en Android: el mismo archivo es
  siempre la misma canción.
- **Bandeja y mini-reproductor:** ya estaban; el menú suma «Anterior» y hay una captura del
  mini-reproductor flotante (06p) para comprobar que se pinta.

### Lo que todavía no

- Vincular con QR y sincronizar, y elegir carpeta y calidad de las descargas: llegan en el hito 5
  (ver abajo).

## Hito 5: decisiones técnicas

### Descargas: calidad y carpeta

- **Calidad:** Máxima, Normal y Ahorro. Se aplica al elegir la pista de audio que da NewPipe, igual
  en Android y en el PC: Máxima es la de más bitrate (lo de antes, y lo que viene puesto), Normal la
  más cercana a 128 kbps y Ahorro la de menos. Siguen valiendo las reglas de antes: solo pistas que
  se bajan de una vez, nunca la doblada si hay original y, si empatan, m4a. Solo cuenta para
  descargar: escuchar antes de añadir usa siempre la mejor.
- **Carpeta en el PC:** el selector de carpetas de Windows (`IFileOpenDialog` en modo carpetas,
  llamado por COM con JNA, que ya estaba por las teclas multimedia; no hace falta otra librería).
  Va en un hilo propio con COM en modo STA. Si no se puede abrir, o fuera de Windows, sale el de
  Swing.
  - Lo ya descargado no se mueve: cada canción guarda la ruta de su archivo, así que basta con que
    las nuevas vayan a la carpeta nueva. Las carátulas y las descargas a medias siguen en la
    carpeta de la app.
  - Si la carpeta elegida no existe y no se puede crear, o no se puede escribir en ella, se usa la
    de la app en vez de fallar la descarga.
  - Borrar los archivos de una canción (`deleteFiles`) fuera de la carpeta de la app solo toca el
    que bajó la app, que se llama como la canción (`yt_<id>.<ext>`).
- **Carpeta en Android:** no se cambia; la música sigue en el almacenamiento privado de la app.
  La sincronización con Syncthing necesita una ruta de verdad, y las carpetas que se eligen en
  Android (SAF) no lo son. Ajustes lo explica en vez de ofrecer el botón. Si el ajuste llegara al
  móvil desde otro sitio, se ignora.
- **Versión 0.5:** las compilaciones de prueba pasan a numerarse `0.5.<n.º de ejecución>`. El
  `versionCode` de Android sigue siendo el número de ejecución, que siempre sube.

### Sincronizar (Syncthing)

- Syncthing oficial (v2.1.6), descargado en CI de sus releases de GitHub y comprobado con su lista
  de sumas SHA-256; nunca se guarda en el repositorio. En Windows va dentro del paquete
  (`syncthing.exe`). En Android va como biblioteca nativa (`libsyncthing.so`), que Android deja
  ejecutar desde su carpeta:
  - arm64-v8a y armeabi-v7a: los binarios oficiales `linux-arm64` y `linux-arm`, que son
    estáticos.
  - x86_64 (el emulador y los Chromebook): el código fuente oficial de la misma versión, con su
    suma SHA-256, compilado para Android con el NDK. El binario oficial `linux-amd64` lleva SQLite
    con una libc estática que llama a `lstat()`, y el filtro seccomp de las apps de Android x86_64
    no deja esa llamada: Syncthing se cerraba al arrancar (SIGSYS). En ARM esas llamadas antiguas
    no existen.
- Se configura solo por su API REST local (127.0.0.1), con una clave que se genera en el
  dispositivo cada vez que se arranca.
- La biblioteca (playlists, notas, rankings e historial) va en un JSON por dispositivo en una
  carpeta compartida, nunca el archivo de SQLite. Gana el cambio más reciente, lo borrado no
  vuelve y las escuchas se suman sin repetir.
- Vincular: el PC enseña su id de Syncthing en un QR (ZXing) y el móvil lo escanea (ZXing
  embebido, sin servicios de Google). El móvil añade el PC y comparte con él dos carpetas con id
  fijo, `freedomplayer-musica` y `freedomplayer-biblioteca`. El PC, solo mientras enseña el QR,
  acepta al dispositivo que pide conectar (`/rest/cluster/pending/devices`) y comparte con él las
  mismas dos carpetas.
- Dónde se vincula: la insignia «SYNC · PC» de Inicio («SYNC · MÓVIL» en el PC), Ajustes ›
  «Vincular con el PC» («con el móvil» en el PC) y «VINCULAR PC» con la biblioteca vacía. El
  diseño solo nombra el QR en la biblioteca vacía; la insignia y Ajustes llevan a la misma
  pantalla.
- «Sincronizando con el PC 12 de 20»: de las canciones de la carpeta de música, las que ya están
  en los dos (lo que le falta a cada uno, según `/rest/db/completion`).
- Avisos, sin cerrar la app:
  - Ya vinculados, mientras el otro no aparece se ve «Buscando el PC…» (no «0 de 0»). Si pasa
    minuto y medio sin aparecer (Syncthing reintenta conectar cada minuto), se dice qué comprobar:
    la app abierta en el otro y la misma wifi. Se sigue esperando, y el aviso se quita al conectar.
  - Sin el binario de Syncthing, se dice que esa instalación no lo trae.
  - Si Syncthing no arranca o no se puede vincular, el aviso lleva «Reintentar».
  - Un código que no es de un dispositivo, o el del propio dispositivo, se explica sin vincular
    nada.
- Con la app abierta, la biblioteca se intercambia cada 20 s. Una canción del otro dispositivo
  aparece cuando su audio ya ha llegado. En Android, Syncthing va en un servicio en primer plano
  (`dataSync`) mientras se sincroniza.
- Pruebas: la mezcla del JSON, el intercambio, la API (con un servidor falso), la pantalla (con un
  Syncthing de mentira: vincular, códigos que no valen, el otro que no aparece y sin el binario) y,
  en la CI, dos Syncthing de verdad que se vinculan y se pasan una canción, su nota y una
  playlist, y el Syncthing del APK arrancando y vinculando en el emulador (Android 14, x86_64).

### Lo que todavía no

- Probarlo entre un móvil y un PC de verdad. En la CI, los dos Syncthing están en el mismo
  equipo, con las direcciones puestas a mano, y en el emulador solo corre el de x86_64: los
  binarios de ARM no se ejecutan en la CI.
- Fuera de la misma wifi puede no funcionar en los móviles ARM: el binario de Linux, en Android,
  probablemente no sabe resolver nombres (no hay `/etc/resolv.conf`), y sin eso no hay
  descubrimiento global ni relés. En la misma wifi se encuentran por descubrimiento local. El de
  x86_64, compilado para Android sin `netgo`, debería resolver con la libc de Android.
- Las carátulas no se sincronizan todavía: las canciones que llegan salen con su color.
- Preguntar «¿Vincular con este móvil?» en el PC. Ahora acepta al primero que pide conectar
  mientras enseña el QR.
- Ver y quitar los dispositivos vinculados.
- Sincronizar la música que se descarga en otra carpeta elegida en el PC: solo se comparte la
  carpeta de la app (la de `freedomplayer-musica`), así que esas canciones se quedan en el PC.
