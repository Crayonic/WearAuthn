package me.henneke.wearauthn.fido.ctap2

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

/**
 * Unit tests for CTAP2 Authenticator implementation
 *
 * Tests cover:
 * - Basic authenticator object validation
 * - Error constants verification
 * - Command constants verification
 */
@ExperimentalUnsignedTypes
class AuthenticatorTest : StringSpec({

    "Authenticator object should exist" {
        Authenticator shouldNotBe null
    }

    "CTAP error constants should have correct values" {
        CtapError.InvalidLength.value shouldBe 0x03.toByte()
        CtapError.InvalidCommand.value shouldBe 0x01.toByte()
        CtapError.InvalidCbor.value shouldBe 0x12.toByte()
        CtapError.RequestTooLarge.value shouldBe 0x04.toByte()
        CtapError.OperationDenied.value shouldBe 0x27.toByte()
    }

    "Request commands should have correct values" {
        RequestCommand.MakeCredential.value shouldBe 0x01.toByte()
        RequestCommand.GetAssertion.value shouldBe 0x02.toByte()
        RequestCommand.GetInfo.value shouldBe 0x04.toByte()
        RequestCommand.Reset.value shouldBe 0x07.toByte()
        RequestCommand.Selection.value shouldBe 0x0B.toByte()
    }
})
