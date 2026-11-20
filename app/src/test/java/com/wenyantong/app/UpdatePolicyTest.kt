package com.wenyantong.app

import com.wenyantong.app.core.model.Mode
import com.wenyantong.app.feature.update.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private fun update()=AppUpdate(3,"0.2.1","v0.2.1","https://github.com/L-use/WenYanTong/releases/download/v0.2.1/app.apk",1024,"a".repeat(64),26,"notes","https://github.com/L-use/WenYanTong/releases/tag/v0.2.1")
    @Test fun offlineAndDisabledModesNeverAutomaticallyConnect() {
        assertFalse(UpdatePolicy.shouldAutoCheck(Mode.OFFLINE,true,0,1,true))
        assertFalse(UpdatePolicy.shouldAutoCheck(Mode.AUTO,false,0,1,true))
        assertFalse(UpdatePolicy.shouldAutoCheck(Mode.AUTO,true,0,1,false))
        assertTrue(UpdatePolicy.shouldAutoCheck(Mode.AUTO,true,0,1,true))
        assertFalse(UpdatePolicy.shouldAutoCheck(Mode.AUTO,true,1000,1001,true))
        assertTrue(UpdatePolicy.shouldAutoCheck(Mode.AUTO,true,1000,1000+UpdatePolicy.CHECK_INTERVAL,true))
        assertTrue(UpdatePolicy.shouldAutoCheck(Mode.AUTO,true,1000,10,true))
    }
    @Test fun redirectsRejectDowngradesCredentialsAndLookalikeDomains() {
        assertTrue(UpdatePolicy.trustedConnection("https://release-assets.githubusercontent.com/file"))
        listOf("http://github.com/a","https://github.com.evil.test/a","https://github.com@evil.test/a","https://evilgithubusercontent.com/a","https://github.com:444/a").forEach { assertFalse(it,UpdatePolicy.trustedConnection(it)) }
    }
    @Test fun metadataPinsRepositoryVersionAndIntegrityBounds() {
        UpdatePolicy.validate(update(),"L-use/WenYanTong")
        listOf(update().copy(tag="v9.0.0"),update().copy(bytes=UpdatePolicy.MAX_APK_BYTES+1),update().copy(sha256=""),update().copy(apkUrl="https://github.com/attacker/repo/releases/download/v0.2.1/app.apk"),update().copy(apkUrl=update().apkUrl+"?redirect=1")).forEach { candidate ->
            assertTrue(runCatching { UpdatePolicy.validate(candidate,"L-use/WenYanTong") }.isFailure)
        }
    }
}
