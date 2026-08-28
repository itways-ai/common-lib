package com.itways.security.resolver;

import com.itways.security.annotation.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every account-scoped controller binds its tenant through this resolver. The
 * null branches matter: a request that reaches a handler without the details
 * map populated must bind null (and fail loudly downstream), never bind some
 * other request's tenant.
 */
@DisplayName("AccountIdArgumentResolver")
class AccountIdArgumentResolverTest {

    private final AccountIdArgumentResolver resolver = new AccountIdArgumentResolver();

    @SuppressWarnings("unused")
    private void sample(@AccountId String accountId, String plain) {
    }

    private MethodParameter parameterAt(int index) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("sample", String.class, String.class);
        return new MethodParameter(method, index);
    }

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("supports exactly the parameters annotated with @AccountId")
    void supportsAnnotatedParameter() throws Exception {
        assertThat(resolver.supportsParameter(parameterAt(0))).isTrue();
        assertThat(resolver.supportsParameter(parameterAt(1))).isFalse();
    }

    @Test
    @DisplayName("resolves the accountId from the authentication details map")
    void resolvesFromDetails() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", null);
        authentication.setDetails(Map.of("accountId", "AIUS000000000001"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolver.resolveArgument(parameterAt(0), null, null, null))
                .isEqualTo("AIUS000000000001");
    }

    @Test
    @DisplayName("no authentication resolves to null rather than throwing")
    void nullWithoutAuthentication() throws Exception {
        assertThat(resolver.resolveArgument(parameterAt(0), null, null, null)).isNull();
    }

    @Test
    @DisplayName("details that are not a map resolve to null")
    void nullWithForeignDetails() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", null);
        authentication.setDetails("a plain string");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolver.resolveArgument(parameterAt(0), null, null, null)).isNull();
    }
}
