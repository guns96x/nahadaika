package ua.nahadaika.update

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Накладає патч формату NHP1 на старий APK і повертає новий.
 * Це bsdiff, у якого блоки стиснуто zlib замість bzip2 (див. tools/make_update.py).
 */
object BsPatch {
    private val MAGIC = "NHP1".toByteArray()
    private const val HEADER = 28

    fun apply(old: ByteArray, patch: ByteArray): ByteArray {
        require(patch.size >= HEADER && patch.copyOfRange(0, 4).contentEquals(MAGIC)) { "Невідомий формат патча" }
        val newSize = le64(patch, 4)
        val ctrlLen = le64(patch, 12).toInt()
        val diffLen = le64(patch, 20).toInt()
        require(newSize in 0..Int.MAX_VALUE && ctrlLen >= 0 && diffLen >= 0 && HEADER + ctrlLen + diffLen <= patch.size) { "Пошкоджений патч" }
        var off = HEADER
        val ctrl = inflate(patch, off, ctrlLen).also { off += ctrlLen }
        val diff = inflate(patch, off, diffLen).also { off += diffLen }
        val extra = inflate(patch, off, patch.size - off)

        val out = ByteArray(newSize.toInt())
        var oldPos = 0L
        var newPos = 0
        var c = 0
        var d = 0
        var e = 0
        while (newPos < out.size) {
            require(c + 24 <= ctrl.size) { "Пошкоджений патч" }
            val x = offtin(ctrl, c)
            val y = offtin(ctrl, c + 8)
            val z = offtin(ctrl, c + 16)
            c += 24
            require(x >= 0 && y >= 0 && newPos + x + y <= out.size && d + x <= diff.size && e + y <= extra.size) { "Пошкоджений патч" }
            for (i in 0 until x.toInt()) {
                val o = oldPos + i
                val base = if (o >= 0 && o < old.size) old[o.toInt()].toInt() else 0
                out[newPos + i] = (diff[d + i].toInt() + base).toByte()
            }
            d += x.toInt()
            newPos += x.toInt()
            oldPos += x
            System.arraycopy(extra, e, out, newPos, y.toInt())
            e += y.toInt()
            newPos += y.toInt()
            oldPos += z
        }
        return out
    }

    private fun le64(b: ByteArray, off: Int): Long =
        (0 until 8).fold(0L) { acc, i -> acc or ((b[off + i].toLong() and 0xff) shl (8 * i)) }

    /** Число bsdiff: 8 байтів little-endian, старший біт — знак. */
    private fun offtin(b: ByteArray, off: Int): Long {
        var y = b[off + 7].toLong() and 0x7f
        for (i in 6 downTo 0) y = (y shl 8) or (b[off + i].toLong() and 0xff)
        return if (b[off + 7].toInt() and 0x80 != 0) -y else y
    }

    private fun inflate(b: ByteArray, off: Int, len: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(b, off, len)
            val out = ByteArrayOutputStream(maxOf(len * 3, 1024))
            val buf = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
