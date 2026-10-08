package com.itways.contracts.knowledge;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The rule for knowledge index names (2.2.0, decision D6 of the knowledge-base upgrade): one
 * definition for journey-service (which creates indexes), account-service (which stores an
 * assistant's list of index names) and the portal (which mirrors {@link #REGEX}).
 *
 * <p>
 * A name is an identifier: it appears in journey step configurations and in URLs, and the
 * index's {@code description} carries the display text. A new name is 2 to 64 ASCII lower-case
 * letters and digits in groups joined by single {@code -} or {@code _}: {@code faq},
 * {@code opening-hours}, {@code menu_2026}. Names are unique case-insensitively within a
 * scope, so {@code Qa-Kb} and {@code qa-kb} are the same name.
 *
 * <p>
 * How a service applies it:
 * <ul>
 * <li>creating an index: {@code String name = normalize(input)}, then refuse unless
 * {@code isValid(name)} (400 {@code INDEX_NAME_INVALID}), then compare case-insensitively with
 * the scope's existing names (409 {@code INDEX_NAME_TAKEN}). {@code Qa-Kb} normalises to the
 * valid {@code qa-kb}, so after {@code qa-kb} it is a clash, not an invalid name;</li>
 * <li>an index created before the rule keeps its name: it is readable and usable,
 * {@link #isLegacy} says so, and it cannot be created again under that name;</li>
 * <li>storing a reference (an assistant's knowledge indexes): {@link #isStorable}, which
 * accepts every legacy name that can be stored as one element of a comma-joined list;</li>
 * <li>suggesting a name from display text: {@link #slug}.</li>
 * </ul>
 *
 * <p>
 * Framework-free and stateless.
 */
public final class KnowledgeIndexName {

    /** The shortest valid name. */
    public static final int MIN_LENGTH = 2;

    /** The longest name, new or stored. */
    public static final int MAX_LENGTH = 64;

    /** Groups of {@code [a-z0-9]} joined by single {@code -} or {@code _}; the length is checked apart. */
    public static final String REGEX = "^[a-z0-9]+(?:[-_][a-z0-9]+)*$";

    /** {@link #REGEX}, compiled. */
    public static final Pattern PATTERN = Pattern.compile(REGEX);

    /** The rule in words, for a 400 answer and the portal's hint. */
    public static final String RULE = "An index name is 2 to 64 lower-case letters (a-z) and digits, in groups joined by "
            + "a single '-' or '_', for example opening-hours";

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private KnowledgeIndexName() {
    }

    /**
     * The form a name is checked and stored in: surrounding whitespace removed and lower-cased
     * ({@link Locale#ROOT}); {@code null} for {@code null}. It changes nothing else, so a name that
     * breaks the rule still breaks it after normalising ({@code My FAQ ?} becomes
     * {@code my faq ?}).
     */
    public static String normalize(String name) {
        return name == null ? null : name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether {@code name}, exactly as given, satisfies the rule: {@value #MIN_LENGTH} to
     * {@value #MAX_LENGTH} characters matching {@link #REGEX}. Callers normalise first.
     */
    public static boolean isValid(String name) {
        return name != null && name.length() >= MIN_LENGTH && name.length() <= MAX_LENGTH
                && PATTERN.matcher(name).matches();
    }

    /**
     * Whether a stored name predates the rule: not blank and not {@link #isValid valid}. Such an
     * index is shown with a "legacy name" hint and cannot be created again under that name.
     */
    public static boolean isLegacy(String name) {
        return name != null && !name.isBlank() && !isValid(name);
    }

    /**
     * Whether {@code name} may be stored as a reference to an index, as account-service does
     * with an assistant's list (comma-joined in one column): not blank, no comma, at most
     * {@value #MAX_LENGTH} characters. Looser than {@link #isValid} on purpose, so an assistant
     * that names a legacy index can still be saved.
     */
    public static boolean isStorable(String name) {
        return name != null && !name.isBlank() && name.indexOf(',') < 0 && name.length() <= MAX_LENGTH;
    }

    /**
     * Whether two names are the same index name: equal after {@link #normalize} (so
     * case-insensitive). Two {@code null}s are the same; {@code null} and a name are not.
     */
    public static boolean sameName(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }

    /**
     * A valid name suggested from display text, for the portal's live preview: accents
     * removed, lower-cased, every run of other characters turned into one {@code -} (a lone
     * {@code _} is kept), cut to {@value #MAX_LENGTH} characters. {@code "My FAQ ?"} gives
     * {@code my-faq}, {@code "Café Menu"} gives {@code cafe-menu}.
     *
     * @return a name that {@link #isValid} accepts, or {@code ""} when the text has fewer than
     *         {@value #MIN_LENGTH} Latin letters or digits to build one from (Arabic-only text,
     *         punctuation, {@code null})
     */
    public static String slug(String preview) {
        if (preview == null) {
            return "";
        }
        String plain = COMBINING_MARKS.matcher(Normalizer.normalize(preview, Normalizer.Form.NFKD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(Math.min(plain.length(), MAX_LENGTH));
        char separator = 0;
        for (int i = 0; i < plain.length() && out.length() < MAX_LENGTH; i++) {
            char c = plain.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (separator != 0 && !out.isEmpty()) {
                    if (out.length() + 2 > MAX_LENGTH) {
                        break;
                    }
                    out.append(separator);
                }
                separator = 0;
                out.append(c);
            } else {
                // One separator per run: '_' only when the run is exactly one underscore.
                separator = separator == 0 && c == '_' ? '_' : '-';
            }
        }
        String name = out.toString();
        return isValid(name) ? name : "";
    }
}
