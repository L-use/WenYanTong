package com.wenyantong.app

import com.wenyantong.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LearningModesTest {
    @Test fun twoFamiliesAutomaticallyChooseEnglishOrChinese() {
        assertEquals(2,TranslationMode.entries.size)
        assertEquals(Direction.EN_TO_ZH,TranslationMode.ENGLISH.direction("hello world"))
        assertEquals(Direction.ZH_TO_EN,TranslationMode.ENGLISH.direction("你好，世界"))
        assertEquals(Direction.ZH_TO_CLASSICAL,TranslationMode.CLASSICAL.direction("我们为什么要学习"))
        assertEquals(Direction.CLASSICAL_TO_ZH,TranslationMode.CLASSICAL.direction("学而时习之"))
    }
    @Test fun doubledUnicodeEscapesBecomeReadableAndDamagedTextIsRejected() {
        assertEquals("学而时习之",LearningText.clean("\\u5b66\\u800c\\u65f6\\u4e60\\u4e4b"))
        assertEquals("𠮷",LearningText.clean("\\ud842\\udfb7"))
        for(value in listOf("坏\uFFFD字","\uD842","\u0000"))try { LearningText.clean(value);fail("Invalid text accepted") } catch(expected: IllegalArgumentException) { }
    }
}
