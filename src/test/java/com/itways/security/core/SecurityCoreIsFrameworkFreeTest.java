package com.itways.security.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The api-gateway (WebFlux) runs this package from the {@code security-core}
 * classifier jar, which holds nothing else. A reference to Spring, the servlet
 * API, Lombok, a logger or another common-lib package would compile here and
 * fail there at runtime, so it is refused here, from the compiled classes'
 * constant pools.
 */
class SecurityCoreIsFrameworkFreeTest {

    /** What the classifier jar packages besides this package (see the pom). */
    private static final String ALLOWED_OTHER_CLASS = "com/itways/contracts/channels/ChannelWebhookTokenClaims";

    private static final List<String> FORBIDDEN = List.of("org/springframework/", "jakarta/", "javax/servlet/",
            "lombok/", "org/slf4j/", "com/fasterxml/");

    private static final Pattern IN_HOUSE = Pattern.compile("com/itways/[A-Za-z0-9_/$]+");

    @Test
    void referencesOnlyTheJdkJjwtAndItself() throws Exception {
        Set<String> violations = new TreeSet<>();
        List<Path> classes = classFiles();
        assertThat(classes).as("compiled classes of com.itways.security.core").isNotEmpty();
        for (Path file : classes) {
            String pool = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (String forbidden : FORBIDDEN) {
                if (pool.contains(forbidden)) {
                    violations.add(file.getFileName() + " -> " + forbidden);
                }
            }
            Matcher matcher = IN_HOUSE.matcher(pool);
            while (matcher.find()) {
                String name = matcher.group();
                if (!name.startsWith("com/itways/security/core/") && !name.equals(ALLOWED_OTHER_CLASS)) {
                    violations.add(file.getFileName() + " -> " + name);
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private static List<Path> classFiles() throws IOException, URISyntaxException {
        Path dir = Path.of(TokenVerifier.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("com/itways/security/core");
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".class")).toList();
        }
    }
}
