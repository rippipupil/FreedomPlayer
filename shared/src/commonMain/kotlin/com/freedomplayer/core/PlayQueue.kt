package com.freedomplayer.core

import kotlin.random.Random

/**
 * Repetir. La cola nunca se para al acabar: [All] es «seguir» (vuelve a empezar; en aleatorio, con
 * otro orden) y [One] repite la canción que suena. [Off] es de las versiones de antes de la 0.9,
 * que paraban al acabar: se trata como [All] (ver [effective]); los motores aún lo entienden.
 */
enum class RepeatMode {
    Off, All, One;

    /** Lo que de verdad hace: «no repetir» ya no existe y vale como «seguir». */
    val effective: RepeatMode get() = if (this == Off) All else this

    /** Orden del botón: seguir ⇄ repetir una. */
    fun next(): RepeatMode = if (this == One) All else One
}

/**
 * Cola de reproducción: qué suena, qué viene después, aleatorio y repetir.
 * Es inmutable: cada operación devuelve una cola nueva.
 */
data class PlayQueue(
    /** Canciones (ids) en el orden en que van a sonar. */
    val items: List<String> = emptyList(),
    /** Posición en [items] de la que suena ahora; -1 si la cola está vacía. */
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.All,
    /** Orden sin aleatorio, para volver a él al quitarlo. */
    val original: List<String> = items,
) {
    val current: String? get() = items.getOrNull(index)

    /** Lo que suena después de la actual, en orden. */
    val upcoming: List<String> get() = if (index < 0) items else items.drop(index + 1)

    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * Pasa a la siguiente canción. [auto] indica que la actual ha terminado sola: con «repetir
     * una» vuelve a sonar la misma. Después de la última viene la primera (la cola nunca se para).
     * Devuelve null solo si la cola está vacía.
     */
    fun next(auto: Boolean = false): PlayQueue? {
        if (items.isEmpty()) return null
        if (auto && repeat == RepeatMode.One) return this
        return if (index < items.lastIndex) copy(index = index + 1) else copy(index = 0)
    }

    /** Vuelve a la anterior. Desde la primera salta a la última. */
    fun previous(): PlayQueue {
        if (items.isEmpty()) return this
        return if (index > 0) copy(index = index - 1) else copy(index = items.lastIndex)
    }

    /**
     * Aleatorio, sonando la última: la que suena pasa a ser la primera y las demás se mezclan otra
     * vez, así la vuelta siguiente lleva otro orden y cada canción suena una vez por vuelta.
     */
    val needsReshuffle: Boolean
        get() = shuffle && repeat != RepeatMode.One && items.size > 2 && index == items.lastIndex

    fun reshuffled(random: Random = Random.Default): PlayQueue {
        val now = current ?: return this
        return copy(items = listOf(now) + items.filterIndexed { i, _ -> i != index }.shuffled(random), index = 0)
    }

    fun jumpTo(position: Int): PlayQueue = if (position in items.indices) copy(index = position) else this

    fun cycleRepeat(): PlayQueue = copy(repeat = repeat.next())

    fun toggleShuffle(random: Random = Random.Default): PlayQueue = withShuffle(!shuffle, random)

    /** Activa o quita el aleatorio. La canción que suena sigue sonando (y queda la primera al mezclar). */
    fun withShuffle(on: Boolean, random: Random = Random.Default): PlayQueue {
        if (on == shuffle) return this
        val now = current
        return if (on) {
            val rest = items.filterIndexed { i, _ -> i != index }.shuffled(random)
            copy(
                items = if (now != null) listOf(now) + rest else rest,
                index = if (now != null) 0 else -1,
                shuffle = true,
                original = items,
            )
        } else {
            val back = original
            copy(
                items = back,
                index = if (back.isEmpty()) -1 else back.indexOf(now).coerceAtLeast(0),
                shuffle = false,
                original = back,
            )
        }
    }

    /** «Poner a continuación»: suena justo después de la actual. */
    fun playNext(id: String): PlayQueue {
        if (items.isEmpty()) return PlayQueue(listOf(id), 0, shuffle, repeat)
        val newItems = items.toMutableList().apply { add(index + 1, id) }
        val at = original.indexOf(current)
        val newOriginal = original.toMutableList().apply { if (at >= 0) add(at + 1, id) else add(id) }
        return copy(items = newItems, original = newOriginal)
    }

    /** Añade al final de la cola. */
    fun add(id: String): PlayQueue {
        if (items.isEmpty()) return PlayQueue(listOf(id), 0, shuffle, repeat)
        return copy(items = items + id, original = original + id)
    }

    /** Quita una canción de la cola (la que suena no se puede quitar). */
    fun remove(position: Int): PlayQueue {
        if (position !in items.indices || position == index) return this
        val id = items[position]
        val newItems = items.toMutableList().apply { removeAt(position) }
        val newOriginal = original.toMutableList().apply { remove(id) }
        return copy(items = newItems, original = newOriginal, index = if (position < index) index - 1 else index)
    }

    /**
     * Cambia de sitio una canción de la cola (también la que suena, que sigue sonando). Sin
     * aleatorio, el orden nuevo es el de la cola; con aleatorio, el orden original no cambia.
     */
    fun move(from: Int, to: Int): PlayQueue {
        if (from !in items.indices || to !in items.indices || from == to) return this
        val newItems = items.toMutableList().apply { add(to, removeAt(from)) }
        val newIndex = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        return copy(items = newItems, index = newIndex, original = if (shuffle) original else newItems)
    }

    companion object {
        /** Empieza a sonar [startId] dentro de [ids] (una playlist, la biblioteca…). */
        fun of(
            ids: List<String>,
            startId: String? = ids.firstOrNull(),
            shuffle: Boolean = false,
            repeat: RepeatMode = RepeatMode.All,
            random: Random = Random.Default,
        ): PlayQueue {
            if (ids.isEmpty()) return PlayQueue(shuffle = shuffle, repeat = repeat)
            val start = ids.indexOf(startId).takeIf { it >= 0 } ?: 0
            val base = PlayQueue(items = ids, index = start, repeat = repeat)
            return if (shuffle) base.withShuffle(true, random) else base
        }
    }
}
