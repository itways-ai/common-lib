package com.itways.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailProviderConfig {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String from;
    // Potentially add 'smtpAuth', 'starttls' etc. in future
}
