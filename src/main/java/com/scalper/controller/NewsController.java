package com.scalper.controller;

import com.scalper.model.entity.NewsAlert;
import com.scalper.repository.NewsAlertRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller REST pour la gestion des alertes news économiques
 */
@Slf4j
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Validated
@Tag(name = "News", description = "API de gestion des alertes news économiques depuis n8n")
public class NewsController {

    private final NewsAlertRepository newsRepository;

    /**
     * Webhook pour recevoir les alertes news depuis n8n
     */
    @PostMapping("/webhook/alert")
    @Operation(
            summary = "Webhook alertes news",
            description = "Point d'entrée pour les alertes news envoyées par n8n"
    )
    @ApiResponse(responseCode = "201", description = "Alerte news créée avec succès")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    public ResponseEntity<Map<String, Object>> receiveNewsAlert(
            @Parameter(description = "Données de l'alerte news", required = true)
            @Valid @RequestBody NewsAlertRequest newsRequest) {

        log.info("Réception alerte news: {} - {} {}",
                newsRequest.getSource(), newsRequest.getCurrency(), newsRequest.getEventTitle());

        try {
            // Conversion du DTO vers entité
            NewsAlert newsAlert = convertToNewsAlert(newsRequest);

            // Sauvegarde en base
            NewsAlert savedAlert = newsRepository.save(newsAlert);

            log.info("Alerte news sauvegardée avec ID: {}", savedAlert.getId());

            Map<String, Object> response = Map.of(
                    "status", "success",
                    "message", "News alert received and processed",
                    "alert_id", savedAlert.getId(),
                    "event_title", savedAlert.getEventTitle(),
                    "impact_level", savedAlert.getImpactLevel(),
                    "target_session", savedAlert.getTargetTradingSession(),
                    "pattern_impact", savedAlert.evaluatePatternImpact()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'alerte news", e);

            Map<String, Object> errorResponse = Map.of(
                    "status", "error",
                    "message", "Failed to process news alert",
                    "error", e.getMessage()
            );

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Récupère les news HIGH impact récentes
     */
    @GetMapping("/high-impact")
    @Operation(
            summary = "News HIGH impact récentes",
            description = "Récupère les alertes news de fort impact récentes"
    )
    public ResponseEntity<List<NewsAlert>> getRecentHighImpactNews(
            @Parameter(description = "Heures d'historique")
            @RequestParam(defaultValue = "24") Integer hoursBack) {

        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);

        log.info("Récupération des news HIGH impact depuis {}", since);

        List<NewsAlert> highImpactNews = newsRepository.findRecentHighImpactNews(since);

        return ResponseEntity.ok(highImpactNews);
    }

    /**
     * Récupère les news par devise
     */
    @GetMapping("/currency/{currency}")
    @Operation(
            summary = "News par devise",
            description = "Récupère les alertes news pour une devise spécifique"
    )
    public ResponseEntity<List<NewsAlert>> getNewsByCurrency(
            @Parameter(description = "Code devise (EUR, USD, XAU)", required = true)
            @PathVariable @NotBlank String currency,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "7") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des news pour {} depuis {}", currency, since);

        List<NewsAlert> currencyNews = newsRepository.findNewsByCurrency(currency, since);

        return ResponseEntity.ok(currencyNews);
    }

    /**
     * Récupère les prochaines news importantes
     */
    @GetMapping("/upcoming")
    @Operation(
            summary = "Prochaines news importantes",
            description = "Récupère les prochaines alertes news HIGH impact"
    )
    public ResponseEntity<Map<String, Object>> getUpcomingNews(
            @Parameter(description = "Nombre maximum de news à retourner")
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("Récupération des {} prochaines news HIGH impact", limit);

        List<NewsAlert> upcomingNews = newsRepository
                .findUpcomingHighImpactNews(LocalDateTime.now(), limit);

        Map<String, Object> response = Map.of(
                "upcoming_news", upcomingNews,
                "count", upcomingNews.size(),
                "next_major_event", upcomingNews.isEmpty() ? null : upcomingNews.get(0),
                "trading_impact_analysis", analyzeUpcomingNewsImpact(upcomingNews)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les news dans une fenêtre temporelle
     */
    @GetMapping("/timeframe")
    @Operation(
            summary = "News dans fenêtre temporelle",
            description = "Récupère les news dans une fenêtre de temps spécifique"
    )
    public ResponseEntity<List<NewsAlert>> getNewsInTimeWindow(
            @Parameter(description = "Début de fenêtre", required = true)
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "Fin de fenêtre", required = true)
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("Récupération des news entre {} et {}", startTime, endTime);

        List<NewsAlert> newsInWindow = newsRepository.findNewsInTimeWindow(startTime, endTime);

        return ResponseEntity.ok(newsInWindow);
    }

    /**
     * Recherche news près d'un moment donné
     */
    @GetMapping("/near-time")
    @Operation(
            summary = "News près d'un moment",
            description = "Trouve les news HIGH impact proches d'un moment donné"
    )
    public ResponseEntity<Map<String, Object>> getNewsNearTime(
            @Parameter(description = "Moment cible", required = true)
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime,

            @Parameter(description = "Tolérance en minutes")
            @RequestParam(defaultValue = "30") Integer toleranceMinutes) {

        log.info("Recherche news près de {} (+/- {} min)", targetTime, toleranceMinutes);

        List<NewsAlert> nearbyNews = newsRepository.findNewsNearTime(targetTime, toleranceMinutes);

        Map<String, Object> response = Map.of(
                "target_time", targetTime,
                "tolerance_minutes", toleranceMinutes,
                "nearby_news", nearbyNews,
                "news_count", nearbyNews.size(),
                "has_high_impact_nearby", !nearbyNews.isEmpty()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les news avec surprises (actual ≠ forecast)
     */
    @GetMapping("/surprises")
    @Operation(
            summary = "News avec surprises",
            description = "Récupère les news où la valeur réelle diffère du consensus"
    )
    public ResponseEntity<Map<String, Object>> getNewsSurprises(
            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "7") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des surprises news depuis {}", since);

        List<NewsAlert> surprises = newsRepository.findNewsSurprises(since);

        Map<String, Object> response = Map.of(
                "news_surprises", surprises,
                "surprise_count", surprises.size(),
                "analysis_period_days", daysBack,
                "surprise_analysis", analyzeSurprises(surprises)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les news par session de trading
     */
    @GetMapping("/session/{session}")
    @Operation(
            summary = "News par session",
            description = "Récupère les news pendant une session de trading spécifique"
    )
    public ResponseEntity<List<NewsAlert>> getNewsBySession(
            @Parameter(description = "Session de trading", required = true)
            @PathVariable @NotBlank String session,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des news session {} depuis {}", session, since);

        List<NewsAlert> sessionNews = switch (session.toUpperCase()) {
            case "LONDON" -> newsRepository.findLondonSessionNews(since);
            case "NY", "NEWYORK" -> newsRepository.findNYSessionNews(since);
            default -> {
                log.warn("Session inconnue: {}, retour de liste vide", session);
                yield List.of();
            }
        };

        return ResponseEntity.ok(sessionNews);
    }

    /**
     * Récupère les statistiques des news
     */
    @GetMapping("/statistics")
    @Operation(
            summary = "Statistiques des news",
            description = "Récupère les statistiques détaillées des news par devise et impact"
    )
    public ResponseEntity<Map<String, Object>> getNewsStatistics(
            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Génération des statistiques news sur {} jours", daysBack);

        List<Map<String, Object>> statsByCurrency = newsRepository
                .getNewsStatisticsByCurrencyAndImpact(since);

        List<Map<String, Object>> timeDistribution = newsRepository
                .getNewsTimeDistribution(since);

        List<Map<String, Object>> sourceAnalysis = newsRepository
                .getNewsSourceAnalysis(since);

        Map<String, Object> response = Map.of(
                "analysis_period_days", daysBack,
                "statistics_by_currency", statsByCurrency,
                "time_distribution", timeDistribution,
                "source_analysis", sourceAnalysis,
                "summary", generateStatisticsSummary(statsByCurrency)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les news pouvant servir de catalyseur
     */
    @GetMapping("/breakout-catalysts")
    @Operation(
            summary = "Catalyseurs de breakout",
            description = "Récupère les news pouvant servir de catalyseur pour breakouts"
    )
    public ResponseEntity<List<NewsAlert>> getBreakoutCatalysts(
            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "7") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des catalyseurs de breakout depuis {}", since);

        List<NewsAlert> catalysts = newsRepository.findBreakoutCatalystCandidates(since);

        return ResponseEntity.ok(catalysts);
    }

    /**
     * Vérifie la présence de news HIGH impact dans une fenêtre
     */
    @GetMapping("/check-window")
    @Operation(
            summary = "Vérification fenêtre news",
            description = "Vérifie s'il y a des news HIGH impact dans une fenêtre temporelle"
    )
    public ResponseEntity<Map<String, Object>> checkNewsInWindow(
            @Parameter(description = "Début de fenêtre", required = true)
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "Fin de fenêtre", required = true)
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("Vérification news HIGH impact entre {} et {}", startTime, endTime);

        boolean hasHighImpactNews = newsRepository.hasHighImpactNewsInWindow(startTime, endTime);

        Map<String, Object> response = Map.of(
                "start_time", startTime,
                "end_time", endTime,
                "has_high_impact_news", hasHighImpactNews,
                "trading_recommendation", hasHighImpactNews ? "AVOID_TRADING" : "SAFE_TO_TRADE",
                "window_duration_minutes", java.time.Duration.between(startTime, endTime).toMinutes()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Helper methods et DTOs
     */
    private NewsAlert convertToNewsAlert(NewsAlertRequest request) {
        return NewsAlert.builder()
                .source(request.getSource())
                .eventTime(request.getEventTime())
                .currency(request.getCurrency())
                .impactLevel(request.getImpactLevel())
                .eventTitle(request.getEventTitle())
                .actualValue(request.getActualValue())
                .forecastValue(request.getForecastValue())
                .previousValue(request.getPreviousValue())
                .marketContext(request.getMarketContext() != null ?
                        request.getMarketContext() : NewsAlert.MarketContext.NEUTRAL)
                .build();
    }

    private Map<String, Object> analyzeUpcomingNewsImpact(List<NewsAlert> upcomingNews) {
        if (upcomingNews.isEmpty()) {
            return Map.of("impact_assessment", "MINIMAL", "message", "No major news scheduled");
        }

        // Analyse de l'impact potentiel
        long eurNews = upcomingNews.stream().filter(n -> "EUR".equals(n.getCurrency())).count();
        long usdNews = upcomingNews.stream().filter(n -> "USD".equals(n.getCurrency())).count();

        NewsAlert nextEvent = upcomingNews.get(0);
        long minutesToNext = java.time.Duration.between(LocalDateTime.now(), nextEvent.getEventTime()).toMinutes();

        String impactLevel = "MEDIUM";
        if (eurNews > 0 && usdNews > 0) impactLevel = "HIGH";
        else if (minutesToNext <= 60) impactLevel = "HIGH";

        return Map.of(
                "impact_assessment", impactLevel,
                "eur_events", eurNews,
                "usd_events", usdNews,
                "minutes_to_next_event", minutesToNext,
                "next_event_title", nextEvent.getEventTitle(),
                "trading_advice", minutesToNext <= 30 ? "PREPARE_FOR_VOLATILITY" : "MONITOR_CLOSELY"
        );
    }

    private Map<String, Object> analyzeSurprises(List<NewsAlert> surprises) {
        Map<String, Long> byCurrency = surprises.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        NewsAlert::getCurrency,
                        java.util.stream.Collectors.counting()
                ));

        return Map.of(
                "surprises_by_currency", byCurrency,
                "total_surprises", surprises.size(),
                "impact_on_trading", surprises.size() > 5 ? "HIGH_VOLATILITY_PERIOD" : "NORMAL_VOLATILITY"
        );
    }

    private Map<String, Object> generateStatisticsSummary(List<Map<String, Object>> stats) {
        int totalNews = stats.stream()
                .mapToInt(stat -> ((Number) stat.get("count")).intValue())
                .sum();

        long highImpactCount = stats.stream()
                .filter(stat -> "HIGH".equals(stat.get("impact")))
                .mapToLong(stat -> ((Number) stat.get("count")).longValue())
                .sum();

        return Map.of(
                "total_news_events", totalNews,
                "high_impact_events", highImpactCount,
                "high_impact_percentage", totalNews > 0 ? (double) highImpactCount / totalNews * 100 : 0,
                "most_active_currency", findMostActiveCurrency(stats)
        );
    }

    private String findMostActiveCurrency(List<Map<String, Object>> stats) {
        return stats.stream()
                .max((a, b) -> Integer.compare(
                        ((Number) a.get("count")).intValue(),
                        ((Number) b.get("count")).intValue()
                ))
                .map(stat -> (String) stat.get("currency"))
                .orElse("UNKNOWN");
    }

    /**
     * DTO pour les requêtes d'alertes news
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NewsAlertRequest {
        @NotBlank
        private String source;

        @NotNull
        private LocalDateTime eventTime;

        @NotBlank
        private String currency;

        @NotNull
        private NewsAlert.ImpactLevel impactLevel;

        @NotBlank
        private String eventTitle;

        private String actualValue;
        private String forecastValue;
        private String previousValue;
        private NewsAlert.MarketContext marketContext;
    }
}