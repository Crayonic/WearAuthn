package me.henneke.wearauthn.fido

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

@ExperimentalUnsignedTypes
class SimpleTest : StringSpec({

    "Simple test should pass" {
        val result = 1 + 1
        result shouldBe 2
    }

    "String operations should work" {
        val text = "Hello World"
        text.length shouldBe 11
        text.contains("World") shouldBe true
    }

    "Boolean logic should work" {
        val isTrue = true
        val isFalse = false
        
        isTrue shouldBe true
        isFalse shouldBe false
        (isTrue && !isFalse) shouldBe true
    }
})
