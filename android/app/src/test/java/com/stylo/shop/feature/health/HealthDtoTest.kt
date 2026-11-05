package com.stylo.shop.feature.health

import com.stylo.shop.feature.health.data.HealthDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the status and ignores additive server fields`() {
        assertEquals(
            HealthDto("ok"),
            json.decodeFromString<HealthDto>("""{"status":"ok","uptimeSeconds":12}"""),
        )
    }
}
