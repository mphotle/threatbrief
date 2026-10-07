package com.mphotle.threatbrief.controller;

import com.mphotle.threatbrief.model.ThreatBrief;
import com.mphotle.threatbrief.service.ThreatBriefService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * REST controller exposing reactive endpoints for querying daily security threat briefings.
 */
@RestController
public class ThreatBriefController {

    private final ThreatBriefService threatBriefService;

    public ThreatBriefController(ThreatBriefService threatBriefService) {
        this.threatBriefService = threatBriefService;
    }

    @GetMapping("/brief")
    public Mono<ThreatBrief> getBrief(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(ZoneOffset.UTC);
        return threatBriefService.getBriefForDate(targetDate);
    }
}
