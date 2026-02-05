package com.itways.freemarker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;

import freemarker.template.TemplateExceptionHandler;

@Configuration
@ComponentScan("com.itways.freemarker")
public class FreeMarkerAutoConfiguration {

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
