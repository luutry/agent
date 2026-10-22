package com.luutry.agent.security.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PiiMasker implementations and PiiMaskerFactory
 */
@DisplayName("PII Masker Tests")
class PiiMaskerTest {

    // ===== Phone Masker =====
    @Nested
    @DisplayName("PhoneMasker Tests")
    class PhoneMaskerTests {

        @Test
        @DisplayName("Should mask phone number: 138****1234")
        void testMaskPhone() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.PHONE);
            assertEquals("138****1234", masker.mask("13812341234"));
        }

        @Test
        @DisplayName("Should return original for short phone number")
        void testMaskShortPhone() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.PHONE);
            assertEquals("12345", masker.mask("12345"));
        }

        @Test
        @DisplayName("Should handle null phone")
        void testMaskNullPhone() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.PHONE);
            assertNull(masker.mask(null));
        }
    }

    // ===== ID Card Masker =====
    @Nested
    @DisplayName("IdCardMasker Tests")
    class IdCardMaskerTests {

        @Test
        @DisplayName("Should mask 18-digit ID card")
        void testMaskIdCard() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.ID_CARD);
            String result = masker.mask("110101199001011234");
            assertEquals("110***********1234", result);
        }

        @Test
        @DisplayName("Should mask ID card ending with X")
        void testMaskIdCardWithX() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.ID_CARD);
            String result = masker.mask("11010119900101123X");
            assertEquals("110***********123X", result);
        }
    }

    // ===== Email Masker =====
    @Nested
    @DisplayName("EmailMasker Tests")
    class EmailMaskerTests {

        @Test
        @DisplayName("Should mask email: l***@gmail.com")
        void testMaskEmail() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.EMAIL);
            assertEquals("l***@gmail.com", masker.mask("luutry@gmail.com"));
        }

        @Test
        @DisplayName("Should return original for email without @")
        void testMaskNoAtSign() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.EMAIL);
            assertEquals("noemail", masker.mask("noemail"));
        }

        @Test
        @DisplayName("Should handle single char before @")
        void testMaskSingleCharEmail() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.EMAIL);
            assertEquals("a@test.com", masker.mask("a@test.com"));
        }
    }

    // ===== Name Masker =====
    @Nested
    @DisplayName("NameMasker Tests")
    class NameMaskerTests {

        @Test
        @DisplayName("Should mask Chinese name: 张*")
        void testMaskTwoCharName() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.NAME);
            assertEquals("张*", masker.mask("张三"));
        }

        @Test
        @DisplayName("Should mask three char name: 张**")
        void testMaskThreeCharName() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.NAME);
            assertEquals("张**", masker.mask("张三丰"));
        }

        @Test
        @DisplayName("Should mask single char name: *")
        void testMaskSingleCharName() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.NAME);
            assertEquals("*", masker.mask("张"));
        }

        @Test
        @DisplayName("Should handle empty name")
        void testMaskEmptyName() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.NAME);
            assertEquals("", masker.mask(""));
        }

        @Test
        @DisplayName("Should handle null name")
        void testMaskNullName() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.NAME);
            assertNull(masker.mask(null));
        }
    }

    // ===== Bank Card Masker =====
    @Nested
    @DisplayName("BankCardMasker Tests")
    class BankCardMaskerTests {

        @Test
        @DisplayName("Should mask 16-digit bank card")
        void testMaskBankCard() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.BANK_CARD);
            String result = masker.mask("6222020012341234");
            assertEquals("6222********1234", result);
        }

        @Test
        @DisplayName("Should return original for short card number")
        void testMaskShortCard() {
            PiiMasker masker = PiiMaskerFactory.getMasker(PiiType.BANK_CARD);
            assertEquals("1234567", masker.mask("1234567"));
        }
    }

    // ===== PiiMaskerFactory.maskText =====
    @Nested
    @DisplayName("PiiMaskerFactory maskText Tests")
    class MaskTextTests {

        @Test
        @DisplayName("Should mask phone number in text")
        void testMaskTextWithPhone() {
            String text = "员工手机号是13812341234，请联系";
            String masked = PiiMaskerFactory.maskText(text);
            assertTrue(masked.contains("138****1234"));
            assertFalse(masked.contains("13812341234"));
        }

        @Test
        @DisplayName("Should mask email in text")
        void testMaskTextWithEmail() {
            String text = "邮箱是luutry@gmail.com，请发送";
            String masked = PiiMaskerFactory.maskText(text);
            assertTrue(masked.contains("l***@gmail.com"));
        }

        @Test
        @DisplayName("Should handle null text")
        void testMaskNullText() {
            assertNull(PiiMaskerFactory.maskText(null));
        }

        @Test
        @DisplayName("Should handle blank text")
        void testMaskBlankText() {
            assertEquals("  ", PiiMaskerFactory.maskText("  "));
        }

        @Test
        @DisplayName("Should handle text with no PII")
        void testMaskTextNoPii() {
            String text = "这是一段普通文本，没有敏感信息";
            assertEquals(text, PiiMaskerFactory.maskText(text));
        }
    }
}
