package com.example

import com.example.util.ApkExporter
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkExporterTest {

    @Test
    fun formatByteSize_formatsZeroKbAndMb() {
        assertEquals("0 KB", ApkExporter.formatByteSize(0L))
        assertEquals("512 KB", ApkExporter.formatByteSize(512L * 1024L))
        assertEquals("1.5 MB", ApkExporter.formatByteSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("18.4 MB", ApkExporter.formatByteSize((18.4 * 1024 * 1024).toLong()))
    }
}
