package com.aditya.powermonitor.data.export

/** Minimal CSV formatting (RFC 4180 quoting rules). */
object Csv {

    /** Joins fields into one CSV line; null becomes an empty field. */
    fun line(fields: List<Any?>): String = fields.joinToString(",", transform = ::escape)

    /**
     * A field containing a comma, quote or line break is wrapped in quotes, and quotes inside
     * it are doubled. App names like "Maps, Navigation" need this.
     */
    fun escape(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }
}
