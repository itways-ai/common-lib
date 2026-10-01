package com.itways.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** What the scrubber removes from a question before it is stored, and what it leaves. */
class PiiScrubberTest {

    @ParameterizedTest
    @ValueSource(strings = { "a@b.com", "first.last+tag@example.co.uk", "USER_1%x@mail-server.example.org",
            "someone@sub.domain.io" })
    void emailAddresses(String address) {
        assertThat(PiiScrubber.scrub("write to " + address + " please")).isEqualTo("write to [email] please");
    }

    @Test
    void anEmailInArabicText() {
        assertThat(PiiScrubber.scrub("راسلني على user@example.com لو سمحت")).isEqualTo("راسلني على [email] لو سمحت");
        assertThat(PiiScrubber.scrub("(a@b.com)")).isEqualTo("([email])");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+962 79 123 4567", "+962791234567", "+962-79-123-4567", "00962791234567", "00 962 79 123 4567",
            "0791234567", "079 123 4567", "079-123-4567", "079.123.4567", "(06) 555 1234", "(+962) 79 123 4567",
            "+1 (555) 123-4567", "555-123-4567", "+44 20 7946 0958", "06 555 1234",
            // Arabic-Indic digits (U+0660..U+0669)
            "٠٧٩١٢٣٤٥٦٧", "٠٧٩ ١٢٣ ٤٥٦٧", "+٩٦٢ ٧٩ ١٢٣ ٤٥٦٧", "+٩٦٢٧٩١٢٣٤٥٦٧", "٠٠٩٦٢٧٩١٢٣٤٥٦٧",
            // Eastern Arabic-Indic digits (U+06F0..U+06F9)
            "۰۷۹۱۲۳۴۵۶۷",
            // Direction marks around and inside a number pasted from RTL text
            "\u200E+962 79 123 4567", "+962\u200F79 123 4567" })
    void phoneNumbers(String number) {
        // A direction mark before the number is not part of it and stays.
        String expected = number.startsWith("\u200E") ? "\u200E[phone]" : "[phone]";
        assertThat(PiiScrubber.scrub("call " + number + " now")).isEqualTo("call " + expected + " now");
        assertThat(PiiScrubber.scrub(number)).isEqualTo(expected);
        assertThat(PiiScrubber.containsPii(number)).isTrue();
    }

    @Test
    void phoneNumbersInArabicText() {
        assertThat(PiiScrubber.scrub("رقمي ٠٧٩ ١٢٣ ٤٥٦٧ اتصل بي")).isEqualTo("رقمي [phone] اتصل بي");
        assertThat(PiiScrubber.scrub("اتصل على +962791234567 أو ٠٧٩٥٥٥٥٥٥٥")).isEqualTo("اتصل على [phone] أو [phone]");
        // "و" (and) is written joined to the number that follows it.
        assertThat(PiiScrubber.scrub("0791234567 و0795555555")).isEqualTo("[phone] و[phone]");
        assertThat(PiiScrubber.scrub("متى تفتحون؟ رقمي 0791234567؟")).isEqualTo("متى تفتحون؟ رقمي [phone]؟");
    }

    @Test
    void twoNumbersWithOnlyASpaceBetweenThem() {
        assertThat(PiiScrubber.scrub("0791234567 0795555555")).isEqualTo("[phone] [phone]");
    }

    @Test
    void parenthesesTheNumberDoesNotOwnStayOutside() {
        assertThat(PiiScrubber.scrub("(call 0791234567)")).isEqualTo("(call [phone])");
        assertThat(PiiScrubber.scrub("(0791234567 is mine")).isEqualTo("([phone] is mine");
        assertThat(PiiScrubber.scrub("mine: 0791234567.")).isEqualTo("mine: [phone].");
    }

    @Test
    void emailAndPhoneTogether() {
        assertThat(PiiScrubber.scrub("I am a@b.com or +962 79 123 4567, and ٠٧٩١٢٣٤٥٦٧"))
                .isEqualTo("I am [email] or [phone], and [phone]");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "We open at 9:30 until 17:00", "since 2026", "the 2026-09-29 meeting", "on 29/09/2026",
            "on 29.09.2026", "costs 150 JD", "order 12345", "ref ABC1234567", "code 1234567XYZ", "room 12 floor 3",
            "a 6-digit pin 123 456", "٢٠٢٦", "متى تفتحون يوم ٢٩/٠٩/٢٠٢٦؟", "no digits at all" })
    void leavesOrdinaryNumbersAndTextAlone(String text) {
        assertThat(PiiScrubber.scrub(text)).isEqualTo(text);
        assertThat(PiiScrubber.containsPii(text)).isFalse();
    }

    @Test
    void numbersLongerThanAPhoneNumberAreLeft() {
        // 16 digits in one run: more than E.164 allows.
        assertThat(PiiScrubber.scrub("id 1234567890123456")).isEqualTo("id 1234567890123456");
    }

    @Test
    void nullEmptyAndContainsPii() {
        assertThat(PiiScrubber.scrub(null)).isNull();
        assertThat(PiiScrubber.scrub("")).isEmpty();
        assertThat(PiiScrubber.containsPii(null)).isFalse();
        assertThat(PiiScrubber.containsPii("mail me at a@b.com")).isTrue();
        assertThat(PiiScrubber.containsPii("٠٧٩١٢٣٤٥٦٧")).isTrue();
    }

    @Test
    void scrubbingTwiceChangesNothingMore() {
        String once = PiiScrubber.scrub("a@b.com +962 79 123 4567");
        assertThat(PiiScrubber.scrub(once)).isEqualTo(once).isEqualTo("[email] [phone]");
    }

    @Test
    void aLongRunOfDigitsIsHandledQuickly() {
        String digits = "1 ".repeat(50_000);
        long started = System.nanoTime();
        PiiScrubber.scrub(digits);
        assertThat(System.nanoTime() - started).isLessThan(5_000_000_000L);
    }
}
