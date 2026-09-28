package com.itways.feign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.itways.security.internal.InternalServiceToken;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * The service token on every Feign call (speech-service's calls to account, journey,
 * channels and template-service), with and without a request being served (PLT-12).
 */
@ExtendWith(OutputCaptureExtension.class)
class ForwardedAuthFeignConfigTest {

    private static final String SERVICE_TOKEN = "svc-token-9a4b27";

    @AfterEach
    void noRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static RequestTemplate call(String internalToken) {
        RequestInterceptor interceptor = new ForwardedAuthFeignConfig().forwardedAuthRequestInterceptor(
                new StaticListableBeanFactory().getBeanProvider(ForwardedAuthorizationResolver.class), internalToken);
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        return template;
    }

    @Test
    void sendsTheServiceTokenWhenOneIsConfiguredEvenOutsideARequest() {
        assertThat(call(SERVICE_TOKEN).headers().get(InternalServiceToken.HEADER)).containsExactly(SERVICE_TOKEN);
        assertThat(call(" " + SERVICE_TOKEN + " ").headers().get(InternalServiceToken.HEADER))
                .containsExactly(SERVICE_TOKEN);
    }

    @Test
    void sendsNoServiceTokenWhenNoneIsConfigured() {
        assertThat(call("").headers()).doesNotContainKey(InternalServiceToken.HEADER);
        assertThat(call("  ").headers()).doesNotContainKey(InternalServiceToken.HEADER);
        assertThat(call(null).headers()).doesNotContainKey(InternalServiceToken.HEADER);
    }

    @Test
    void sendsItBesideTheForwardedCredentialAndNeverLogsIt(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer caller");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = call(SERVICE_TOKEN);
        RequestContextHolder.resetRequestAttributes();
        call(SERVICE_TOKEN);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer caller");
        assertThat(template.headers().get(InternalServiceToken.HEADER)).containsExactly(SERVICE_TOKEN);
        assertThat(output.getAll()).doesNotContain(SERVICE_TOKEN);
    }
}
