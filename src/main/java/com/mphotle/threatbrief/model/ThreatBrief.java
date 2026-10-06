package com.mphotle.threatbrief.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ThreatBrief(
    LocalDate date,
    String content,
    int totalCount,
    LocalDateTime generatedAt
) {}
