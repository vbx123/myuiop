package com.subtitle.studio

import java.util.Locale

data class Sub(val id: Long, val startMs: Long, val endMs: Long, val text: String)

object SrtUtil {

    private const val TIME = "(?:(\\d+):)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})"
    private val CUE = Regex("$TIME\\s*-->\\s*$TIME")

    fun fmtSrt(ms: Long): String = fmt(ms, ',')

    fun fmtVtt(ms: Long): String = fmt(ms, '.')

    private fun fmt(ms: Long, sep: Char): String {
        val t = ms.coerceAtLeast(0L)
        val h = t / 3600000
        val m = (t / 60000) % 60
        val s = (t / 1000) % 60
        val x = t % 1000
        return String.format(Locale.US, "%02d:%02d:%02d%c%03d", h, m, s, sep, x)
    }

    fun fmtShort(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        val m = t / 60000
        val s = (t / 1000) % 60
        val x = t % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", m, s, x)
    }

    private fun fmtLrc(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        val m = t / 60000
        val s = (t / 1000) % 60
        val c = (t % 1000) / 10
        return String.format(Locale.US, "[%02d:%02d.%02d]", m, s, c)
    }

    fun toSrt(list: List<Sub>): String {
        val sb = StringBuilder()
        list.forEachIndexed { i, s ->
            sb.append(i + 1).append('\n')
            sb.append(fmtSrt(s.startMs)).append(" --> ").append(fmtSrt(s.endMs)).append('\n')
            sb.append(s.text).append("\n\n")
        }
        return sb.toString()
    }

    fun toVtt(list: List<Sub>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        list.forEach { s ->
            sb.append(fmtVtt(s.startMs)).append(" --> ").append(fmtVtt(s.endMs)).append('\n')
            sb.append(s.text).append("\n\n")
        }
        return sb.toString()
    }

    fun toLrc(list: List<Sub>): String {
        val sb = StringBuilder()
        list.forEach { s ->
            sb.append(fmtLrc(s.startMs)).append(s.text.replace("\n", " ")).append('\n')
        }
        return sb.toString()
    }

    private fun toMs(h: String, m: String, s: String, f: String): Long {
        val hh = if (h.isEmpty()) 0L else h.toLong()
        val frac = f.padEnd(3, '0').take(3).toLong()
        return ((hh * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + frac
    }

    /** يقرأ SRT و VTT */
    fun parse(raw: String): List<Sub> {
        val text = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val out = ArrayList<Sub>()
        var id = 1L
        for (block in text.split(Regex("\n\\s*\n"))) {
            val lines = block.trim().split("\n")
            val idx = lines.indexOfFirst { it.contains("-->") }
            if (idx < 0) continue
            val m = CUE.find(lines[idx]) ?: continue
            val g = m.groupValues
            val start = toMs(g[1], g[2], g[3], g[4])
            val end = toMs(g[5], g[6], g[7], g[8])
            val body = lines.drop(idx + 1).joinToString("\n").trim()
            if (body.isEmpty()) continue
            out.add(Sub(id++, start, end, body))
        }
        return out.sortedBy { it.startMs }
    }
}
