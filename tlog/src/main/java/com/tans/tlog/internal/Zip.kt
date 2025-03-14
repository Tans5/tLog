package com.tans.tlog.internal


import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun zipFiles(inputFiles: List<File>, outputFile: File): Boolean {
    return try {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        outputFile.outputStream().use { zipFiles(inputFiles = inputFiles, outputStream = it) }
        true
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }
}

internal fun zipFiles(inputFiles: List<File>): ByteArray? {
    return try {
        ByteArrayOutputStream().use {
            zipFiles(inputFiles = inputFiles, outputStream = it)
            it.toByteArray()
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

internal fun zipFiles(inputFiles: List<File>, outputStream: OutputStream) {
    val fixedInputFiles = inputFiles.filter { it.isFile && it.exists() }.map { it.canonicalPath }
    var entryNameCutIndex = 0
    if (fixedInputFiles.isNotEmpty()) {
        while (true) {
            var finish = false
            val c = fixedInputFiles[0].getOrNull(entryNameCutIndex) ?: break
            for (f in fixedInputFiles) {
                val fc = f.getOrNull(entryNameCutIndex)
                if (fc != c) {
                    finish = true
                    break
                }
            }
            if (finish) {
                break
            } else {
                entryNameCutIndex ++
            }
        }
    }

    outputStream.use { fos ->
        ZipOutputStream(fos).use { zos ->
            for (f in fixedInputFiles) {
                val entryName = f.substring(entryNameCutIndex).removeSuffix(File.separator).replace(File.separator, "/")
                val entry = ZipEntry(entryName)
                entry.method = ZipEntry.DEFLATED
                zos.putNextEntry(entry)
                FileInputStream(f).use {
                    it.copyTo(zos)
                }
            }
        }
    }
}

internal fun zipDir(baseDir: File, outputFile: File, filter: (f: File) -> Boolean = { true }): Boolean {
    return try {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        outputFile.outputStream().use {
            zipDir(baseDir = baseDir, outputStream = it, filter = filter)
        }
        true
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }
}

internal fun zipDir(baseDir: File, filter: (f: File) -> Boolean = { true }): ByteArray? {
    return try {
        ByteArrayOutputStream().use {
            zipDir(baseDir = baseDir, outputStream = it, filter = filter)
            it.toByteArray()
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

internal fun zipDir(baseDir: File, outputStream: OutputStream, filter: (f: File) -> Boolean = { true }) {
    val baseDirString = baseDir.canonicalPath
    fun writeZipFile(dir: File, zos: ZipOutputStream) {
        val children = (dir.listFiles() ?: emptyArray()).filter { filter(it) && it.canRead() }
        for (c in children) {
            if (c.isDirectory) {
                writeZipFile(c, zos)
            } else {
                val entryName = c.canonicalPath.substring(baseDirString.length).removeSuffix(File.separator).replace(File.separator, "/")
                val entry = ZipEntry(entryName)
                entry.method = ZipEntry.DEFLATED
                zos.putNextEntry(entry)
                FileInputStream(c).use {
                    it.copyTo(zos)
                }
            }
        }
    }
    outputStream.use { os ->
        ZipOutputStream(os).use { zos ->
            writeZipFile(baseDir, zos)
        }
    }
}