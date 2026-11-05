package com.estudia.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {

    @Test
    fun comparesVersionsByNumber() {
        assertTrue(isNewer("1.0.1", "1.0.0"))
        assertTrue(isNewer("v1.10.0", "1.9.0"))
        assertTrue(isNewer("2.0.0", "1.99.99"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertFalse(isNewer("1.0.0", "1.0"))
        assertTrue(isNewer("1.0.1", "1.0"))
    }

    @Test
    fun unknownVersionFormatsNeverOfferAnUpdate() {
        assertFalse(isNewer("nightly", "1.0.0"))
        assertFalse(isNewer("1.0.1-beta", "1.0.0"))
    }

    @Test
    fun readsTheApkFromTheRelease() {
        val json = """
            {"tag_name":"v1.2.3","body":" Cambios \r\n","draft":false,"assets":[
              {"name":"notas.txt","size":5,"browser_download_url":"https://example.com/notas.txt"},
              {"name":"MisApuntes-v1.2.3.apk","size":1234,"browser_download_url":"https://example.com/a.apk"}
            ]}
        """
        assertEquals(Release("1.2.3", "Cambios", "https://example.com/a.apk", 1234), parseRelease(json))
    }

    @Test
    fun aReleaseWithoutApkIsIgnored() {
        assertNull(parseRelease("""{"tag_name":"v1.2.3","body":null,"assets":[]}"""))
    }
}
