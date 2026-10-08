package com.itways.common.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes e-mail addresses and phone numbers from free text before it is stored, e.g. the
 * questions journey-service keeps as knowledge gaps (2.2.0). An address becomes
 * {@value #EMAIL}, a number {@value #PHONE}; everything else is left as it was.
 *
 * <p>
 * Phone numbers are read in ASCII digits, Arabic-Indic digits (U+0660..U+0669) and Eastern
 * Arabic-Indic digits (U+06F0..U+06F9), with spaces, hyphens, dots, parentheses and
 * direction marks between the groups: {@code +962 79 123 4567}, {@code 00962791234567},
 * {@code 079-123-4567}, {@code (06) 555 1234}, {@code ٠٧٩١٢٣٤٥٦٧}. A run of digits counts
 * as a phone number when it has 7 to 15 digits (up to 17 with a {@code 00} international
 * prefix) and is not written as a date ({@code 2026-09-29}, {@code 29/09/2026},
 * {@code 29.09.2026}). Shorter numbers (a year, a price, an opening hour) are kept; a digit
 * run glued to Latin letters ({@code ABC1234567}, a reference) is kept too; one joined to an
 * Arabic word ({@code و0791234567}, "and 079…") is not. The rule errs on the side of
 * removing: a long order number written with spaces also becomes {@value #PHONE}.
 *
 * <p>
 * Framework-free and stateless; safe for any thread.
 */
public final class PiiScrubber {

    /** What an e-mail address is replaced with. */
    public static final String EMAIL = "[email]";

    /** What a phone number is replaced with. */
    public static final String PHONE = "[phone]";

    private static final Pattern EMAIL_ADDRESS = Pattern
            .compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    private static final String DIGIT = "[0-9\\u0660-\\u0669\\u06F0-\\u06F9]";

    /** Between two digits of one number: spaces (incl. no-break), '-', '.', parentheses, LRM/RLM/ALM. */
    private static final String SEPARATOR = "[ \\t\\u00A0\\u202F\\-.()\\u200E\\u200F\\u061C]";

    /**
     * What may not touch a number for it to count: a Latin letter, a digit or {@code _} (a
     * reference such as {@code ABC1234567}). An Arabic letter may: the one-letter words
     * {@code و} ("and"), {@code ب}, {@code ل} are written joined to the next word.
     */
    private static final String GLUED = "[A-Za-z0-9\\u0660-\\u0669\\u06F0-\\u06F9_]";

    /**
     * A candidate: an optional {@code +} (in parentheses or not), then digits with at
     * most three separator characters between two of them, not {@link #GLUED} on either side.
     * Bounded, and separators and digits are disjoint, so matching is linear.
     */
    private static final Pattern PHONE_CANDIDATE = Pattern.compile("(?<![A-Za-z0-9\\u0660-\\u0669\\u06F0-\\u06F9_+])"
            + "\\(?(?:\\+" + SEPARATOR + "?)?\\(?" + DIGIT + "(?:" + SEPARATOR + "{0,3}" + DIGIT + "){5,40}\\)?(?!" + GLUED
            + ")");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("[ \\t\\u00A0\\u202F]+");

    private static final Pattern DATE = Pattern
            .compile("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}[-./]\\d{1,2}[-./]\\d{2,4}");

    private static final int MIN_DIGITS = 7;
    private static final int MAX_DIGITS = 15;

    private PiiScrubber() {
    }

    /**
     * {@code text} with every e-mail address replaced by {@value #EMAIL} and every phone number
     * by {@value #PHONE}; {@code null} for {@code null}.
     */
    public static String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String withoutEmails = EMAIL_ADDRESS.matcher(text).replaceAll(Matcher.quoteReplacement(EMAIL));
        Matcher matcher = PHONE_CANDIDATE.matcher(withoutEmails);
        StringBuilder out = new StringBuilder(withoutEmails.length());
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(balanced(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Whether {@code text} holds an e-mail address or a phone number, by the rules of {@link #scrub}. */
    public static boolean containsPii(String text) {
        return text != null && !text.equals(scrub(text));
    }

    /**
     * {@link #replacement} of the candidate, leaving outside it a parenthesis the number does
     * not own: the {@code )} of {@code (call 0791234567)}, the {@code (} of {@code (0791234567}.
     */
    private static String balanced(String candidate) {
        String lead = "";
        String tail = "";
        String number = candidate;
        if (number.startsWith("(") && number.indexOf(')') < 0) {
            lead = "(";
            number = number.substring(1);
        }
        if (number.endsWith(")") && number.indexOf('(') < 0) {
            tail = ")";
            number = number.substring(0, number.length() - 1);
        }
        return lead + replacement(number) + tail;
    }

    /**
     * {@value #PHONE} for a phone number; for a run too long to be one (two numbers written
     * with only a space between them), each space-separated part that is a phone number on its
     * own; else the candidate unchanged.
     */
    private static String replacement(String candidate) {
        if (isPhoneNumber(candidate)) {
            return PHONE;
        }
        if (significantDigits(asciiDigits(candidate)) <= MAX_DIGITS) {
            return candidate;
        }
        Matcher spaces = WHITESPACE_RUN.matcher(candidate);
        StringBuilder out = new StringBuilder(candidate.length());
        int start = 0;
        while (spaces.find()) {
            out.append(part(candidate.substring(start, spaces.start()))).append(spaces.group());
            start = spaces.end();
        }
        return out.append(part(candidate.substring(start))).toString();
    }

    private static String part(String token) {
        return isPhoneNumber(token) ? PHONE : token;
    }

    private static int significantDigits(String ascii) {
        String digits = ascii.replaceAll("[^0-9]", "");
        return digits.startsWith("00") ? digits.length() - 2 : digits.length();
    }

    private static boolean isPhoneNumber(String candidate) {
        String ascii = asciiDigits(candidate).trim();
        boolean international = ascii.startsWith("+") || ascii.startsWith("(+")
                || ascii.replaceAll("[^0-9]", "").startsWith("00");
        int significant = significantDigits(ascii);
        if (significant < MIN_DIGITS || significant > MAX_DIGITS) {
            return false;
        }
        return international || !DATE.matcher(ascii).matches();
    }

    /** Arabic-Indic and Eastern Arabic-Indic digits as ASCII digits; other characters unchanged. */
    private static String asciiDigits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') {
                out.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                out.append((char) ('0' + (c - '\u06F0')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
