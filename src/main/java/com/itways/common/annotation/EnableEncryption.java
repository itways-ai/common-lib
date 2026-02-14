package com.itways.common.annotation;

import com.itways.common.config.EncryptionConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(EncryptionConfiguration.class)
public @interface EnableEncryption {
}
