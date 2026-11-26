package com.david.carpcast

import android.util.AtomicFile
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Android rename replaces an existing destination. java.io.File.renameTo on Windows
 * does not. Keep the Android replace semantics when running the same AtomicFile API
 * in Robolectric on Windows; device tests use the real Android implementation.
 */
@Implements(AtomicFile::class)
class WindowsAtomicFileShadow {
    @RealObject private lateinit var real: AtomicFile

    @Implementation
    fun finishWrite(stream: FileOutputStream?) {
        if (stream == null) return
        stream.fd.sync()
        stream.close()
        Files.move(File(real.baseFile.path + ".new").toPath(), real.baseFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
