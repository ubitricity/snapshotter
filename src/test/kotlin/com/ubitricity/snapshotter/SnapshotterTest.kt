/**
 * Copyright (c), ubitricity Gesellschaft für Verteilte Energiesysteme mbH,
 * Berlin, Germany
 *
 * All rights reserved. Dissemination, reproduction, or use of this material in source
 * and binary forms requires prior written permission from ubitricity.
 */
package com.ubitricity.snapshotter

import org.junit.Test

class SnapshotterTest {
    private val snapshotter = Snapshotter(snapshotsDirectory = "src/test/resources/__snapshot__")

    @Test
    fun `test simple json`() {
        val data = mapOf(
                "foo" to "bar",
                "numbers" to listOf(1, 2, 3, 4)
        )
        snapshotter.validateSnapshot(data)
    }

    @Test
    fun `test simple string`() {
        snapshotter.validateSnapshot("test data")
    }

    @Test
    fun `test null`() {
        snapshotter.validateSnapshot(null)
    }

    @Test
    fun `test ignoring array order`() {
        // Saved snapshot contains sequential numbers
        snapshotter.validateSnapshot(listOf(1, 4, 5, 2, 3))
    }
}
