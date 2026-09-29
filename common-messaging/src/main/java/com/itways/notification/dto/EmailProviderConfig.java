package com.itways.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailProviderConfig {
    private String host;
    private Integer port;
    private String username;
    @ToString.Exclude
    private String password;
    private String from;
    // Potentially add 'smtpAuth', 'starttls' etc. in future
}
