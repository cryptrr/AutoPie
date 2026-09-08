package com.autopi.autopieapp.data.services

import java.io.File

internal fun resolveObserverDirectory(base: File, path: String): File =
    (if (File(path).isAbsolute) File(path) else File(base, path)).canonicalFile

internal fun resolveObservedFile(directory: File, name: String): File? {
    if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\u0000' in name) return null
    val file = File(directory, name).canonicalFile
    return file.takeIf { it.parentFile == directory.canonicalFile && it.isFile && it.canRead() }
}

/** Tracks only new files; a later edit of an existing file must not rerun a command. */
internal class ObserverFileEvents(selectors: List<String>) {
    // Linux inotify / Android FileObserver event bits. Kept here for plain JVM tests.
    private val selectors = selectors.map { Regex(it) }
    private val created = linkedSetOf<String>()
    private var closed = false

    @Synchronized
    fun onEvent(event: Int, path: String?): String? {
        if (closed || path.isNullOrEmpty() || event and 0x40000000 != 0 ||
            path == "." || path == ".." || '/' in path || '\u0000' in path) return null
        if (path.startsWith(".pending") || path.contains(".conv") ||
            (selectors.isNotEmpty() && selectors.none { it.matches(path) })) return null
        if (event and (0x200 or 0x40) != 0) { // DELETE / MOVED_FROM
            created.remove(path)
            return null
        }
        if (event and 0x100 != 0) { // CREATE
            if (created.size >= 1024) created.remove(created.first())
            created.add(path)
        }
        if (event and 0x80 != 0) { // MOVED_TO: use the final name, never guess a pending rename.
            created.remove(path)
            return path
        }
        return path.takeIf { event and 0x8 != 0 && created.remove(path) } // CLOSE_WRITE
    }

    @Synchronized
    fun close() {
        closed = true
        created.clear()
    }
}
