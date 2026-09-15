package com.mphotle.threatbrief.model;

import java.time.LocalDate;
import java.util.List;

public record DailyVulnerabilities(
    LocalDate date,
    int totalCount,
    List<VulnerabilityItem> items
) {}
