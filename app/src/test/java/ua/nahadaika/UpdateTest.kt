package ua.nahadaika

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.nahadaika.update.BsPatch
import ua.nahadaika.update.UpdateInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateTest {
    private fun res(name: String) = javaClass.getResourceAsStream("/patch/$name")!!.readBytes()

    @Test fun patchRebuildsNewFileExactly() {
        assertArrayEquals(res("new.bin"), BsPatch.apply(res("old.bin"), res("old-to-new.patch")))
    }

    @Test fun rejectsForeignFormat() {
        assertThrows(IllegalArgumentException::class.java) { BsPatch.apply(res("old.bin"), "BSDIFF40garbage".toByteArray() + ByteArray(40)) }
    }

    private val json = """
        {"versionCode": 16, "versionName": "3.7", "notes": "Що нового",
         "apk": {"name": "Nahadaika-3.7.apk", "size": 20000000, "sha256": "AA"},
         "patches": [{"from": 15, "fromSha256": "BB", "name": "Nahadaika-15-to-16.patch", "size": 1800000, "sha256": "CC"}]}
    """.trimIndent()
    private val urls = mapOf(
        "Nahadaika-3.7.apk" to "https://x/apk",
        "Nahadaika-15-to-16.patch" to "https://x/patch",
        "update.json" to "https://x/json",
    )

    @Test fun choosesPatchForInstalledVersion() {
        val info = UpdateInfo.parse(json, urls, currentCode = 15)
        assertEquals(16, info.versionCode)
        assertEquals("https://x/patch", info.patch!!.url)
        assertEquals("bb", info.patchFromSha256)
        assertEquals(1_800_000, info.downloadSize)
    }

    @Test fun fullApkWhenNoPatchFromInstalledVersion() {
        val info = UpdateInfo.parse(json, urls, currentCode = 12)
        assertNull(info.patch)
        assertEquals("https://x/apk", info.apk.url)
        assertEquals("aa", info.apk.sha256)
        assertEquals(20_000_000, info.downloadSize)
    }

    @Test fun assetUrlsPointToTheReleaseTag() {
        val info = UpdateInfo.parse(json, UpdateInfo.releaseUrls(json, "guns96x/nahadaika"), currentCode = 15)
        assertEquals("https://github.com/guns96x/nahadaika/releases/download/v3.7/Nahadaika-3.7.apk", info.apk.url)
        assertEquals("https://github.com/guns96x/nahadaika/releases/download/v3.7/Nahadaika-15-to-16.patch", info.patch!!.url)
    }
}
