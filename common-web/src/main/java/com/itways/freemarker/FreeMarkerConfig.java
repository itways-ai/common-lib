package com.itways.freemarker;

import freemarker.template.TemplateExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;

/** Opt in with {@code @EnableFreeMarker}; FreeMarker is an optional dependency of this module. */
@Configuration
@ConditionalOnClass(name = "freemarker.template.Configuration")
public class FreeMarkerConfig {

    @Primary
    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() throws Exception {
        FreeMarkerConfigurationFactoryBean factoryBean = new FreeMarkerConfigurationFactoryBean();
        factoryBean.setTemplateLoaderPath("classpath:/");
        factoryBean.setDefaultEncoding("UTF-8");

        freemarker.template.Configuration configuration = factoryBean.createConfiguration();

        // Handle missing parameters gracefully by skipping them
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);

        return configuration;
    }

    @Bean
    public TemplateRender templateService(freemarker.template.Configuration freemarkerConfiguration,
            ResourceLoader resourceLoader) {
        return new TemplateRender(freemarkerConfiguration, resourceLoader);
    }
}
