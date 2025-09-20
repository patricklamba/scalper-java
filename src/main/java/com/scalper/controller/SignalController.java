package com.scalper.controller;

import com.scalper.model.entity.TradingSignalEnriched;
import com.scalper.repository.TradingSignalEnrichedRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
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
 * Controller REST pour la gestion des signaux de trading enrichis multi-sessions
 */
@Slf4j
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
@Validated
@Tag(name = "Signals", description = "API de gestion des signaux de trading enrichis avec contexte multi-sessions")
public class SignalController {

    private final TradingSignalEnrichedRepository signalRepository;

    /**
     * Récupère les signaux actifs pour un symbole
     */
    @GetMapping("/active/{symbol}")
    @Operation(
            summary = "Signaux actifs",
            description = "Récupère tous les signaux de trading actifs pour un symbole"
    )
    @ApiResponse(responseCode = "200", description = "Signaux actifs récupérés avec succès")
    public ResponseEntity<List<TradingSignalEnriched>> getActiveSignals(
            @Parameter(description = "Symbole de trading (ex: EURUSD)", required = true)
            @PathVariable @NotBlank String symbol) {

        log.info("Récupération des signaux actifs pour {}", symbol);

        List<TradingSignalEnriched> activeSignals = signalRepository.findActiveSignals(symbol);

        return ResponseEntity.ok(activeSignals);
    }

    /**
     * Récupère les derniers signaux générés
     */
    @GetMapping("/latest/{symbol}")
    @Operation(
            summary = "Derniers signaux",
            description = "Récupère les derniers signaux générés pour un symbole"
    )
    public ResponseEntity<List<TradingSignalEnriched>> getLatestSignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de signaux à récupérer")
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("Récupération des {} derniers signaux pour {}", limit, symbol);

        List<TradingSignalEnriched> latestSignals = signalRepository.findLatestSignals(symbol, limit);

