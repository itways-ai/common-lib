package com.itways.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheSettings {
    private String name;
    @Builder.Default
    private int ttlMinutes=10;
    @Builder.Default
    private int heapSize=1000;
    @Builder.Default
    private boolean resetTtlOnUpdate = false;

    // Default configuration
    public static CacheSettings defaultConfig() {
        return CacheSettings.builder()
                .ttlMinutes(10)
                .heapSize(1000)
                .resetTtlOnUpdate(false)
                .build();
    }
}
