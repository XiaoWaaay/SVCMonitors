package com.svcmonitor.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

object BinEventParser {
    private const val MAGIC = 0x45435653
    private const val MAX_RECORD = 2048

    data class ParseResult(
        val events: List<StatusParser.SvcEvent>,
        val consumedBytes: Int
    )

    fun parse(buf: ByteArray): ParseResult {
        if (buf.isEmpty()) return ParseResult(emptyList(), 0)
        val out = ArrayList<StatusParser.SvcEvent>(128)
        var off = 0
        while (off + 12 <= buf.size) {
            val magic = u32le(buf, off)
            if (magic != MAGIC) {
                off += 1
                continue
            }
            val len = u32le(buf, off + 8)
            if (len <= 0 || len > MAX_RECORD) {
                off += 1
                continue
            }
            if (off + len > buf.size) break

            val bb = ByteBuffer.wrap(buf, off, len).order(ByteOrder.LITTLE_ENDIAN)
            bb.int
            bb.short
            bb.short
            bb.int
            val seq = bb.int.toLong() and 0xFFFF_FFFFL
            val nr = bb.int
            val tgid = bb.int
            val pid = bb.int
            val uid = bb.int
            val pc = bb.long
            val caller = bb.long
            val fp = bb.long
            val sp = bb.long
            val cloneFn = bb.long
            val ret = bb.long
            val a0 = bb.long
            val a1 = bb.long
            val a2 = bb.long
            val a3 = bb.long
            val a4 = bb.long
            val a5 = bb.long
            val btDepth = bb.int.coerceIn(0, 7)
            bb.int
            val bt = ArrayList<Long>(btDepth)
            for (i in 0 until 7) {
                val v = bb.long
                if (i < btDepth) bt.add(v)
            }
            val commBytes = ByteArray(16)
            bb.get(commBytes)
            val comm = commBytes.takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8)
            val descLen = (bb.short.toInt() and 0xFFFF).coerceIn(0, 1024)
            bb.short
            val descBytes = ByteArray(descLen)
            if (descLen > 0) bb.get(descBytes)
            val desc = descBytes.toString(Charsets.UTF_8)

            out.add(
                StatusParser.SvcEvent(
                    seq = seq,
                    nr = nr,
                    name = StatusParser.nrToName(nr),
                    tgid = tgid,
                    pid = pid,
                    uid = uid,
                    comm = comm,
                    pc = pc,
                    caller = caller,
                    fp = fp,
                    sp = sp,
                    bt = bt,
                    cloneFn = cloneFn,
                    ret = ret,
                    a0 = a0,
                    a1 = a1,
                    a2 = a2,
                    a3 = a3,
                    a4 = a4,
                    a5 = a5,
                    desc = desc
                )
            )

            off += len
        }
        return ParseResult(out, off)
    }

    private fun u32le(b: ByteArray, o: Int): Int {
        return (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
    }
}
