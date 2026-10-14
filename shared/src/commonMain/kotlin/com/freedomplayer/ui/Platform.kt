package com.freedomplayer.ui

/** Hora actual en milisegundos desde 1970 (para el historial de escuchas). */
expect fun nowMillis(): Long

/** Hora del día, de 0 a 23 (para el saludo de Inicio). */
expect fun currentHour(): Int
