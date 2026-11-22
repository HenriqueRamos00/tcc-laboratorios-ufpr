package br.ufpr.lab_mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufpr.lab_mobile.session.EncryptedSessionStorage
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage = EncryptedSessionStorage(context)

    @After fun clear() { TestSession.clear(context) }

    @Test fun rememberedSessionRoundTripsWithoutPlaintextTokenAndLogoutDeletesIt() {
        TestSession.start(context)
        val original = context.portalApplication.sessions.current!!.copy(remembered = true)
        storage.write(original)
        val file = File(context.noBackupFilesDir, "portal-session")
        assertTrue(file.exists())
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(original.token))
        assertEquals(original, EncryptedSessionStorage(context).read())
        TestSession.clear(context)
        assertNull(storage.read())
        assertFalse(file.exists())
    }

    @Test fun corruptCiphertextRequiresNewLoginAndMemoryOnlySessionIsNotSaved() {
        TestSession.start(context)
        assertNull(storage.read())
        storage.write(context.portalApplication.sessions.current!!.copy(remembered = true))
        File(context.noBackupFilesDir, "portal-session").writeText("corrupt")
        assertNull(storage.read())
        assertFalse(File(context.noBackupFilesDir, "portal-session").exists())
    }
}
