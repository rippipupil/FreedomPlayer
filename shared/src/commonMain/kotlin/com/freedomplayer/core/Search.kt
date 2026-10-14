package com.freedomplayer.core

/** Un álbum del buscador, para «+ Álbum entero». */
data class AlbumResult(
    val title: String,
    val artist: String,
    val year: String,
    val coverColor: Long,
    /** Canciones del álbum (ids de resultados de tipo canción). */
    val songIds: List<String>,
)

object Search {
    private val stopWords = setOf("de", "del", "la", "las", "el", "los", "y", "a", "al", "en", "un", "una", "the")

    /** Quita tildes y mayúsculas: «Neón» encuentra «neon» y al revés. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text.lowercase()) {
            sb.append(
                when (c) {
                    'á', 'à', 'ä', 'â', 'ã' -> 'a'
                    'é', 'è', 'ë', 'ê' -> 'e'
                    'í', 'ì', 'ï', 'î' -> 'i'
                    'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
                    'ú', 'ù', 'ü', 'û' -> 'u'
                    'ñ' -> 'n'
                    'ç' -> 'c'
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    /** Singular aproximado: «luces» → «luz», «farolas» → «farola». */
    internal fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("ces") -> word.dropLast(3) + "z"
        word.length > 3 && word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    private fun tokens(text: String): List<String> =
        normalize(text).split(' ', ',', '-', '·', '—', '(', ')', '[', ']', '|', '/', '+', '.', '«', '»', '"', '\'')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Palabras útiles de la búsqueda (sin «de», «la»… salvo que no haya otras). */
    private fun words(query: String): List<String> {
        val all = tokens(query)
        return all.filter { it !in stopWords }.ifEmpty { all }.map(::stem).distinct()
    }

    /** Cuántas palabras de la búsqueda aparecen (como principio de palabra) en el texto. */
    private fun hits(haystack: String, words: List<String>): Int {
        val h = tokens(haystack).map(::stem)
        return words.count { w -> h.any { it.startsWith(w) } }
    }

    private fun SearchResult.text() = "$title $artist ${album.orEmpty()} ${note.orEmpty()}"

    /**
     * Resultados de un tipo (canciones o vídeos), como en YouTube: primero los que tienen
     * más palabras de la búsqueda, y en caso de empate, en el orden del catálogo.
     */
    fun results(catalog: List<SearchResult>, query: String, kind: ResultKind): List<SearchResult> {
        val w = words(query)
        if (w.isEmpty()) return emptyList()
        return catalog.asSequence()
            .filter { it.kind == kind }
            .map { it to hits(it.text(), w) }
            .filter { it.second > 0 }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** Álbumes cuyo título o artista coincide, o que contienen alguna canción encontrada. */
    fun albums(albums: List<AlbumResult>, catalog: List<SearchResult>, query: String): List<AlbumResult> {
        val w = words(query)
        if (w.isEmpty()) return emptyList()
        val songHits = results(catalog, query, ResultKind.Cancion).map { it.id }.toSet()
        return albums.filter { hits("${it.title} ${it.artist}", w) > 0 || it.songIds.any { id -> id in songHits } }
    }

    /** ¿Está ya en la biblioteca? Por id o por el mismo título y artista. */
    fun inLibrary(result: SearchResult, library: List<Song>): Boolean =
        library.any {
            it.id == result.id ||
                (normalize(it.title) == normalize(result.title) && normalize(it.artist) == normalize(result.artist))
        }
}
