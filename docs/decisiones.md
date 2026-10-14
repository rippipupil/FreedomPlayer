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

### Lo que todavía no

- **Importar de Spotify de verdad:** la pantalla está, pero sin los datos de ejemplo avisa de que
  llega más adelante (hito 3).
- **Sonido:** «Volumen igualado», «Fundido entre canciones» y el ecualizador guardan el ajuste,
  pero aún no cambian el sonido.
- **Otras pantallas:** ver la cola, vincular con QR, temporizador y elegir carpeta y calidad.

### Licencias

NewPipe Extractor es GPL-3.0, y FFmpeg es LGPL. Al llevarlos dentro, la app se tiene que
distribuir con una licencia compatible (GPL-3.0). El repositorio todavía no tiene archivo de
licencia: esa elección queda pendiente.