        return ResponseEntity.ok(latestSignals);
    }

    /**
     * Récupère les signaux par type de setup
     */
    @GetMapping("/setup/{symbol}")
    @Operation(
            summary = "Signaux par type de setup",
            description = "Récupère les signaux filtrés par type de setup spécifique"
    )
    public ResponseEntity<List<TradingSignalEnriched>> getSignalsBySetupType(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Type de setup", required = true)
            @RequestParam @NotBlank String setupType,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "7") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux {} pour {} depuis {}", setupType, symbol, since);

        List<TradingSignalEnriched> signals = signalRepository
                .findSignalsBySetupType(symbol, setupType, since);

        return ResponseEntity.ok(signals);
    }

    /**
     * Récupère les signaux de haute qualité
     */
    @GetMapping("/high-quality/{symbol}")
    @Operation(
            summary = "Signaux de haute qualité",
            description = "Récupère les signaux avec haute confiance et bon risk/reward"
    )
    public ResponseEntity<Map<String, Object>> getHighQualitySignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Confiance minimum (0.0 à 1.0)")
            @RequestParam(defaultValue = "0.8") @DecimalMin("0.0") BigDecimal minConfidence,

            @Parameter(description = "Risk/Reward minimum")
            @RequestParam(defaultValue = "1.5") @DecimalMin("0.0") BigDecimal minRR,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux haute qualité pour {} (conf >= {}, RR >= {})",
                symbol, minConfidence, minRR);

        List<TradingSignalEnriched> highQualitySignals = signalRepository
                .findHighQualitySignals(symbol, minConfidence, minRR, since);

        Map<String, Object> response = buildQualityAnalysis(highQualitySignals, symbol, minConfidence, minRR);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les signaux par session de trading
     */
    @GetMapping("/session/{symbol}")
    @Operation(
            summary = "Signaux par session",
            description = "Récupère les signaux générés pendant une session spécifique"
    )
    public ResponseEntity<List<TradingSignalEnriched>> getSignalsBySession(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Session de trading", required = true)
            @RequestParam @NotBlank String session,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux session {} pour {}", session, symbol);

        List<TradingSignalEnriched> sessionSignals = signalRepository
                .findSignalsBySession(symbol, session, since);

        return ResponseEntity.ok(sessionSignals);
    }

    /**
     * Récupère les signaux breakout Asia → London
     */
    @GetMapping("/asia-breakout/{symbol}")
    @Operation(
            summary = "Signaux breakout Asia",
            description = "Récupère les signaux de breakout de range Asia à l'ouverture Londres"
    )
    public ResponseEntity<Map<String, Object>> getAsiaBreakoutSignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux Asia breakout pour {}", symbol);

        List<TradingSignalEnriched> asiaBreakoutSignals = signalRepository
                .findAsiaBreakoutSignals(symbol, since);

        Map<String, Object> response = buildSetupSpecificAnalysis(
                asiaBreakoutSignals, "ASIA_BREAKOUT_AT_LONDON", symbol);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les signaux retest London → NY
     */
    @GetMapping("/london-retest/{symbol}")
    @Operation(
            summary = "Signaux retest London",
            description = "Récupère les signaux de retest de niveaux London pendant session NY"
    )
    public ResponseEntity<Map<String, Object>> getLondonRetestSignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux London retest pour {}", symbol);

        List<TradingSignalEnriched> londonRetestSignals = signalRepository
                .findLondonRetestSignals(symbol, since);

        Map<String, Object> response = buildSetupSpecificAnalysis(
                londonRetestSignals, "LONDON_RETEST_AT_NY", symbol);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les signaux influencés par les news
     */
    @GetMapping("/news-influenced/{symbol}")
    @Operation(
            summary = "Signaux influencés par news",
            description = "Récupère les signaux générés en contexte de news importantes"
    )
    public ResponseEntity<List<TradingSignalEnriched>> getNewsInfluencedSignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux influencés par news pour {}", symbol);

        List<TradingSignalEnriched> newsInfluencedSignals = signalRepository
                .findNewsInfluencedSignals(symbol, since);

        return ResponseEntity.ok(newsInfluencedSignals);
    }

    /**
     * Récupère les statistiques de performance par setup
     */
    @GetMapping("/performance/{symbol}")
    @Operation(
            summary = "Performance par setup",
            description = "Analyse de performance des signaux par type de setup"
    )
    public ResponseEntity<Map<String, Object>> getSetupPerformance(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "60") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse de performance des setups pour {} sur {} jours", symbol, daysBack);

        List<Map<String, Object>> performanceStats = signalRepository
                .getSetupPerformanceStats(symbol, since);

        List<Map<String, Object>> qualityMetrics = signalRepository
                .getQualityMetricsBySession(symbol, since);

        Map<String, Object> response = Map.of(
                "symbol", symbol,
                "analysis_period_days", daysBack,
                "performance_by_setup", performanceStats,
                "quality_by_session", qualityMetrics,
                "overall_summary", calculateOverallSummary(performanceStats)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère la distribution temporelle des signaux
     */
    @GetMapping("/time-distribution/{symbol}")
    @Operation(
            summary = "Distribution temporelle",
            description = "Analyse de la distribution des signaux par heure et session"
    )
    public ResponseEntity<List<Map<String, Object>>> getSignalTimeDistribution(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse de distribution temporelle des signaux pour {}", symbol);

        List<Map<String, Object>> timeDistribution = signalRepository
                .getSignalTimeDistribution(symbol, since);

        return ResponseEntity.ok(timeDistribution);
    }

    /**
     * Récupère les signaux avec probabilité de succès élevée
     */
    @GetMapping("/high-probability/{symbol}")
    @Operation(
            summary = "Signaux haute probabilité",
            description = "Récupère les signaux avec probabilité de succès élevée"
    )
    public ResponseEntity<List<TradingSignalEnriched>> getHighProbabilitySignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Probabilité minimum (0.0 à 1.0)")
            @RequestParam(defaultValue = "0.75") @DecimalMin("0.0") BigDecimal minProbability,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "14") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux haute probabilité pour {} (prob >= {})",
                symbol, minProbability);

        List<TradingSignalEnriched> highProbSignals = signalRepository
                .findHighProbabilitySignals(symbol, minProbability, since);

        return ResponseEntity.ok(highProbSignals);
    }

    /**
     * Analyse de corrélation confiance vs performance
     */
    @GetMapping("/confidence-correlation/{symbol}")
    @Operation(
            summary = "Corrélation confiance/performance",
            description = "Analyse la corrélation entre niveau de confiance et performance"
    )
    public ResponseEntity<List<Map<String, Object>>> analyzeConfidenceCorrelation(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "90") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse corrélation confiance/performance pour {}", symbol);

        List<Map<String, Object>> correlation = signalRepository
                .analyzeConfidencePerformanceCorrelation(symbol, since);

        return ResponseEntity.ok(correlation);
    }

    /**
     * Récupère les patterns de signaux récurrents
     */
    @GetMapping("/patterns/{symbol}")
    @Operation(
            summary = "Patterns récurrents",
            description = "Analyse des patterns de signaux les plus fréquents"
    )
    public ResponseEntity<List<Map<String, Object>>> getRecurringSignalPatterns(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Occurrences minimum")
            @RequestParam(defaultValue = "3") Long minOccurrences,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "90") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Analyse des patterns récurrents pour {} (min {} occurrences)",
                symbol, minOccurrences);

        List<Map<String, Object>> recurringPatterns = signalRepository
                .findRecurringSignalPatterns(symbol, since, minOccurrences);

        return ResponseEntity.ok(recurringPatterns);
    }

    /**
     * Récupère les signaux avec risk/reward exceptionnel
     */
    @GetMapping("/exceptional-rr/{symbol}")
    @Operation(
            summary = "Signaux risk/reward exceptionnel",
            description = "Récupère les signaux avec ratio risk/reward exceptionnel"
    )
    public ResponseEntity<List<TradingSignalEnriched>> getExceptionalRiskRewardSignals(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Risk/Reward minimum")
            @RequestParam(defaultValue = "3.0") @DecimalMin("0.0") BigDecimal minRR,

            @Parameter(description = "Confiance minimum")
            @RequestParam(defaultValue = "0.7") @DecimalMin("0.0") BigDecimal minConfidence,

            @Parameter(description = "Nombre de jours d'historique")
            @RequestParam(defaultValue = "30") Integer daysBack) {

        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        log.info("Récupération des signaux RR exceptionnel pour {} (RR >= {}, conf >= {})",
                symbol, minRR, minConfidence);

        List<TradingSignalEnriched> exceptionalSignals = signalRepository
                .findExceptionalRiskRewardSignals(symbol, minRR, minConfidence, since);

        return ResponseEntity.ok(exceptionalSignals);
    }

    /**
     * Génération de contexte IA pour un signal
     */
    @GetMapping("/ai-context/{signalId}")
    @Operation(
            summary = "Contexte IA pour signal",
            description = "Génère le contexte enrichi pour analyse IA externe d'un signal spécifique"
    )
    public ResponseEntity<Map<String, Object>> getSignalAIContext(
            @Parameter(description = "ID du signal", required = true)
            @PathVariable @NotNull Long signalId) {

        log.info("Génération du contexte IA pour signal {}", signalId);

        var signal = signalRepository.findById(signalId);

        if (signal.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> aiContext = signal.get().generateAIContext();

        return ResponseEntity.ok(aiContext);
    }

    /**
     * Helper methods pour la construction des réponses
     */
    private Map<String, Object> buildQualityAnalysis(List<TradingSignalEnriched> signals, String symbol,
                                                     BigDecimal minConfidence, BigDecimal minRR) {
        Map<String, Object> analysis = new java.util.HashMap<>();

        analysis.put("symbol", symbol);
        analysis.put("filter_criteria", Map.of(
                "min_confidence", minConfidence,
                "min_risk_reward", minRR
        ));
        analysis.put("high_quality_signals", signals);
        analysis.put("total_count", signals.size());

        // Analyse par direction
        long longSignals = signals.stream()
                .filter(s -> s.getSignalDirection() == TradingSignalEnriched.SignalDirection.LONG)
                .count();

        analysis.put("directional_distribution", Map.of(
                "long_signals", longSignals,
                "short_signals", signals.size() - longSignals,
                "bias", longSignals > (signals.size() / 2) ? "BULLISH" : "BEARISH"
        ));

        // Métriques de qualité moyennes
        double avgConfidence = signals.stream()
                .mapToDouble(s -> s.getConfidenceScore().doubleValue())
                .average().orElse(0.0);

        double avgRR = signals.stream()
                .mapToDouble(s -> s.getRiskRewardRatio().doubleValue())
                .average().orElse(0.0);

        analysis.put("average_metrics", Map.of(
                "confidence", avgConfidence,
                "risk_reward", avgRR
        ));

        return analysis;
    }

    private Map<String, Object> buildSetupSpecificAnalysis(List<TradingSignalEnriched> signals,
                                                           String setupType, String symbol) {
        Map<String, Object> analysis = new java.util.HashMap<>();

        analysis.put("symbol", symbol);
        analysis.put("setup_type", setupType);
        analysis.put("signals", signals);
        analysis.put("total_count", signals.size());

        if (!signals.isEmpty()) {
            // Performance moyenne
            double avgConfidence = signals.stream()
                    .mapToDouble(s -> s.getConfidenceScore().doubleValue())
                    .average().orElse(0.0);

            double avgRR = signals.stream()
                    .mapToDouble(s -> s.getRiskRewardRatio().doubleValue())
                    .average().orElse(0.0);

            // Distribution par session
            Map<String, Long> sessionDistribution = signals.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            TradingSignalEnriched::getPrimarySession,
                            java.util.stream.Collectors.counting()
                    ));

            analysis.put("performance_metrics", Map.of(
                    "average_confidence", avgConfidence,
                    "average_risk_reward", avgRR,
                    "quality_grade", avgConfidence > 0.8 ? "HIGH" : avgConfidence > 0.6 ? "MEDIUM" : "LOW"
            ));

            analysis.put("session_distribution", sessionDistribution);

            // Signaux récents vs anciens
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            long recentSignals = signals.stream()
                    .filter(s -> s.getCreatedAt().isAfter(cutoff))
                    .count();

            analysis.put("temporal_analysis", Map.of(
                    "recent_signals_7d", recentSignals,
                    "historical_signals", signals.size() - recentSignals,
                    "frequency_trend", recentSignals > (signals.size() / 4) ? "INCREASING" : "STABLE"
            ));
        }

        return analysis;
    }

    private Map<String, Object> calculateOverallSummary(List<Map<String, Object>> performanceStats) {
        if (performanceStats.isEmpty()) {
            return Map.of("message", "No performance data available");
        }

        int totalSignals = performanceStats.stream()
                .mapToInt(stat -> ((Number) stat.get("totalCount")).intValue())
                .sum();

        double avgConfidence = performanceStats.stream()
                .filter(stat -> stat.get("avgConfidence") != null)
                .mapToDouble(stat -> ((Number) stat.get("avgConfidence")).doubleValue())
                .average().orElse(0.0);

        double avgRiskReward = performanceStats.stream()
                .filter(stat -> stat.get("avgRiskReward") != null)
                .mapToDouble(stat -> ((Number) stat.get("avgRiskReward")).doubleValue())
                .average().orElse(0.0);

        return Map.of(
                "total_signals_analyzed", totalSignals,
                "overall_avg_confidence", avgConfidence,
                "overall_avg_risk_reward", avgRiskReward,
                "signal_quality", avgConfidence > 0.75 ? "HIGH" : avgConfidence > 0.6 ? "MEDIUM" : "LOW",
                "most_frequent_setup", findMostFrequentSetup(performanceStats)
        );
    }

    private String findMostFrequentSetup(List<Map<String, Object>> stats) {
        return stats.stream()
                .max((a, b) -> Integer.compare(
                        ((Number) a.get("totalCount")).intValue(),
                        ((Number) b.get("totalCount")).intValue()
                ))
                .map(stat -> (String) stat.get("setupType"))
                .orElse("UNKNOWN");
    }
}