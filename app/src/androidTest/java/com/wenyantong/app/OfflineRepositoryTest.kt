package com.wenyantong.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.model.Direction
import com.wenyantong.app.core.storage.LocalRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineRepositoryTest {
    private var installed: String?=null
    private var active: String?=null
    @org.junit.Before fun useBundledFixture() {
        val p=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("resource_updates",0)
        installed=p.getString("installed",null);active=p.getString("active",null);p.edit().clear().commit()
    }
    @org.junit.After fun restoreDownloadedLibraries() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("resource_updates",0).edit().clear().putString("installed",installed).putString("active",active).commit()
    }
    @Test fun bundledResourceSupportsLookupAndAssociatedPractice() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=LocalRepository(context)
        try {
            repo.initialize()
            val zhi=repo.query("之",Direction.CLASSICAL_TO_ZH)
            assertNull(zhi.passage)
            assertEquals(4,zhi.entries.single().senses.size)
            assertEquals(4,repo.questions(setOf("zhi")).size)
            val known=repo.query("学而时习之不亦说乎",Direction.CLASSICAL_TO_ZH)
            assertNotNull(known.passage)
            assertEquals("《论语·学而》1.1",known.passage!!.work)
            val unknown=repo.query("江山明月清风未收录",Direction.CLASSICAL_TO_ZH)
            assertNull(unknown.passage)
            assertFalse(unknown.hasMatch)
            val wordOnly=repo.query("未知文句之中",Direction.CLASSICAL_TO_ZH)
            assertNull(wordOnly.passage)
            assertTrue(wordOnly.entries.isNotEmpty())
        } finally { repo.close() }
    }
    @Test fun reverseDirectionsAndPersonalDataSurviveReopen() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        var repo=LocalRepository(context)
        try {
            repo.initialize()
            assertEquals("Hello.",repo.query("你好",Direction.ZH_TO_EN).passage!!.translation)
            assertEquals("你好。",repo.query("hello",Direction.EN_TO_ZH).passage!!.translation)
            val p=repo.query("学而时习之，不亦说乎？",Direction.CLASSICAL_TO_ZH).passage!!
            assertEquals(p.original,repo.query(p.translation,Direction.ZH_TO_CLASSICAL).passage!!.translation)
            val record=repo.history().first { it.query=="hello" && it.direction==Direction.EN_TO_ZH }
            repo.favorite(record.id,true)
            val q=repo.questions(setOf("zhi")).first()
            repo.recordAnswer("instrumentation",q,(q.answer+1)%q.options.size)
            repo.close()
            repo=LocalRepository(context)
            repo.initialize()
            assertTrue(repo.history().first { it.id==record.id }.favorite)
            assertTrue(repo.questions(wrongOnly=true).any { it.id==q.id })
        } finally { repo.close() }
    }
}
