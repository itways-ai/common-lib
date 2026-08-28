package com.itways.freemarker;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TemplateRender is the shared renderer behind notification emails and the
 * templates-in-Postgres design, so renderFromString (DB content) and the
 * renderFromFile ResourceLoader fallback (templates inside fat JARs, where
 * FreeMarker's own loader goes blind) are both load-bearing. No Spring context
 * is needed: the FreeMarker Configuration is built directly, mirroring
 * FreeMarkerConfig's settings, and the fallback uses a real
 * DefaultResourceLoader against src/test/resources/templates/greeting.ftl.
 */
@DisplayName("TemplateRender")
class TemplateRenderTest {

    /** Mirrors FreeMarkerConfig: UTF-8, IGNORE handler, classpath loading. */
    private static Configuration prodLikeConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
        configuration.setClassForTemplateLoading(TemplateRenderTest.class, "/");
        return configuration;
    }

    /** A configuration whose own loader knows no templates at all. */
    private static Configuration configurationWithoutTemplates() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
        configuration.setTemplateLoader(new StringTemplateLoader());
        return configuration;
    }

    @Nested
    @DisplayName("renderFromString")
    class RenderFromString {

        private final TemplateRender render =
                new TemplateRender(prodLikeConfiguration(), new DefaultResourceLoader());

        @Test
        @DisplayName("interpolates model values into the template text")
        void interpolatesModel() {
            String result = render.renderFromString(
                    "Hello ${name}, you have ${count} pending tasks.",
                    Map.of("name", "Nibras", "count", 3));

            assertThat(result).isEqualTo("Hello Nibras, you have 3 pending tasks.");
        }

        @Test
        @DisplayName("a missing model value renders as blank under the production IGNORE handler")
        void missingValueRendersBlank() {
            // FreeMarkerConfig deliberately installs IGNORE_HANDLER so a
            // template referencing an absent binding degrades to an empty slot
            // instead of failing the whole notification. renderFromString
            // copies that handler from the injected configuration.
            String result = render.renderFromString("Hello ${name}!", Map.of());

            assertThat(result).isEqualTo("Hello !");
        }

        @Test
        @DisplayName("the injected configuration's exception handler is honored, not hardcoded")
        void handlerIsInherited() {
            Configuration strict = prodLikeConfiguration();
            strict.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            TemplateRender strictRender = new TemplateRender(strict, new DefaultResourceLoader());

            assertThatThrownBy(() -> strictRender.renderFromString("Hello ${name}!", Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to render template from string");
        }

        @Test
        @DisplayName("a syntactically broken template fails with the wrapped render exception")
        void brokenSyntax() {
            assertThatThrownBy(() -> render.renderFromString("Hello ${name", Map.of("name", "x")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to render template from string");
        }
    }

    @Nested
    @DisplayName("renderFromFile")
    class RenderFromFile {

        @Test
        @DisplayName("resolves templates through FreeMarker's own loader first")
        void standardLoaderPath() {
            TemplateRender render =
                    new TemplateRender(prodLikeConfiguration(), new DefaultResourceLoader());

            String result = render.renderFromFile("templates/greeting.ftl", Map.of("name", "Nibras"));

            assertThat(result).isEqualTo("Hello Nibras!");
        }

        @Test
        @DisplayName("falls back to Spring's ResourceLoader when the standard loader is blind")
        void resourceLoaderFallback() {
            // The scenario this fallback exists for: inside a fat JAR the
            // configured TemplateLoader cannot see the entry, but the classpath
            // resource is still reachable via Spring's ResourceLoader.
            TemplateRender render =
                    new TemplateRender(configurationWithoutTemplates(), new DefaultResourceLoader());

            String result = render.renderFromFile("templates/greeting.ftl", Map.of("name", "Nibras"));

            assertThat(result).isEqualTo("Hello Nibras!");
        }

        @Test
        @DisplayName("a template missing everywhere fails with the path in the message")
        void missingEverywhere() {
            TemplateRender render =
                    new TemplateRender(configurationWithoutTemplates(), new DefaultResourceLoader());

            assertThatThrownBy(() -> render.renderFromFile("templates/nope.ftl", Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("templates/nope.ftl");
        }
    }
}
