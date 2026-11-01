package com.swp81x.nrsuite.core.wps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WpsPixieCrackerTest {
    @Test
    fun recoversPinFromTest11Material() {
        val material = WpsPixieMaterial.fromHex(
            pke = "9dd10d64b5ff918b6b13d3f0847e061aa74b54e64e438ed4a18ae7f66c1132a1" +
                "9f6f0746bfba6eab4e04f56f26d78013e896d66838d138fad3314fb8cfbb46f5" +
                "0b0ee2db46659517afa07b6140916a9929b2369d6914300763a03d53ce1070f0" +
                "4a7944ebc05bdf0f5c7658315f0838a2688e63276b6e32472e6755cf0d37a8e3" +
                "b1cb93476fd5082aa2e5695c2ff42f5ce1bddb02d3d4d525c538e0cd33faed30" +
                "48c1d762cdd7679e4ae591e644ba00032af86771287b8e932bc0b84bb706b441",
            pkr = "9e70b1e81d8cc949d95e86828be605e9679be599e8e943f713627aa0b0addc75" +
                "2d97a5d3bf9de7694e1c64fbb15c4539d5fc849f968e4cc28d45a1d87678755a" +
                "2f827be450c5bc8087fbe80741799a3252cee72915c7bfcd6c5f0112fab2fccd" +
                "9f9f68ca651eaf64379304b3e3ca7c3df0f5e48dd558997215f83b61265d0da9" +
                "273b775246d413a2ac94ae3c63104c66dc433793d88c15a1594de58401d3a4f9" +
                "e18eea5ad4be12891c667b6c8fc47d38b7962d161e9e56a65238f87eb7cb17eb",
            eNonce = "93462c26b00e3f301a7de0a0efdd6e22",
            rNonce = "9d7af085e417106fe04db83eda97c757",
            eHash1 = "4245b739300fffeb4ded595b287e6543a44467e3b2e9ebe84bddc3f485e67d04",
            eHash2 = "b4089d3e958058ce476f03060c72f7ec296856870eae71cf7a724f907946b603",
            authKey = "07ddfa15dff87bdf54a9ea0b7b73faed2fc4067acebca9851b134acb1f5f7d36",
            bssid = "b0:4e:26:cc:2e:f8",
            ssid = "FAMILYUBE_nomap",
            channel = 3,
        )

        val result = WpsPixieCracker.crack(material)
        assertNotNull(result)
        assertEquals("45922008", result!!.pin)
        assertEquals(WpsPixieMode.RT_MT_CL, result.mode)
        assertEquals("4a54e58fc2aec5b79339c3d0d5792792", result.psk1.toHex())
        assertEquals("469bd217d86341d9ee1a2405356c2a37", result.psk2.toHex())
        assertEquals("1803f767d5baa19ffe770800038956f0", result.eS1.toHex())
        assertEquals("bce8af10d438c8c8ab1d24df2f3b46c5", result.eS2.toHex())
    }

    @Test
    fun recoversPinFromTest14Material() {
        val material = WpsPixieMaterial.fromHex(
            pke = "73dded9391cce105cba62447e4af502c9e672378668be4d159ec516d20671063" +
                "76fbb6588fa738f804ed7aeddbd76def44db2ba1eb052e20936d8695974eba10" +
                "3035147898b4dcd23df23d680b803cb74b9a45e5062f4484b3fd2e468a57007c" +
                "a095948c47ac0190dc133c9c94f013a319ccd22aab10f873b2c47e8e3ff72988" +
                "af7c15763410d26710089d3357cc0099c8a8e7b7a5f35e64a4cc37e6adc24274" +
                "03f467e497f8992e4ab77f2fe9d52ddc026c25f2deb6e31696fa3de8a548db98",
            pkr = "51ea70e752dc87847fd4e5f7b4f0300df2a42fd5eb14677e496414270afa0af0" +
                "221c50139545db895d25d705718626864b625743491e336a7a101324ad5f3c9c" +
                "96a7f57c8a84fbecadc8b2ca539ea1f2db66a6fa083c29aa5561cf179bfe1dae" +
                "a34dfe186bb88d94244a4de2154058eb32022990389b45f013e60daef38da265" +
                "fc2ad242deecd79955994f6519769e571ff969e4f3dc2dbea8b2c7999b2d49fe" +
                "5a13bef71ad4fe0eee525604366eba4ac494835cfd594b9f6620073df1d26c61",
            eNonce = "7a623fdabf8069c6234c678bf1ba5d99",
            rNonce = "2ac0251cf52adf54dfce23cdfdd9b82e",
            eHash1 = "8963cbef8add98f419edb8897bfb23357af92dfd82a19ea2b419abadf3711ea4",
            eHash2 = "ed0e4f6addfbde2353a8d307edf0583db1e4e838d10fdd10fbf124db71afb71d",
            authKey = "babafd5323d531459888b55a489319ac26f9599baccfc8fb7df35f4ae6f6ecda",
            bssid = "b0:4e:26:cc:2e:f8",
            ssid = "FAMILYUBE_nomap",
            channel = 3,
        )

        val result = WpsPixieCracker.crack(material)
        assertNotNull(result)
        assertEquals("45922008", result!!.pin)
        assertEquals(WpsPixieMode.RT_MT_CL, result.mode)
        assertEquals("d9dcc598dd01c7d4434fbc6284d399cb", result.eS1.toHex())
        assertEquals("721126333a9b91bb71f044c38683fc41", result.eS2.toHex())
    }

    @Test
    fun recoversPinFromEcosSimplestMaterial() {
        val material = syntheticMaterial(
            eNonce = "51b6b7248d425390898eafbc459acba8",
            eS1 = "c166a754fdf243c0f93e9fecb54abbd8",
            eS2 = "311697846da233f069ee8f1c25faab08",
            eHash1 = "72ac0b5698f953ca10840d58bbf1233b9a65b71bc7c990dc75d127e68214a0f3",
            eHash2 = "154d991938b3ec3083e389b9c66199caaa6579267000a72c4490fd2d173ae2b6",
        )

        val result = WpsPixieCracker.crack(material)
        assertNotNull(result)
        assertEquals("45922008", result!!.pin)
        assertEquals(WpsPixieMode.ECOS_SIMPLEST, result.mode)
        assertEquals("c166a754fdf243c0f93e9fecb54abbd8", result.eS1.toHex())
        assertEquals("311697846da233f069ee8f1c25faab08", result.eS2.toHex())
    }

    @Test
    fun recoversPinFromRtlDateWindowMaterial() {
        val material = syntheticMaterial(
            eNonce = "77bf67a632c286c96755be15634ec853",
            eS1 = "77bf67a632c286c96755be15634ec853",
            eS2 = "253fe2956766abbe0d97299a4bd23778",
            eHash1 = "25a24a2eda77f16b6da944b4b4f6fcb8e53fe5bf805971673b57f188bd1ed744",
            eHash2 = "df5f05926b8356114a25ea9be02609c3d08eb63054a1ec683881babfdeb0f63d",
        )

        val result = WpsPixieCracker.crack(material, epochSeconds = 2_000_000_000L)
        assertNotNull(result)
        assertEquals("45922008", result!!.pin)
        assertEquals(WpsPixieMode.RTL, result.mode)
        assertEquals("77bf67a632c286c96755be15634ec853", result.eS1.toHex())
        assertEquals("253fe2956766abbe0d97299a4bd23778", result.eS2.toHex())
    }

    @Test
    fun recoversPinFromEcosSimpleMaterial() {
        val material = syntheticMaterial(
            eNonce = "459441c28b1ffaa37615fd6259aa3f2d",
            eS1 = "6dd6d5f90ccac832988fb0e8dedffe31",
            eS2 = "e3bea67f3ca4e4e88c75074c90dfae26",
            eHash1 = "ac64a80f0920038dbc6185f0dc74d569b2ea31864571e78939a020eb0c132e32",
            eHash2 = "a40484031ba3ffcb2cae6cc6a329976bd21e418a5299fb1c5c08124f71ffc0d4",
        )

        val result = WpsPixieCracker.crack(material)
        assertNotNull(result)
        assertEquals("45922008", result!!.pin)
        assertEquals(WpsPixieMode.ECOS_SIMPLE, result.mode)
        assertEquals("6dd6d5f90ccac832988fb0e8dedffe31", result.eS1.toHex())
        assertEquals("e3bea67f3ca4e4e88c75074c90dfae26", result.eS2.toHex())
    }

    @Test
    fun wpsChecksumMatchesKnownPin() {
        assertEquals(8, WpsPixieCracker.wpsPinChecksum(4_592_200))
        assertTrue(WpsPixieCracker.wpsPinIsValid(45_922_008))
    }

    private fun syntheticMaterial(
        eNonce: String,
        eS1: String,
        eS2: String,
        eHash1: String,
        eHash2: String,
    ): WpsPixieMaterial {
        val authKey = ByteArray(32) { it.toByte() }
        val pke = ByteArray(192) { ((it * 3 + 1) and 0xff).toByte() }
        val pkr = ByteArray(192) { ((it * 5 + 7) and 0xff).toByte() }
        return WpsPixieMaterial(
            pke = pke,
            pkr = pkr,
            eNonce = WpsPixieMaterial.hexToBytes(eNonce),
            rNonce = ByteArray(16) { 0x5a },
            eHash1 = WpsPixieMaterial.hexToBytes(eHash1),
            eHash2 = WpsPixieMaterial.hexToBytes(eHash2),
            authKey = authKey,
            bssid = "b0:4e:26:cc:2e:f8",
            ssid = "FAMILYUBE_nomap",
            channel = 3,
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
