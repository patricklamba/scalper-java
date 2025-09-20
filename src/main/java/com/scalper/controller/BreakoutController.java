package com.scalper.controller;

import com.scalper.model.entity.SessionBreakout;
import com.scalper.repository.SessionBreakoutRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller REST pour la gestion des breakouts inter-sessions
 */
@Slf4j
@RestController
@RequestMapping("/api/breakouts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Breakouts", description = "API de gestion des breakouts inter-sessions (Asia→London, London→NY)")
public class BreakoutController {

    private final SessionBreakoutRepository breakoutRepository;

    /**
     * Récupère les breakouts récents pour un symbole
     */
    @GetMapping("/recent/{symbol}")
    @Operation(
            summary = "Breakouts récents",
            description = "Récupère les breakouts inter-sessions récents pour un symbole"
    )
    @ApiResponse(responseCode = "200", description = "Breakouts récents récupérés avec succès")
    public ResponseEntity<List<SessionBreakout>> getRecentBreakouts(
            @Parameter(description = "Symbole de trading (ex: EURUSD)", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Heures d'historique")
            @RequestParam(defaultValue = "48") Integer hoursBack) {

        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);

        log.info("Récupération des breakouts récents pour {} depuis {}", symbol, since);

        List<SessionBreakout> breakouts = breakoutRepository.findRecentBreakouts(symbol, since);

        return ResponseEntity.ok(breakouts);
    }

