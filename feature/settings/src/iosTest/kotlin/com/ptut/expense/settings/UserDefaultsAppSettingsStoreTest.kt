package com.ptut.expense.settings

import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.slip.SlipOwner
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserDefaultsAppSettingsStoreTest {

    private val suite = "settings-test-" + Uuid.random()
    private val defaults = NSUserDefaults(suiteName = suite)
    private val ownerFile = NSTemporaryDirectory().toPath() / "$suite.json"
    private val store = UserDefaultsAppSettingsStore(defaults, ownerFile)

    @AfterTest
    fun cleanUp() {
        defaults.removePersistentDomainForName(suite)
        FileSystem.SYSTEM.delete(ownerFile)
    }

    @Test
    fun `the owner name is kept in the file and never in user defaults`() {
        store.write(AppChoices(slipOwner = SlipOwner(name = "MR. THAW Z")))

        assertEquals("MR. THAW Z", store.read().slipOwner.name)
        assertTrue(FileSystem.SYSTEM.exists(ownerFile))
        assertNull(defaults.stringForKey("ptut-slip-owner-name"))
    }

    @Test
    fun `a name an older build left in user defaults moves to the file once`() {
        defaults.setObject("MR. THAW Z", "ptut-slip-owner-name")
        defaults.setObject("MR. THAW Z", "ptut-slip-owner-candidate")

        val owner = store.read().slipOwner

        assertEquals("MR. THAW Z", owner.name)
        assertEquals("MR. THAW Z", owner.candidate)
        assertNull(defaults.stringForKey("ptut-slip-owner-name"))
        assertNull(defaults.stringForKey("ptut-slip-owner-candidate"))
        assertEquals("MR. THAW Z", UserDefaultsAppSettingsStore(defaults, ownerFile).read().slipOwner.name)
    }

    @Test
    fun `a file already written wins over a name left in user defaults`() {
        store.write(AppChoices(slipOwner = SlipOwner(name = "MS. MAY T")))
        defaults.setObject("MR. THAW Z", "ptut-slip-owner-name")

        assertEquals("MS. MAY T", store.read().slipOwner.name)
        assertNull(defaults.stringForKey("ptut-slip-owner-name"))
    }

    @Test
    fun `the count of slips seen stays under its old key`() {
        store.write(AppChoices(slipOwner = SlipOwner(candidate = "MR. THAW Z", seen = 1)))

        assertEquals(1L, defaults.integerForKey("ptut-slip-owner-seen"))
        assertEquals(SlipOwner(candidate = "MR. THAW Z", seen = 1), store.read().slipOwner)
    }

    @Test
    fun `forgetting the owner removes the file`() {
        store.write(AppChoices(slipOwner = SlipOwner(name = "MR. THAW Z")))
        store.write(AppChoices(slipOwner = SlipOwner()))

        assertFalse(FileSystem.SYSTEM.exists(ownerFile))
        assertNull(store.read().slipOwner.name)
    }

    @Test
    fun `a damaged file reads as no owner learned yet`() {
        FileSystem.SYSTEM.write(ownerFile) { writeUtf8("not json") }

        assertEquals(SlipOwner(), store.read().slipOwner)
    }

    @Test
    fun `a phone that finished first run with no play key keeps play on`() {
        val upgraded = UserDefaultsAppSettingsStore(defaults, ownerFile, firstRunFinished = { true })

        assertTrue(upgraded.read().play.on)
        assertNull(defaults.objectForKey("ptut-play-on"))
    }

    @Test
    fun `a fresh install starts with play off and keeps it after first run`() {
        assertFalse(UserDefaultsAppSettingsStore(defaults, ownerFile, firstRunFinished = { false }).read().play.on)

        assertFalse(UserDefaultsAppSettingsStore(defaults, ownerFile, firstRunFinished = { true }).read().play.on)
    }
}
