package com.wenyantong.app.core.model

enum class Direction(val title: String, val shortTitle: String, val hint: String) {
    CLASSICAL_TO_ZH("文言文 → 现代汉语", "古文 → 今译", "输入文言文，读懂字里行间…"),
    ZH_TO_CLASSICAL("现代汉语 → 文言文", "今文 → 古文", "输入现代汉语，查找收录的古文对照…"),
    ZH_TO_EN("中文 → English", "中文 → English", "输入中文，查找双语对照…"),
    EN_TO_ZH("English → 中文", "English → 中文", "Enter a word or a sentence…")
}
enum class Mode(val label: String) { AUTO("自动"), AI("AI"), OFFLINE("离线") }
data class Sense(val id: String, val gloss: String, val pos: String, val example: String, val translation: String, val source: String)
data class Entry(val id: String, val headword: String, val pinyin: String, val summary: String, val senses: List<Sense>, val packs: List<String> = emptyList(), val linkedIds: List<String> = listOf(id), val totalSenses: Int = senses.size)
data class Passage(val id: String, val original: String, val translation: String, val direction: Direction, val work: String, val analysis: String, val entryIds: List<String>, val sources: List<String> = emptyList())

data class LibraryArticle(val id: String, val packId: String, val title: String, val author: String, val dynasty: String)
data class ArticleText(val title: String, val author: String, val original: String, val translation: String, val notes: String, val source: String)
data class QueryResult(val query: String, val direction: Direction, val passage: Passage?, val entries: List<Entry>, val alternatives: List<Passage> = emptyList()) {
    val hasMatch get() = passage != null || entries.isNotEmpty()
}
data class HistoryItem(val id: Long, val query: String, val direction: Direction, val timestamp: Long, val favorite: Boolean)
data class Question(val id: String, val stem: String, val options: List<String>, val answer: Int, val explanation: String, val entryId: String)
data class Answer(val questionId: String, val selected: Int, val correct: Boolean)

object TextRules {
    fun normalize(text: String): String = text.trim().lowercase(java.util.Locale.ROOT)
        .filterNot { it.isWhitespace() || it in "，。？！；：、,.?!;:\"“”‘’（）()" }
    fun matches(left: String, right: String): Boolean = normalize(left).isNotEmpty() && normalize(left) == normalize(right)
    fun isCorrect(question: Question, choice: Int): Boolean = choice in question.options.indices && choice == question.answer
}
