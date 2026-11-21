package com.autopi.use_case

import com.autopi.autopieapp.domain.model.CloudCommandCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudCommandCompatibilityTest {
    @Test
    fun `manifest bounds support the QR generator version range`() {
        val compatibility = cloudManifestCompatibility(
            """
            compatibility:
              autopieMinVersion: "0.19.0-beta"
              autopieMaxVersion: "0.x"
            """.trimIndent()
        )

        assertEquals(false, compatibility.isCompatibleWith("0.18.9-beta"))
        assertEquals(true, compatibility.isCompatibleWith("0.19.0-beta"))
        assertEquals(true, compatibility.isCompatibleWith("0.20.9-beta"))
        assertEquals(true, compatibility.isCompatibleWith("0.999.999"))
        assertEquals(false, compatibility.isCompatibleWith("1.0.0-beta"))
        assertEquals(false, compatibility.isCompatibleWith("1.0.0"))
    }

    @Test
    fun `exact bounds are inclusive and compare numeric components`() {
        val compatibility = CloudCommandCompatibility("0.9.0", "0.20.9")

        assertEquals(true, compatibility.isCompatibleWith("0.9.0"))
        assertEquals(true, compatibility.isCompatibleWith("0.10.0"))
        assertEquals(true, compatibility.isCompatibleWith("0.20.9"))
        assertEquals(false, compatibility.isCompatibleWith("0.20.10"))
    }

    @Test
    fun `release comes after prerelease and prerelease numbers compare numerically`() {
        val compatibility = CloudCommandCompatibility("0.20.0-beta.2", "0.20.0-beta.10")

        assertEquals(false, compatibility.isCompatibleWith("0.20.0-alpha"))
        assertEquals(true, compatibility.isCompatibleWith("0.20.0-beta.3"))
        assertEquals(false, compatibility.isCompatibleWith("0.20.0"))
        assertEquals(false, CloudCommandCompatibility("0.20.0").isCompatibleWith("0.20.0-beta"))
        assertEquals(true, CloudCommandCompatibility("0.20.0-beta").isCompatibleWith("0.20.0"))
    }

    @Test
    fun `wildcard minor and unrestricted wildcard cover their version families`() {
        val compatibility = CloudCommandCompatibility("0.20.x", "0.20.x")

        assertEquals(true, compatibility.isCompatibleWith("0.20.99-beta"))
        assertEquals(false, compatibility.isCompatibleWith("0.19.99"))
        assertEquals(false, compatibility.isCompatibleWith("0.21.0"))
        assertEquals(true, CloudCommandCompatibility(autopieMaxVersion = "*").isCompatibleWith("9.0.0"))
    }

    @Test
    fun `missing or blank manifest constraints do not mark commands incompatible`() {
        assertEquals(true, cloudManifestCompatibility("name: Legacy").isCompatibleWith("0.20.9-beta"))
        assertEquals(
            CloudCommandCompatibility(),
            cloudManifestCompatibility("compatibility: { autopieMinVersion: '', autopieMaxVersion: ' ' }")
        )
        assertEquals(false, CloudCommandCompatibility(autopieMaxVersion = "0.19.0").isCompatibleWith("0.20.0"))
    }

    @Test
    fun `version prefix and build metadata do not affect compatibility`() {
        assertEquals(
            true,
            CloudCommandCompatibility("0.20.9-beta", "0.20.9-beta")
                .isCompatibleWith("v0.20.9-beta+debug.42")
        )
    }

    @Test
    fun `unrecognized constraints or app versions do not claim incompatibility`() {
        assertNull(CloudCommandCompatibility("latest").isCompatibleWith("0.20.9"))
        assertNull(CloudCommandCompatibility(autopieMaxVersion = "0.x.1").isCompatibleWith("0.20.9"))
        assertNull(CloudCommandCompatibility("0.19.0").isCompatibleWith("unknown"))
    }
}
