package com.itways.common.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The content hash of a knowledge passage and of a knowledge source (2.2.0), so that
 * conversation-service (which reads files and computes the hashes) and journey-service (which
 * stores passages, deduplicates by hash and backfilled the existing rows in SQL) agree on one
 * value.
 *
 * <p>
 * A passage's hash is the lower-case hex SHA-256 of the UTF-8 bytes of
 *
 * <pre>
 * btrim(question) + "\n" + btrim(answer ?? "") + "\n" + (locale ?? "")
 * </pre>
 *
 * which is journey-service's V14 backfill, byte for byte:
 *
 * <pre>
 * encode(sha256(convert_to(btrim(chunk_text) || E'\n' || btrim(COALESCE(answer, '')) || E'\n'
 *        || COALESCE(locale, ''), 'UTF8')), 'hex')
 * </pre>
 *
 * Like PostgreSQL's one-argument {@code btrim}, the trim removes spaces (U+0020) only, from both
 * ends: a tab, a newline or a no-break space at an end is part of the text. Nothing else is
 * normalised: letter case, inner whitespace, diacritics and the locale's spelling all change
 * the hash, as they do in SQL. A {@code null} question is hashed as {@code ""} (the SQL column
 * is never null).
 *
 * <p>
 * A source's hash is the SHA-256 of its passages' hashes, sorted and joined with {@code "\n"}:
 * the same set of passages gives the same source hash in any order.
 *
 * <p>
 * Framework-free and stateless.
 */
public final class PassageHashes {

    private static final HexFormat HEX = HexFormat.of();

    private PassageHashes() {
    }

    /**
     * The passage hash of {@code question}, {@code answer} and {@code locale}: 64 lower-case hex
     * characters. For a document passage, {@code question} is the passage text and
     * {@code answer} is {@code null}.
     */
    public static String sha256Hex(String question, String answer, String locale) {
        return sha256(btrim(question) + "\n" + btrim(answer) + "\n" + (locale == null ? "" : locale));
    }

    /**
     * The source hash of a set of passage hashes: SHA-256 of the hashes sorted and joined with
     * {@code "\n"}; the hash of {@code ""} for none.
     *
     * @throws NullPointerException for a {@code null} collection or element
     */
    public static String sourceHash(Collection<String> passageHashes) {
        List<String> sorted = passageHashes.stream().map(h -> Objects.requireNonNull(h, "a passage hash is null"))
                .sorted().toList();
        return sha256(String.join("\n", sorted));
    }

    /** PostgreSQL's {@code btrim(text)}: leading and trailing spaces (U+0020) removed; {@code ""} for null. */
    static String btrim(String text) {
        if (text == null) {
            return "";
        }
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(start, end);
    }

    private static String sha256(String text) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JDK ships SHA-256 (a required algorithm of the platform).
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
