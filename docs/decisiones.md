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
