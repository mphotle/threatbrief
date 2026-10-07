package com.mphotle.threatbrief.scheduler;

import com.mphotle.threatbrief.service.ThreatBriefService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background component responsible for warming and maintaining the rolling 14-day threat intelligence cache.
 * <p>
 * Triggers automatic briefing pre-generation upon application startup and at hourly intervals to ensure near-zero 
 * latency for user requests. The hourly intervals also ensure that new publications are cached within the hour of their
 * publications since they are not necessarily published at the beginning of the day.
 * </p>
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "threatbrief.pregenerate.enabled", havingValue = "true", matchIfMissing = true)
public class ThreatBriefScheduler {

    private final ThreatBriefService threatBriefService;

    public ThreatBriefScheduler(ThreatBriefService threatBriefService) {
        this.threatBriefService = threatBriefService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pregenerateOnStartup() {
        threatBriefService.pregenerateBriefsForPast14Days();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void pregenerateHourly() {
        threatBriefService.pregenerateBriefsForPast14Days();
    }
}