    /**
     * Récupère les breakouts Asia → London
     */
    @GetMapping("/asia-to-london/{symbol}")
    @Operation(
            summary = "Breakouts Asia → London",
            description = "Récupère les breakouts de ranges Asia à l'ouverture de Londres"
    )
    public ResponseEntity<Map<String, Object>> getAsiaToLondonBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "7") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts Asia→London pour {} depuis {}", symbol, since);

        List<SessionBreakout> breakouts = breakoutRepository.findAsiaToLondonBreakouts(symbol, since);

        Map<String, Object> response = buildBreakoutAnalysis(breakouts, "ASIA_TO_LONDON", symbol);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les breakouts London → NY
     */
    @GetMapping("/london-to-ny/{symbol}")
    @Operation(
            summary = "Breakouts London → NY",
            description = "Récupère les breakouts de niveaux London pendant la session NY"
    )
    public ResponseEntity<Map<String, Object>> getLondonToNYBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "7") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts London→NY pour {} depuis {}", symbol, since);

        List<SessionBreakout> breakouts = breakoutRepository.findLondonToNYBreakouts(symbol, since);

        Map<String, Object> response = buildBreakoutAnalysis(breakouts, "LONDON_TO_NY", symbol);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les breakouts avec confirmation technique
     */
    @GetMapping("/confirmed/{symbol}")
    @Operation(
            summary = "Breakouts confirmés techniquement",
            description = "Récupère les breakouts avec confirmation volume et momentum"
    )
    public ResponseEntity<List<SessionBreakout>> getTechnicallyConfirmedBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Momentum minimum (0.0 à 1.0)")
            @RequestParam(defaultValue = "0.6") BigDecimal minMomentum,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts confirmés pour {} avec momentum >= {}", symbol, minMomentum);

        List<SessionBreakout> confirmedBreakouts = breakoutRepository
                .findTechnicallyConfirmedBreakouts(symbol, minMomentum, since);

        return ResponseEntity.ok(confirmedBreakouts);
    }

    /**
     * Récupère les breakouts avec catalyseur news
     */
    @GetMapping("/news-catalyzed/{symbol}")
    @Operation(
            summary = "Breakouts avec catalyseur news",
            description = "Récupère les breakouts ayant eu un catalyseur news"
    )
    public ResponseEntity<List<SessionBreakout>> getNewsCatalyzedBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Minutes maximum de proximité avec news")
            @RequestParam(defaultValue = "60") Integer maxMinutesToNews,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts avec news pour {} (max {} min)", symbol, maxMinutesToNews);

        List<SessionBreakout> newsCatalyzedBreakouts = breakoutRepository
                .findNewsCatalyzedBreakouts(symbol, maxMinutesToNews, since);

        return ResponseEntity.ok(newsCatalyzedBreakouts);
    }

    /**
     * Récupère les breakouts par direction
     */
    @GetMapping("/direction/{symbol}")
    @Operation(
            summary = "Breakouts par direction",
            description = "Récupère les breakouts filtrés par direction (LONG/SHORT)"
    )
    public ResponseEntity<List<SessionBreakout>> getBreakoutsByDirection(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Direction du breakout", required = true)
            @RequestParam @NotNull SessionBreakout.BreakoutDirection direction,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts {} pour {}", direction, symbol);

        List<SessionBreakout> directionalBreakouts = breakoutRepository
                .findBreakoutsByDirection(symbol, direction, since);

        return ResponseEntity.ok(directionalBreakouts);
    }

    /**
     * Récupère les breakouts qui ont été retestés
     */
    @GetMapping("/retested/{symbol}")
    @Operation(
            summary = "Breakouts retestés",
            description = "Récupère les breakouts qui ont subi un retest"
    )
    public ResponseEntity<Map<String, Object>> getRetestedBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts retestés pour {}", symbol);

        List<SessionBreakout> retestedBreakouts = breakoutRepository.findRetestedBreakouts(symbol, since);
        List<SessionBreakout> successfulRetests = breakoutRepository.findSuccessfulRetests(symbol, since);

        Map<String, Object> response = Map.of(
                "symbol", symbol,
                "analysis_period_days", daysBack,
                "total_retested", retestedBreakouts.size(),
                "successful_retests", successfulRetests.size(),
                "retest_success_rate", calculateRetestSuccessRate(retestedBreakouts.size(), successfulRetests.size()),
                "retested_breakouts", retestedBreakouts,
                "successful_retests", successfulRetests
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les statistiques de performance des breakouts
     */
    @GetMapping("/performance/{symbol}")
    @Operation(
            summary = "Performance des breakouts",
            description = "Analyse de performance des breakouts par type et direction"
    )
    public ResponseEntity<Map<String, Object>> getBreakoutPerformance(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "60") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse de performance des breakouts pour {} sur {} jours", symbol, daysBack);

        List<Map<String, Object>> performanceStats = breakoutRepository
                .getBreakoutPerformanceStats(symbol, since);

        List<Map<String, Object>> successRateBySession = breakoutRepository
                .getBreakoutSuccessRateBySession(symbol, since);

        List<SessionBreakout> topPerforming = breakoutRepository
                .findTopPerformingBreakouts(symbol, 30, since); // 30+ pips follow-through

        Map<String, Object> response = Map.of(
                "symbol", symbol,
                "analysis_period_days", daysBack,
                "performance_by_type", performanceStats,
                "success_rate_by_session", successRateBySession,
                "top_performing_breakouts", topPerforming,
                "overall_metrics", calculateOverallMetrics(performanceStats)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les patterns récurrents de breakouts
     */
    @GetMapping("/patterns/{symbol}")
    @Operation(
            summary = "Patterns récurrents de breakouts",
            description = "Analyse des patterns de breakouts les plus fréquents"
    )
    public ResponseEntity<List<Map<String, Object>>> getRecurringBreakoutPatterns(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Occurrences minimum pour être considéré récurrent")
            @RequestParam(defaultValue = "3") Long minOccurrences,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "90") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse des patterns récurrents pour {} (min {} occurrences)", symbol, minOccurrences);

        List<Map<String, Object>> recurringPatterns = breakoutRepository
                .findRecurringBreakoutPatterns(symbol, since, minOccurrences);

        return ResponseEntity.ok(recurringPatterns);
    }

    /**
     * Récupère la distribution temporelle des breakouts
     */
    @GetMapping("/time-distribution/{symbol}")
    @Operation(
            summary = "Distribution temporelle",
            description = "Analyse de la distribution des breakouts par heure de la journée"
    )
    public ResponseEntity<List<Map<String, Object>>> getBreakoutTimeDistribution(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "60") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse de distribution temporelle des breakouts pour {}", symbol);

        List<Map<String, Object>> timeDistribution = breakoutRepository
                .getBreakoutTimeDistribution(symbol, since);

        return ResponseEntity.ok(timeDistribution);
    }

    /**
     * Détection de breakout en temps réel
     */
    @PostMapping("/detect/{symbol}")
    @Operation(
            summary = "Détection breakout temps réel",
            description = "Lance la détection de breakout pour un symbole à l'instant présent"
    )
    public ResponseEntity<Map<String, Object>> detectBreakout(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol) {

        log.info("Détection de breakout en temps réel pour {}", symbol);

        // Cette méthode sera implémentée avec les services de pattern detection
        // Pour l'instant, retour d'un placeholder

        Map<String, Object> response = Map.of(
                "symbol", symbol,
                "scan_timestamp", LocalDateTime.now(),
                "message", "Detection service will be implemented in pattern detection phase",
                "current_session", getCurrentSession(),
                "next_scan_in_minutes", 5
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les breakouts avec momentum exceptionnel
     */
    @GetMapping("/high-momentum/{symbol}")
    @Operation(
            summary = "Breakouts à momentum élevé",
            description = "Récupère les breakouts avec momentum et volume exceptionnels"
    )
    public ResponseEntity<List<SessionBreakout>> getHighMomentumBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Strength minimum (0.0 à 1.0)")
            @RequestParam(defaultValue = "0.8") BigDecimal minStrength,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des breakouts high momentum pour {} (strength >= {})", symbol, minStrength);

        List<SessionBreakout> highMomentumBreakouts = breakoutRepository
                .findHighMomentumBreakouts(symbol, minStrength, since);

        return ResponseEntity.ok(highMomentumBreakouts);
    }

    /**
     * Analyse de corrélation momentum vs performance
     */
    @GetMapping("/momentum-correlation/{symbol}")
    @Operation(
            summary = "Corrélation momentum/performance",
            description = "Analyse la corrélation entre la force du momentum et la performance"
    )
    public ResponseEntity<List<Map<String, Object>>> analyzeMomentumCorrelation(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "90") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse corrélation momentum/performance pour {}", symbol);

        List<Map<String, Object>> correlation = breakoutRepository
                .analyzeBreakoutMomentumCorrelation(symbol, since);

        return ResponseEntity.ok(correlation);
    }

    /**
     * Helper methods pour la construction des réponses
     */
    private Map<String, Object> buildBreakoutAnalysis(List<SessionBreakout> breakouts, String breakoutType, String symbol) {
        Map<String, Object> analysis = new java.util.HashMap<>();

        analysis.put("symbol", symbol);
        analysis.put("breakout_type", breakoutType);
        analysis.put("total_breakouts", breakouts.size());

        // Séparation par direction
        long longBreakouts = breakouts.stream()
                .filter(b -> b.getBreakoutDirection() == SessionBreakout.BreakoutDirection.LONG)
                .count();
        long shortBreakouts = breakouts.size() - longBreakouts;

        analysis.put("long_breakouts", longBreakouts);
        analysis.put("short_breakouts", shortBreakouts);
        analysis.put("directional_bias", longBreakouts > shortBreakouts ? "BULLISH" : "BEARISH");

        // Breakouts avec confirmation technique
        long confirmedBreakouts = breakouts.stream()
                .filter(SessionBreakout::isTechnicallyConfirmed)
                .count();

        analysis.put("technically_confirmed", confirmedBreakouts);
        analysis.put("confirmation_rate", calculatePercentage(confirmedBreakouts, breakouts.size()));

        // Breakouts avec catalyseur news
        long newsCatalyzed = breakouts.stream()
                .filter(SessionBreakout::hasNewsCatalyst)
                .count();

        analysis.put("news_catalyzed", newsCatalyzed);
        analysis.put("news_catalyst_rate", calculatePercentage(newsCatalyzed, breakouts.size()));

        // Performance moyenne
        double avgFollowThrough = breakouts.stream()
                .filter(b -> b.getMaxFollowThroughPips() != null)
                .mapToInt(SessionBreakout::getMaxFollowThroughPips)
                .average()
                .orElse(0.0);

        analysis.put("avg_follow_through_pips", avgFollowThrough);
        analysis.put("breakouts", breakouts);

        return analysis;
    }

    private Map<String, Object> calculateOverallMetrics(List<Map<String, Object>> performanceStats) {
        if (performanceStats.isEmpty()) {
            return Map.of("message", "No data available");
        }

        // Calculs d'agrégation sur les stats de performance
        int totalBreakouts = performanceStats.stream()
                .mapToInt(stat -> ((Number) stat.get("totalCount")).intValue())
                .sum();

        double avgMomentum = performanceStats.stream()
                .filter(stat -> stat.get("avgMomentum") != null)
                .mapToDouble(stat -> ((Number) stat.get("avgMomentum")).doubleValue())
                .average()
                .orElse(0.0);

        return Map.of(
                "total_breakouts_analyzed", totalBreakouts,
                "average_momentum_strength", avgMomentum,
                "most_frequent_pattern", findMostFrequentPattern(performanceStats),
                "analysis_quality", totalBreakouts > 10 ? "HIGH" : "LOW"
        );
    }

    private String findMostFrequentPattern(List<Map<String, Object>> stats) {
        return stats.stream()
                .max((a, b) -> Integer.compare(
                        ((Number) a.get("totalCount")).intValue(),
                        ((Number) b.get("totalCount")).intValue()
                ))
                .map(stat -> (String) stat.get("breakoutType"))
                .orElse("UNKNOWN");
    }

    private double calculateRetestSuccessRate(int totalRetested, int successfulRetests) {
        return totalRetested > 0 ? (double) successfulRetests / totalRetested * 100 : 0.0;
    }

    private double calculatePercentage(long numerator, long denominator) {
        return denominator > 0 ? (double) numerator / denominator * 100 : 0.0;
    }

    private String getCurrentSession() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 6) return "ASIA";
        if (hour >= 7 && hour < 12) return "LONDON";
        if (hour >= 12 && hour < 17) return "NEWYORK";
        return "AFTER_HOURS";
    }
}