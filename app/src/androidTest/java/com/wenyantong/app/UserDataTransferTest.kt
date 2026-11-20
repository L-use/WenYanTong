package com.wenyantong.app

import android.app.Application
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.model.Direction
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.storage.UserBackupCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UserDataTransferTest {
    @Test fun documentUriExportPreviewAndImportRestoreUserRecords() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val repository=LocalRepository(context)
        val store=ViewModelStore()
        lateinit var vm: AppViewModel
        val file=File(context.cacheDir,"app-updates/backup-fixture.json").apply { parentFile!!.mkdirs() }
        try {
            repository.initialize()
            repository.query("document-transfer-marker",Direction.EN_TO_ZH)
            val marker=repository.history().first { it.query=="document-transfer-marker" }
            repository.favorite(marker.id,true)
            instrumentation.runOnMainSync { vm=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[AppViewModel::class.java] }
            withTimeout(10000) { vm.state.first { it.ready } }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
            instrumentation.runOnMainSync { vm.exportUserData(uri) }
            withTimeout(10000) { vm.state.first { !it.storage.busy } }
            assertTrue(file.isFile)
            assertTrue(UserBackupCodec.decode(file.readBytes()).history.any { it.query==marker.query && it.favorite })
            repository.deleteHistory(marker.id)
            instrumentation.runOnMainSync { vm.prepareImport(uri) }
            withTimeout(10000) { vm.state.first { !it.storage.busy } }
            assertNotNull(vm.state.value.storage.pending)
            instrumentation.runOnMainSync { vm.importPreparedData() }
            withTimeout(10000) { vm.state.first { !it.storage.busy } }
            assertTrue(vm.state.value.history.any { it.query==marker.query && it.favorite })
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            repository.close();file.delete()
        }
    }
}
