package com.itways.contracts.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The index-name rule (D6) as journey-service, account-service and the portal apply it. */
class KnowledgeIndexNameTest {

    @ParameterizedTest
    @ValueSource(strings = { "qa", "faq", "qa-kb", "opening-hours", "menu_2026", "a1", "2026", "a-b_c-d",
            "0123456789012345678901234567890123456789012345678901234567890123" })
    void validNames(String name) {
        assertThat(KnowledgeIndexName.isValid(name)).as(name).isTrue();
        assertThat(KnowledgeIndexName.isLegacy(name)).isFalse();
        assertThat(KnowledgeIndexName.normalize(name)).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = { "qa,comma", "qa kb space", "My FAQ ?", "Qa-Kb", "a", "", "   ", "-qa", "qa-", "_qa",
            "qa_", "qa--kb", "qa-_kb", "qa.kb", "qa/kb", "أسئلة", "faq-عربي", "café", "qa\tkb", " qa",
            "01234567890123456789012345678901234567890123456789012345678901234" })
    void invalidNames(String name) {
        assertThat(KnowledgeIndexName.isValid(name)).as("[" + name + "]").isFalse();
    }

    @Test
    void nullIsNeitherValidNorLegacyNorStorable() {
        assertThat(KnowledgeIndexName.isValid(null)).isFalse();
        assertThat(KnowledgeIndexName.isLegacy(null)).isFalse();
        assertThat(KnowledgeIndexName.isStorable(null)).isFalse();
        assertThat(KnowledgeIndexName.normalize(null)).isNull();
        assertThat(KnowledgeIndexName.slug(null)).isEmpty();
    }

    @Test
    void normalisingTrimsAndLowerCasesOnly() {
        assertThat(KnowledgeIndexName.normalize("  Qa-Kb \t")).isEqualTo("qa-kb");
        assertThat(KnowledgeIndexName.normalize("My FAQ ?")).isEqualTo("my faq ?");
        // Locale.ROOT: no Turkish dotless i.
        assertThat(KnowledgeIndexName.normalize("TITLE")).isEqualTo("title");
    }

    @Test
    void theCreateRuleNormalisesThenValidates() {
        // B10: after qa-kb exists, Qa-Kb is the same name (409), not an invalid one (400).
        assertThat(KnowledgeIndexName.isValid(KnowledgeIndexName.normalize("Qa-Kb"))).isTrue();
        assertThat(KnowledgeIndexName.isValid(KnowledgeIndexName.normalize("qa,comma"))).isFalse();
        assertThat(KnowledgeIndexName.isValid(KnowledgeIndexName.normalize("qa kb space"))).isFalse();
        assertThat(KnowledgeIndexName.isValid(KnowledgeIndexName.normalize("My FAQ ?"))).isFalse();
    }

    @Test
    void caseTwinsAreTheSameName() {
        assertThat(KnowledgeIndexName.sameName("Qa-Kb", "qa-kb")).isTrue();
        assertThat(KnowledgeIndexName.sameName("FAQ", " faq ")).isTrue();
        assertThat(KnowledgeIndexName.sameName("Legacy Name", "legacy name")).isTrue();
        assertThat(KnowledgeIndexName.sameName("qa-kb", "qa_kb")).isFalse();
        assertThat(KnowledgeIndexName.sameName("qa", null)).isFalse();
        assertThat(KnowledgeIndexName.sameName(null, null)).isTrue();
    }

    @Test
    void legacyNamesAreStoredNamesThatBreakTheRule() {
        assertThat(KnowledgeIndexName.isLegacy("My FAQ")).isTrue();
        assertThat(KnowledgeIndexName.isLegacy("أسئلة شائعة")).isTrue();
        assertThat(KnowledgeIndexName.isLegacy("Qa-Kb")).isTrue();
        assertThat(KnowledgeIndexName.isLegacy("qa-kb")).isFalse();
        assertThat(KnowledgeIndexName.isLegacy("  ")).isFalse();
    }

    @Test
    void storableIsWhatAnAssistantsCommaJoinedListCanHold() {
        assertThat(KnowledgeIndexName.isStorable("qa-kb")).isTrue();
        // Legacy names stay referable.
        assertThat(KnowledgeIndexName.isStorable("My FAQ ?")).isTrue();
        assertThat(KnowledgeIndexName.isStorable("أسئلة شائعة")).isTrue();
        assertThat(KnowledgeIndexName.isStorable("x".repeat(64))).isTrue();

        assertThat(KnowledgeIndexName.isStorable("qa,comma")).isFalse();
        assertThat(KnowledgeIndexName.isStorable("x".repeat(65))).isFalse();
        assertThat(KnowledgeIndexName.isStorable("")).isFalse();
        assertThat(KnowledgeIndexName.isStorable("  ")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "My FAQ ?|my-faq", "qa kb space|qa-kb-space", "qa,comma|qa-comma", "Qa-Kb|qa-kb", "menu_2026|menu_2026",
            "a _ b|a-b", "Café Menu|cafe-menu", "  Opening   Hours!!|opening-hours", "ＦＡＱ ２０２６|faq-2026",
            "FAQ (عربي)|faq", "--x--y--|x-y" })
    void slugSuggestsAValidName(String preview, String expected) {
        assertThat(KnowledgeIndexName.slug(preview)).isEqualTo(expected);
        assertThat(KnowledgeIndexName.isValid(expected)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "أسئلة شائعة", "?!", "a", "", "   " })
    void slugIsEmptyWhenNothingValidCanBeBuilt(String preview) {
        assertThat(KnowledgeIndexName.slug(preview)).isEmpty();
    }

    @Test
    void slugIsCutToTheMaximumWithoutATrailingSeparator() {
        String slug = KnowledgeIndexName.slug("word ".repeat(30));
        assertThat(slug).hasSizeLessThanOrEqualTo(KnowledgeIndexName.MAX_LENGTH).doesNotEndWith("-");
        assertThat(KnowledgeIndexName.isValid(slug)).isTrue();

        String long_ = KnowledgeIndexName.slug("x".repeat(100));
        assertThat(long_).hasSize(KnowledgeIndexName.MAX_LENGTH);
    }

    @Test
    void theConstantsDescribeTheRule() {
        assertThat(KnowledgeIndexName.MIN_LENGTH).isEqualTo(2);
        assertThat(KnowledgeIndexName.MAX_LENGTH).isEqualTo(64);
        assertThat(KnowledgeIndexName.PATTERN.pattern()).isEqualTo(KnowledgeIndexName.REGEX)
                .isEqualTo("^[a-z0-9]+(?:[-_][a-z0-9]+)*$");
    }
}
