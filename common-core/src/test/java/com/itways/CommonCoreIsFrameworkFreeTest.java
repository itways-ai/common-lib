package com.itways;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.itways.security.core.TokenVerifier;

/**
 * common-core is the framework-free module: the api-gateway (WebFlux) and every
 * service load it, so a reference to Spring, the servlet API, Jackson databind,
 * a logger or another module would compile here and fail there at runtime.
 * Every class of the module is checked from its compiled constant pool. The
 * security core ({@code com.itways.security.core}) keeps its stricter rule:
 * nothing but the JDK, jjwt, itself and one constants class; no Lombok, no
 * logging.
 */
class CommonCoreIsFrameworkFreeTest {

    /** What any class of the module may reference. */
    private static final List<String> ALLOWED = List.of(
            "java/", "javax/", // the JDK
            "io/jsonwebtoken/", // jjwt (TokenVerifier)
            "com/fasterxml/jackson/annotation/", // @JsonIgnoreProperties on the contracts
            "com/itways/", // this module
            "io/swagger/v3/oas/annotations/"); // @Schema: optional, compile-time only annotations (see the pom)

    private static final String SECURITY_CORE = "com/itways/security/core/";

    /** The one class outside the security core it reads (webhook-token claim names). */
    private static final String ALLOWED_OTHER_CLASS = "com/itways/contracts/channels/ChannelWebhookTokenClaims";

    private static final List<String> FORBIDDEN_IN_SECURITY_CORE = List.of("org/springframework/", "jakarta/",
            "javax/servlet/", "lombok/", "org/slf4j/", "com/fasterxml/");

    private static final Pattern IN_HOUSE = Pattern.compile("com/itways/[A-Za-z0-9_/$]+");

    /** An object type inside a field, method or annotation descriptor: {@code Lpkg/Name;} or {@code Lpkg/Name<...>}. */
    private static final Pattern OBJECT_TYPE = Pattern
            .compile("L([A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)*)[;<]");

    @Test
    void everyClassReferencesOnlyTheJdkJjwtAnnotationsAndItself() throws Exception {
        Set<String> violations = new TreeSet<>();
        Path root = classesRoot();
        List<Path> classes = classFiles(root);
        assertThat(classes).as("compiled classes of common-core").hasSizeGreaterThan(50);
        int securityCoreClasses = 0;

        for (Path file : classes) {
            String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
            byte[] bytes = Files.readAllBytes(file);

            for (String type : referencedTypes(bytes)) {
                if (ALLOWED.stream().noneMatch(type::startsWith)) {
                    violations.add(name + " -> " + type);
                }
            }

            if (name.startsWith(SECURITY_CORE)) {
                securityCoreClasses++;
                String pool = new String(bytes, StandardCharsets.ISO_8859_1);
                for (String forbidden : FORBIDDEN_IN_SECURITY_CORE) {
                    if (pool.contains(forbidden)) {
                        violations.add(name + " -> " + forbidden);
                    }
                }
                Matcher matcher = IN_HOUSE.matcher(pool);
                while (matcher.find()) {
                    String reference = matcher.group();
                    if (!reference.startsWith(SECURITY_CORE) && !reference.equals(ALLOWED_OTHER_CLASS)) {
                        violations.add(name + " -> " + reference);
                    }
                }
            }
        }

        assertThat(securityCoreClasses).as("compiled classes of com.itways.security.core").isGreaterThan(0);
        assertThat(violations).isEmpty();
    }

    /**
     * Every type a class file names: the constant pool's class entries, plus the
     * object types inside its descriptors and signatures (fields, methods,
     * annotations, generics).
     */
    static Set<String> referencedTypes(byte[] classFile) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        List<Integer> classEntries = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
            case 1 -> utf8[i] = in.readUTF(); // Utf8
            case 7 -> classEntries.add(in.readUnsignedShort()); // Class
            case 8, 16, 19, 20 -> in.readUnsignedShort(); // String, MethodType, Module, Package
            case 3, 4 -> in.readInt(); // Integer, Float
            case 5, 6 -> { // Long, Double: two slots
                in.readLong();
                i++;
            }
            case 9, 10, 11, 12, 17, 18 -> { // Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                in.readUnsignedShort();
                in.readUnsignedShort();
            }
            case 15 -> { // MethodHandle
                in.readUnsignedByte();
                in.readUnsignedShort();
            }
            default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }

        Set<String> types = new TreeSet<>();
        for (int index : classEntries) {
            String name = utf8[index];
            if (name.startsWith("[")) {
                addObjectTypes(types, name);
            } else {
                types.add(name);
            }
        }
        for (String value : utf8) {
            if (value != null && !value.isEmpty() && "(L[<".indexOf(value.charAt(0)) >= 0) {
                addObjectTypes(types, value);
            }
        }
        return types;
    }

    private static void addObjectTypes(Set<String> types, String descriptor) {
        Matcher matcher = OBJECT_TYPE.matcher(descriptor);
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
    }

    private static Path classesRoot() throws URISyntaxException {
        return Path.of(TokenVerifier.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static List<Path> classFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.toString().endsWith(".class")).toList();
        }
    }
}
