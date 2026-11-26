package net.secorp.rssreader.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives [HeadSpin] with a synthetic 60 fps clock: idle sway stays in its
 * band, a fling coasts and then settles back into the sway, and pitch is
 * clamped however hard it is dragged.
 */
class HeadSpinTest {

    private val spin = HeadSpin()
    private var nowMs = 0L

    private fun tick(frames: Int) {
        repeat(frames) {
            nowMs += 16
            spin.step(nowMs, 0.016f)
        }
    }

    @Test
    fun idleSwayStaysInBand() {
        tick(60 * 20)
        assertEquals(HeadSpin.Mode.AUTO, spin.mode)
        assertTrue("yaw ${spin.yaw}", spin.yaw in -1.62f..-0.82f)
        assertTrue("pitch ${spin.pitch}", spin.pitch in -0.06f..0.06f)
    }

    @Test
    fun flingCoastsThenReturnsToSway() {
        tick(10)
        spin.dragStart()
        spin.dragBy(0.3f, 0f)
        assertEquals(HeadSpin.Mode.DRAG, spin.mode)
        val yawAtRelease = spin.yaw
        spin.release(vYaw = 8f, vPitch = 0f)

        tick(6)
        assertEquals(HeadSpin.Mode.COAST, spin.mode)
        assertTrue("fling should keep turning the same way", spin.yaw > yawAtRelease + 0.5f)

        tick(60 * 8)
        assertEquals(HeadSpin.Mode.AUTO, spin.mode)
        assertEquals(HeadSpin.autoYaw(nowMs), spin.yaw, 1e-6f)
        assertEquals(HeadSpin.autoPitch(nowMs), spin.pitch, 1e-6f)
    }

    @Test
    fun setDownWithoutFlingStillReturns() {
        spin.dragStart()
        spin.dragBy(1.0f, 0.2f)
        spin.release(0f, 0f)
        tick(60)
        assertEquals("waits before heading back", HeadSpin.Mode.COAST, spin.mode)
        tick(60 * 6)
        assertEquals(HeadSpin.Mode.AUTO, spin.mode)
    }

    @Test
    fun pitchIsClamped() {
        spin.dragStart()
        spin.dragBy(0f, 10f)
        assertEquals(HeadSpin.PITCH_LIMIT, spin.pitch, 0f)
        spin.release(0f, -50f)
        tick(30)
        assertEquals(-HeadSpin.PITCH_LIMIT, spin.pitch, 0f)
    }
}
