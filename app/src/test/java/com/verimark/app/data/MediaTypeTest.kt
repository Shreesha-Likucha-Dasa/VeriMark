package com.verimark.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun fromStorage_returnsVideo() {
        assertEquals(MediaType.VIDEO, MediaType.fromStorage("VIDEO"))
    }

    @Test
    fun fromStorage_returnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromStorage("AUDIO"))
    }

    @Test
    fun fromStorage_defaultsToVideoForUnknownValues() {
        assertEquals(MediaType.VIDEO, MediaType.fromStorage("unknown"))
        assertEquals(MediaType.VIDEO, MediaType.fromStorage(""))
    }
}
