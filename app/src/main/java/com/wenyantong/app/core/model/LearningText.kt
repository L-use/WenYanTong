package com.wenyantong.app.core.model

object LearningText {
    fun clean(value: String): String {
        var result=value.removePrefix("\uFEFF")
        repeat(2) { result=Regex("\\\\u([0-9a-fA-F]{4})").replace(result) { it.groupValues[1].toInt(16).toChar().toString() } }
        require('\uFFFD' !in result && result.none { it.isISOControl() && it !in "\n\r\t" }) { "内容存在编码异常，请重新生成或编辑后重试。" }
        var i=0
        while(i<result.length) {
            val c=result[i]
            if(c.isHighSurrogate()) { require(i+1<result.length && result[i+1].isLowSurrogate()) { "内容包含无效字符，请重新生成。" };i++ }
            else require(!c.isLowSurrogate()) { "内容包含无效字符，请重新生成。" }
            i++
        }
        return result
    }
}
