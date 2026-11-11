package com.chuhe.quickmax

object PageSourceDetector {
    private val RULES = listOf(
        "拼多多" to listOf("拼多多", "多多买菜", "pinduoduo"),
        "淘宝" to listOf("淘宝", "天猫", "taobao"),
        "菜鸟" to listOf("菜鸟", "裹裹", "cainiao", "身份码"),
        "京东" to listOf("京东"),
        "抖音" to listOf("抖音", "抖店")
    )

    fun detect(raw: String): String? {
        if (raw.isBlank()) return null
        val text = raw.lowercase()
        var best: String? = null
        var bestScore = 0
        for ((platform, keywords) in RULES) {
            var score = 0
            for (keyword in keywords) {
                val needle = keyword.lowercase()
                var from = 0
                while (true) {
                    val index = text.indexOf(needle, from)
                    if (index < 0) break
                    score++
                    from = index + needle.length
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = platform
            }
        }
        return if (bestScore > 0) best else null
    }
}
