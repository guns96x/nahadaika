package ua.nahadaika.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Звукова доріжка з відео-кружечка (без перекодування) — у Gemini йде лише звук, не відео. */
object AudioExtractor {
    /** true, якщо в [video] була звукова доріжка і її збережено в [out] (m4a). */
    fun extract(video: File, out: File): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(video.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(track)
            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val index = muxer.addTrack(extractor.getTrackFormat(track))
                muxer.start()
                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(index, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
            return out.length() > 0
        } catch (_: Exception) {
            out.delete()
            return false
        } finally {
            extractor.release()
        }
    }
}
