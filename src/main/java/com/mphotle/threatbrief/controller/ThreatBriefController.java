package com.mphotle.threatbrief.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.service.NvdService;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
class ThreatBriefController {
    private final NvdService nvdService;

    public ThreatBriefController(NvdService nvdService) {
        this.nvdService = nvdService;
    }

    @GetMapping("/vulnerabilities")
    public Mono<DailyVulnerabilities> get(
        @RequestParam(required = false) 
        @DateTimeFormat(iso =DateTimeFormat.ISO.DATE)
        LocalDate date
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(ZoneOffset.UTC);
        return nvdService.fetchVulnerabilitiesForDate(targetDate);
    }

}
