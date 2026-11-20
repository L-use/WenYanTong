package com.wenyantong.app

import com.wenyantong.app.core.model.Question
import com.wenyantong.app.core.model.TextRules
import org.junit.Assert.*
import org.junit.Test

class TextRulesTest {
    @Test fun punctuationAndWhitespaceDoNotPreventExactMatch() {
        assertTrue(TextRules.matches(" 学而时习之，不亦说乎？ ","学而时习之不亦说乎"))
        assertTrue(TextRules.matches("Hello."," hello "))
    }
    @Test fun partialAndUnknownTextMustNotClaimFullTranslation() {
        assertFalse(TextRules.matches("学而时习之", "学而时习之，不亦说乎？"))
        assertFalse(TextRules.matches("从未收录的一句话", "学而时习之，不亦说乎？"))
        assertFalse(TextRules.matches(" ？", "！"))
    }
    @Test fun gradingRejectsMissingAndOutOfRangeAnswers() {
        val q=Question("q","stem",listOf("a","b","c"),1,"explanation","zhi")
        assertTrue(TextRules.isCorrect(q,1))
        assertFalse(TextRules.isCorrect(q,0))
        assertFalse(TextRules.isCorrect(q,-1))
        assertFalse(TextRules.isCorrect(q,3))
    }
}
