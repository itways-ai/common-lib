package com.itways.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDTO {
    private Long id;
    private String name;
    private String type;
    private String content;
    private String description;
    private Boolean allowFailure;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
