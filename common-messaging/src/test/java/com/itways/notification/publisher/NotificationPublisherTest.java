package com.itways.notification.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationPublisherTest {

    @Test
    void theLogShowsAMaskedRecipient() {
        assertThat(NotificationPublisher.maskedRecipient("jane.doe@example.com"))
                .isEqualTo("j***@example.com")
                .doesNotContain("jane.doe");
    }

    @Test
    void anAddressWithoutALocalPartIsFullyMasked() {
        assertThat(NotificationPublisher.maskedRecipient("no-at-sign")).isEqualTo("***");
        assertThat(NotificationPublisher.maskedRecipient("@example.com")).isEqualTo("***");
    }
}
