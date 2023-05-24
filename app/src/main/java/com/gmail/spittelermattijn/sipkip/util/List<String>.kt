package com.gmail.spittelermattijn.sipkip.util

fun List<String>.filterValidOpusPaths() = ArrayList<String>().apply {
    for (path in this@filterValidOpusPaths) {
        if (path matches """.+\.opus""".toRegex(RegexOption.IGNORE_CASE)) {
            // Do this substring dance, because then we preserve the case insensitivity of the Opus file extension.
            val pathWithoutExtension = path.substring(0 until path.length - ".opus".length)
            if (this@filterValidOpusPaths.any { it matches """.+\.opus_packets""".toRegex(RegexOption.IGNORE_CASE) &&
                        it.substring(0 until it.length - ".opus_packets".length) == pathWithoutExtension })
                add(pathWithoutExtension)
        }
    }
}