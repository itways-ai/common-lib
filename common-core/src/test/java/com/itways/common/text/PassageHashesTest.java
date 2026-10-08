package com.itways.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The passage hash equals journey-service's V14 SQL formula. The expected values were computed by
 * PostgreSQL 16 itself (the platform's pgvector/pg16 image), with
 * {@code encode(sha256(convert_to(btrim(q) || E'\n' || btrim(COALESCE(a,'')) || E'\n' || COALESCE(l,''), 'UTF8')), 'hex')}.
 */
class PassageHashesTest {

    /** {@code ('متى تفتحون؟', 'نفتح من الساعة 9 صباحاً', 'ar')}, with or without surrounding spaces. */
    private static final String ARABIC_ROW = "5e46f0ae85e93cb2b7eab20114ec5511662511777303854ae902a0511d7bd113";

    @Test
    void equalsTheSqlFormulaForAnArabicRow() {
        assertThat(PassageHashes.sha256Hex("متى تفتحون؟", "نفتح من الساعة 9 صباحاً", "ar")).isEqualTo(ARABIC_ROW);
    }

    @Test
    void surroundingSpacesDoNotChangeTheHash() {
        assertThat(PassageHashes.sha256Hex("  متى تفتحون؟  ", " نفتح من الساعة 9 صباحاً ", "ar"))
                .isEqualTo(ARABIC_ROW);
    }

    @Test
    void aMissingAnswerAndLocaleAreEmpty() {
        // SQL: ('Opening hours', NULL, NULL)
        String expected = "c422ade621f1a500219c4b5ec8ca2a814f24a7e34e4cf544605dda8be38dcabb";
        assertThat(PassageHashes.sha256Hex("Opening hours", null, null)).isEqualTo(expected);
        assertThat(PassageHashes.sha256Hex("Opening hours", "", "")).isEqualTo(expected);
        assertThat(PassageHashes.sha256Hex("Opening hours", "   ", null)).isEqualTo(expected);
    }

    @Test
    void onlySpacesAreTrimmedAsPostgresBtrimDoes() {
        // SQL: (E'Tab\tend\t', 'x', 'en') and (E'Line\n', '   ', ''): tabs and newlines stay.
        assertThat(PassageHashes.sha256Hex("Tab\tend\t", "x", "en"))
                .isEqualTo("f873a4557fc8f12c063dedc84ed3c0d9ca8f4f3125b1e24b5b4301d6552df3a3");
        assertThat(PassageHashes.sha256Hex("Line\n", "   ", ""))
                .isEqualTo("1882b7883e65e7a70a718b05e5718e7d4c7904a70f41c1bf84afb66e20e4bc4a");
        assertThat(PassageHashes.sha256Hex("Line\n", null, null)).isNotEqualTo(PassageHashes.sha256Hex("Line", null, null));
        assertThat(PassageHashes.btrim("  a b  ")).isEqualTo("a b");
        assertThat(PassageHashes.btrim("\u00A0a\u00A0")).isEqualTo("\u00A0a\u00A0");
        assertThat(PassageHashes.btrim("    ")).isEmpty();
        assertThat(PassageHashes.btrim(null)).isEmpty();
    }

    @Test
    void caseInnerWhitespaceAndLocaleAreSignificant() {
        String base = PassageHashes.sha256Hex("Opening hours", "9 to 5", "en");

        assertThat(PassageHashes.sha256Hex("opening hours", "9 to 5", "en")).isNotEqualTo(base);
        assertThat(PassageHashes.sha256Hex("Opening  hours", "9 to 5", "en")).isNotEqualTo(base);
        assertThat(PassageHashes.sha256Hex("Opening hours", "9 to 5", "ar")).isNotEqualTo(base);
        assertThat(PassageHashes.sha256Hex("Opening hours", "9 to 5", null)).isNotEqualTo(base);
        // The separators keep the parts apart: moving text between question and answer changes it.
        assertThat(PassageHashes.sha256Hex("Opening", "hours 9 to 5", "en")).isNotEqualTo(base);
    }

    @Test
    void aNullQuestionIsEmptyAndTheHashIsLowerCaseHex() {
        assertThat(PassageHashes.sha256Hex(null, null, null)).isEqualTo(PassageHashes.sha256Hex("", "", ""))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void theSourceHashIgnoresOrder() {
        String a = PassageHashes.sha256Hex("a", "1", null);
        String b = PassageHashes.sha256Hex("b", "2", null);
        String c = PassageHashes.sha256Hex("c", "3", null);

        assertThat(PassageHashes.sourceHash(List.of(a, b, c))).isEqualTo(PassageHashes.sourceHash(List.of(c, a, b)))
                .matches("[0-9a-f]{64}");
        assertThat(PassageHashes.sourceHash(List.of(a, b))).isNotEqualTo(PassageHashes.sourceHash(List.of(a, b, c)));
        // sha256 of the sorted hashes joined with "\n"; of "" for none.
        assertThat(PassageHashes.sourceHash(List.of()))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThatThrownBy(() -> PassageHashes.sourceHash(Arrays.asList(a, null)))
                .isInstanceOf(NullPointerException.class);
    }
}
