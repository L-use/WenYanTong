package com.wenyantong.app.core.model

enum class Direction(val title: String, val shortTitle: String, val hint: String) {
    CLASSICAL_TO_ZH("文言文 → 现代汉语", "古文 → 今译", "输入文言文，读懂字里行间…"),
    ZH_TO_CLASSICAL("现代汉语 → 文言文", "今文 → 古文", "输入现代汉语，查找收录的古文对照…"),
    ZH_TO_EN("中文 → English", "中文 → English", "输入中文，查找双语对照…"),
    EN_TO_ZH("English → 中文", "English → 中文", "Enter a word or a sentence…")
}
enum class Mode(val label: String) { AUTO("自动"), AI("AI"), OFFLINE("离线") }
enum class TranslationMode(val title: String,val hint: String) {
    CLASSICAL("文言白话互译","输入文言文或现代汉语，自动判断方向…"),
    ENGLISH("中英互译","输入中文或英文，自动判断方向…");
    fun direction(text: String): Direction = if(this==ENGLISH) {
        if(text.count { it in 'a'..'z' || it in 'A'..'Z' }>text.count { it.code in 0x4e00..0x9fff })Direction.EN_TO_ZH else Direction.ZH_TO_EN
    } else if(Regex("我们|你们|他们|现在|什么|为什么|怎么|这个|那个|没有|可以|因为|所以|学习了").containsMatchIn(text))Direction.ZH_TO_CLASSICAL else Direction.CLASSICAL_TO_ZH
    companion object { fun of(direction: Direction)=if(direction in listOf(Direction.EN_TO_ZH,Direction.ZH_TO_EN))ENGLISH else CLASSICAL }
}
enum class QuestionKind(val title: String) { SINGLE("单选题"), TRUE_FALSE("判断题"), FILL_BLANK("选词填空") }
data class Sense(val id: String, val gloss: String, val pos: String, val example: String, val translation: String, val source: String)
data class Entry(val id: String, val headword: String, val pinyin: String, val summary: String, val senses: List<Sense>, val packs: List<String> = emptyList(), val linkedIds: List<String> = listOf(id), val totalSenses: Int = senses.size, val hasMore: Boolean=false, val aliases: List<String> = emptyList())
data class Passage(val id: String, val original: String, val translation: String, val direction: Direction, val work: String, val analysis: String, val entryIds: List<String>, val sources: List<String> = emptyList(),val aiGenerated: Boolean=false)

data class LibraryArticle(val id: String, val packId: String, val title: String, val author: String, val dynasty: String)
data class ArticleText(val title: String, val author: String, val original: String, val translation: String, val notes: String, val source: String)
data class QueryResult(val query: String, val direction: Direction, val passage: Passage?, val entries: List<Entry>, val alternatives: List<Passage> = emptyList()) {
    val hasMatch get() = passage != null || entries.isNotEmpty()
    val exactWordHit get() = entries.any { TextRules.matches(it.headword,query) || it.aliases.any { alias -> TextRules.matches(alias,query) } }
}
data class HistoryItem(val id: Long, val query: String, val direction: Direction, val timestamp: Long, val favorite: Boolean)
data class Question(val id: String, val stem: String, val options: List<String>, val answer: Int, val explanation: String, val entryId: String,val kind: QuestionKind=QuestionKind.SINGLE,val edited: Boolean=false)
data class Answer(val questionId: String, val selected: Int, val correct: Boolean)

object TextRules {
    fun normalize(text: String): String = text.trim().lowercase(java.util.Locale.ROOT)
        .filterNot { it.isWhitespace() || it in "，。？！；：、,.?!;:\"“”‘’（）()" }
    fun matches(left: String, right: String): Boolean = normalize(left).isNotEmpty() && normalize(left) == normalize(right)
    fun isCorrect(question: Question, choice: Int): Boolean = choice in question.options.indices && choice == question.answer
}
