package com.freedomplayer.platform

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.Services
import com.freedomplayer.db.FpDatabase
import com.freedomplayer.jvm.AppDirs
import com.freedomplayer.jvm.SyncthingBackend
import com.freedomplayer.jvm.realServices
import java.io.File
import java.util.Properties

/** Dónde guarda la app del PC su biblioteca, y los servicios de verdad para el PC. */
object DesktopLibrary {
    /**
     * Carpeta de datos de la app: en Windows, «%LOCALAPPDATA%\FreedomPlayer» (no la de
     * itinerancia: la música ocupa mucho); en Linux, «~/.local/share/FreedomPlayer».
     */
    fun dataDir(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home").orEmpty()
        val base = when {
            os.contains("win") -> System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: "$home\\AppData\\Local"
            os.contains("mac") -> "$home/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
        }
        return File(base, "FreedomPlayer").also { it.mkdirs() }
    }

    fun dirs(): AppDirs = AppDirs(dataDir())

    /**
     * Syncthing del PC: el que va en el paquete (resources/windows/syncthing.exe, que el workflow
     * descarga y comprueba) o, al probar desde el código, el de desktopApp/resources.
     */
    fun sync(dirs: AppDirs): SyncthingBackend = SyncthingBackend(
        executable = {
            val packaged = System.getProperty("compose.application.resources.dir")?.let { File(it, "syncthing.exe") }
            listOfNotNull(packaged, File("desktopApp/resources/windows/syncthing.exe"), File("resources/windows/syncthing.exe")).firstOrNull { it.isFile }
        },
        home = File(dirs.root, "syncthing"),
        music = dirs.music,
        library = File(dirs.root, "sincronizar"),
        showsQr = true,
        qrCode = QrCodes::matrix,
    )

    fun services(engine: AudioEngine): Services {
        val dirs = dirs()
        dirs.root.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dirs.database.absolutePath}", Properties(), FpDatabase.Schema)
        return realServices(driver, dirs, engine, FfmpegLoudness, sync = sync(dirs))
    }
}
