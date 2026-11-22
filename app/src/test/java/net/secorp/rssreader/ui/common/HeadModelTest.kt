package net.secorp.rssreader.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forces [HeadModel]'s one-time geometry build on the JVM. The build is pure
 * Kotlin, so anything that throws during object initialisation (which would
 * surface on-device as an ExceptionInInitializerError the first time the
 * empty state renders) fails here in seconds without a device.
 */
class HeadModelTest {

    @Test
    fun geometryBuildsWithoutThrowing() {
        val points = HeadModel.pointCount
        val edges = HeadModel.edgeCount
        assertTrue("expected a dense point cloud, got $points", points > 1500)
        assertTrue("expected edges to roughly track point count, got $edges for $points points", edges > points)
        val neurons = HeadModel.neuronCount
        assertTrue("expected a sparse neuron scatter, got $neurons of $points", neurons in 20 until points / 4)
    }
}
