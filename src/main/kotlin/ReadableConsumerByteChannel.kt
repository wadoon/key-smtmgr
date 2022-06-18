import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

class ReadableConsumerByteChannel(
    private val rbc: ReadableByteChannel,
    private val onBytesRead: (Int) -> Unit
) : ReadableByteChannel {

    private var totalByteRead = 0

    override fun read(dst: ByteBuffer): Int {
        val nRead = rbc.read(dst)
        notifyBytesRead(nRead)
        return nRead
    }

    private fun notifyBytesRead(nRead: Int) {
        if (nRead <= 0) return
        totalByteRead += nRead
        onBytesRead(totalByteRead)
    }

    override fun isOpen(): Boolean = rbc.isOpen

    override fun close() {
        rbc.close()
    }
}