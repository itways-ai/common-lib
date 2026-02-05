package com.itways.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    private String intent;
    private double confidence;
    private Map<String, Object> entities;
    private String originalText;
    private String reasoning;
}
