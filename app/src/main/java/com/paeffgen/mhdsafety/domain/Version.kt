package com.paeffgen.mhdsafety.domain

/** Versionsnummer nach SemVer (1.2.3). Ein führendes "v" und Anhänge wie "-debug" werden ignoriert. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""")

        fun parse(text: String): Version? = pattern.find(text.trim())?.destructured?.let { (ma, mi, pa) ->
            Version(ma.toInt(), mi.toInt(), pa.ifEmpty { "0" }.toInt())
        }
    }
}
