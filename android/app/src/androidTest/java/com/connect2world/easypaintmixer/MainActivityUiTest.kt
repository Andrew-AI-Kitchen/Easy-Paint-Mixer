package com.connect2world.easypaintmixer

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    private val packageName = "com.connect2world.easypaintmixer"
    private val launchTimeout = 5_000L
    private val uiTimeout = 3_000L

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun resetAppState() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("easy_paint_mixer_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        revokeCameraPermission()
        launchApp()
    }

    @After
    fun leaveApp() {
        device.pressHome()
    }

    @Test
    fun firstUseGuidanceAppearsBeforeCameraPermissionRequest() {
        assertTextVisible("Choose a matching workflow")
        assertTextVisible("Camera permission needed")
        assertTextVisible("Allow camera")
    }

    @Test
    fun chineseCameraToggleCanCloseAndReopen() {
        tapText("Got it")
        tapText("Settings")
        tapText("简体中文")
        tapText("取色")

        tapText("开启")
        assertTextVisible("关闭")
        tapText("关闭")
        assertTextVisible("开启")
    }

    @Test
    fun restoreDefaultsKeepsLanguageAndResetsSuggestionMode() {
        tapText("Got it")
        tapText("Settings")
        tapText("简体中文")
        tapText("HSV")
        tapText("恢复默认")

        assertTextVisible("简体中文")
        assertTextVisible("Lab")
    }

    private fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("Cannot find launch intent for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
    }

    private fun tapText(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), uiTimeout)
            ?: error("Cannot find text: $text\n${device.dumpWindowHierarchyText()}")
        node.click()
        device.waitForIdle()
    }

    private fun assertTextVisible(text: String) {
        assertTrue(
            "Expected text is not visible: $text\n${device.dumpWindowHierarchyText()}",
            device.wait(Until.hasObject(By.text(text)), uiTimeout)
        )
    }

    private fun revokeCameraPermission() {
        runCatching {
            device.executeShellCommand("pm revoke $packageName ${Manifest.permission.CAMERA}")
        }
    }

    private fun UiDevice.dumpWindowHierarchyText(): String {
        return runCatching {
            val output = StringBuilder()
            findObjects(By.pkg(packageName)).forEach { node ->
                output.append(node.text.orEmpty()).append('\n')
            }
            output.toString()
        }.getOrDefault("")
    }
}
