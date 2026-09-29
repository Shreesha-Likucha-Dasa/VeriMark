package com.verimark.app.portable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VeriMarkPackageTest {

    private val sampleJson = """{"formatVersion":1,"app":"VeriMark","project":{"title":"T","mediaType":"VIDEO","mediaFile":"media/rec.mp4"},"markers":[]}"""

    private fun tempDir(): File {
        val f = File.createTempFile("verimark", "tmp")
        f.delete()
        f.mkdirs()
        return f
    }

    @Test
    fun safeEntryName_acceptsNormalNames() {
        assertTrue(VeriMarkPackage.isSafeEntryName("project.json"))
        assertTrue(VeriMarkPackage.isSafeEntryName("media/recording.mp4"))
        assertTrue(VeriMarkPackage.isSafeEntryName("media/sub/interview.m4a"))
    }

    @Test
    fun safeEntryName_rejectsTraversalAndAbsolute() {
        assertFalse(VeriMarkPackage.isSafeEntryName("../evil.txt"))
        assertFalse(VeriMarkPackage.isSafeEntryName("media/../../evil.txt"))
        assertFalse(VeriMarkPackage.isSafeEntryName("/absolute/path.txt"))
        assertFalse(VeriMarkPackage.isSafeEntryName("C:\\evil.txt"))
        assertFalse(VeriMarkPackage.isSafeEntryName("media//double.txt"))
        assertFalse(VeriMarkPackage.isSafeEntryName(""))
    }

    @Test
    fun write_readRaw_roundTripsMediaAndJson() {
        val mediaBytes = ByteArray(4096) { (it % 256).toByte() }
        val out = ByteArrayOutputStream()
        VeriMarkPackage.write(out, sampleJson, "media/rec.mp4", ByteArrayInputStream(mediaBytes))

        val dir = tempDir()
        try {
            val raw = VeriMarkPackage.readRaw(ByteArrayInputStream(out.toByteArray()), dir)
            assertEquals(sampleJson, raw.projectJson)
            assertTrue(raw.mediaFile.exists())
            assertTrue(mediaBytes.contentEquals(raw.mediaFile.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readRaw_rejectsMissingProjectJson() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("media/rec.mp4"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val dir = tempDir()
        try {
            assertThrows(VeriMarkPackage.PackageException::class.java) {
                VeriMarkPackage.readRaw(ByteArrayInputStream(out.toByteArray()), dir)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readRaw_rejectsMissingMedia() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(sampleJson.toByteArray())
            zip.closeEntry()
        }
        val dir = tempDir()
        try {
            assertThrows(VeriMarkPackage.PackageException::class.java) {
                VeriMarkPackage.readRaw(ByteArrayInputStream(out.toByteArray()), dir)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readRaw_rejectsTraversalEntry() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(sampleJson.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }
        val dir = tempDir()
        try {
            assertThrows(VeriMarkPackage.PackageException::class.java) {
                VeriMarkPackage.readRaw(ByteArrayInputStream(out.toByteArray()), dir)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readRaw_rejectsNonZipInput() {
        val dir = tempDir()
        try {
            assertThrows(VeriMarkPackage.PackageException::class.java) {
                VeriMarkPackage.readRaw(ByteArrayInputStream("not a zip".toByteArray()), dir)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
