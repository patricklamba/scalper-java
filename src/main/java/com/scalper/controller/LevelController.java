package com.scalper.controller;

import com.scalper.model.entity.IntradayLevel;
import com.scalper.repository.IntradayLevelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller REST pour la gestion des niveaux intraday multi-sessions
 */
@Slf4j
@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
@Validated
@Tag(name = "Levels", description = "API de gestion des niveaux intraday (Asia H/L, London H/L, VWAP, etc.)")
public class LevelController {

    private final IntradayLevelRepository levelRepository;

    /**
     * Récupère tous les niveaux actifs pour un symbole
     */
    @GetMapping("/active/{symbol}")
    @Operation(
            summary = "Niveaux actifs",
            description = "Récupère tous les niveaux intraday actifs pour un symbole donné"
    )
    @ApiResponse(responseCode = "200", description = "Niveaux actifs récupérés avec succès")
    public ResponseEntity<Map<String, Object>> getActiveLevels(
            @Parameter(description = "Symbole de trading (ex: EURUSD)", required = true)
            @PathVariable @NotBlank String symbol) {

        log.info("Récupération des niveaux actifs pour {}", symbol);

        List<IntradayLevel> activeLevels = levelRepository.findActiveLevels(symbol);

        // Organisation par catégorie
        Map<String, Object> response = organizeLevelsByCategory(activeLevels, symbol);

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les niveaux intraday pour une date spécifique
     */
    @GetMapping("/intraday/{symbol}")
    @Operation(
            summary = "Niveaux intraday par date",
            description = "Récupère les niveaux Asia H/L, London H/L, NY H/L pour une date donnée"
    )
    public ResponseEntity<Map<String, Object>> getIntradayLevels(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Date des niveaux (format: YYYY-MM-DD)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        log.info("Récupération des niveaux intraday pour {} le {}", symbol, targetDate);

        // Récupération des niveaux par session
        Map<String, Object> sessionLevels = new HashMap<>();

        // Niveaux Asia
        sessionLevels.put("asia_levels", getLevelsBySessionType(symbol, targetDate, "ASIA"));

        // Niveaux London
        sessionLevels.put("london_levels", getLevelsBySessionType(symbol, targetDate, "LONDON"));

        // Niveaux New York
        sessionLevels.put("newyork_levels", getLevelsBySessionType(symbol, targetDate, "NEWYORK"));

        // VWAP et pivots
        sessionLevels.put("vwap_levels", getVWAPLevels(symbol, targetDate));
        sessionLevels.put("pivot_levels", getPivotLevels(symbol, targetDate));

        // Récupération des niveaux actifs pour le total
        List<IntradayLevel> activeLevels = levelRepository.findActiveLevels(symbol);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("date", targetDate);
        response.put("session_levels", sessionLevels);
        response.put("total_active_levels", activeLevels.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les niveaux cassés récemment
     */
    @GetMapping("/broken/{symbol}")
    @Operation(
            summary = "Niveaux cassés récemment",
            description = "Récupère les niveaux qui ont été cassés récemment"
    )
    public ResponseEntity<List<IntradayLevel>> getRecentlyBrokenLevels(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre d'heures à regarder en arrière")
            @RequestParam(defaultValue = "24") int hoursBack) {

        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);

        log.info("Récupération des niveaux cassés pour {} depuis {}", symbol, since);

        List<IntradayLevel> brokenLevels = levelRepository.findBrokenLevelsSince(symbol, since);

        return ResponseEntity.ok(brokenLevels);
    }

    /**
     * Trouve le support et résistance les plus proches
     */
    @GetMapping("/nearest/{symbol}")
    @Operation(
            summary = "Support/Résistance les plus proches",
            description = "Trouve le support en-dessous et la résistance au-dessus d'un prix"
    )
    public ResponseEntity<Map<String, Object>> getNearestSupportResistance(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Prix de référence", required = true)
            @RequestParam @NotNull @DecimalMin("0.0") BigDecimal price) {

        log.info("Recherche S/R les plus proches de {} pour {}", price, symbol);

        var nearestSupport = levelRepository.findNearestSupport(symbol, price);
        var nearestResistance = levelRepository.findNearestResistance(symbol, price);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("reference_price", price);
        response.put("nearest_support", nearestSupport.orElse(null));
        response.put("nearest_resistance", nearestResistance.orElse(null));
        response.put("support_distance_pips", nearestSupport.map(s -> calculatePipsDistance(price, s.getPrice())).orElse(null));
        response.put("resistance_distance_pips", nearestResistance.map(r -> calculatePipsDistance(r.getPrice(), price)).orElse(null));

        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les niveaux par type spécifique
     */
    @GetMapping("/type/{symbol}")
    @Operation(
            summary = "Niveaux par type",
            description = "Récupère les niveaux d'un type spécifique (ASIA_HIGH, LONDON_LOW, etc.)"
    )
    public ResponseEntity<List<IntradayLevel>> getLevelsByType(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Type de niveau", required = true)
            @RequestParam @NotNull IntradayLevel.LevelType levelType) {

        log.info("Récupération des niveaux {} pour {}", levelType, symbol);

        List<IntradayLevel> levels = levelRepository.findBySymbolAndLevelTypeAndStatus(
                symbol, levelType, IntradayLevel.LevelStatus.ACTIVE);

        return ResponseEntity.ok(levels);
    }

    /**
     * Récupère les niveaux les plus testés
     */
    @GetMapping("/tested/{symbol}")
    @Operation(
            summary = "Niveaux les plus testés",
            description = "Récupère les niveaux qui ont été testés le plus souvent"
    )
    public ResponseEntity<List<IntradayLevel>> getMostTestedLevels(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nombre minimum de tests")
            @RequestParam(defaultValue = "3") int minTests) {

        log.info("Récupération des niveaux les plus testés pour {} (min {} tests)", symbol, minTests);

        List<IntradayLevel> levels = levelRepository.findMostTestedLevels(symbol, minTests);

        return ResponseEntity.ok(levels);
    }

    /**
     * Analyse de la qualité des niveaux
     */
    @GetMapping("/analysis/{symbol}")
    @Operation(
            summary = "Analyse qualité des niveaux",
            description = "Analyse la qualité et la fiabilité des niveaux actifs"
    )
    public ResponseEntity<Map<String, Object>> getLevelsAnalysis(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol) {

        log.info("Analyse des niveaux pour {}", symbol);

        List<IntradayLevel> activeLevels = levelRepository.findActiveLevels(symbol);

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("symbol", symbol);
        analysis.put("total_active_levels", activeLevels.size());
        analysis.put("high_quality_levels", countHighQualityLevels(activeLevels));
        analysis.put("average_importance", calculateAverageImportance(activeLevels));
        analysis.put("most_reliable_level", findMostReliableLevel(activeLevels));
        analysis.put("retest_candidates", findRetestCandidates(activeLevels));

        return ResponseEntity.ok(analysis);
    }

    // ===========================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // ===========================================

    private Map<String, Object> organizeLevelsByCategory(List<IntradayLevel> levels, String symbol) {
        Map<String, Object> response = new HashMap<>();

        response.put("symbol", symbol);
        response.put("timestamp", LocalDateTime.now());
        response.put("total_active_levels", levels.size());

        // Organisation par type de session
        Map<String, List<IntradayLevel>> sessionGroups = levels.stream()
                .collect(Collectors.groupingBy(level -> {
                    String type = level.getLevelType().toString();
                    if (type.startsWith("ASIA")) return "asia";
                    if (type.startsWith("LONDON")) return "london";
                    if (type.startsWith("NY")) return "newyork";
                    if (type.startsWith("VWAP")) return "vwap";
                    return "other";
                }));

        response.put("session_levels", sessionGroups);

        // Statistiques globales
        Map<String, Object> stats = new HashMap<>();
        stats.put("high_importance_count", levels.stream()
                .mapToLong(l -> l.getImportanceScore().compareTo(BigDecimal.valueOf(0.8)) >= 0 ? 1 : 0)
                .sum());
        stats.put("heavily_tested_count", levels.stream()
                .mapToLong(l -> l.getTouchCount() >= 3 ? 1 : 0)
                .sum());
        stats.put("average_importance", calculateAverageImportance(levels));

        response.put("statistics", stats);

        return response;
    }

    private List<IntradayLevel> getLevelsBySessionType(String symbol, LocalDate date, String sessionType) {
        return levelRepository.findBySymbolAndLevelTypeStartingWithAndStatus(
                symbol, sessionType, IntradayLevel.LevelStatus.ACTIVE);
    }

    private List<IntradayLevel> getVWAPLevels(String symbol, LocalDate date) {
        return levelRepository.findBySymbolAndLevelTypeStartingWithAndStatus(
                symbol, "VWAP", IntradayLevel.LevelStatus.ACTIVE);
    }

    private List<IntradayLevel> getPivotLevels(String symbol, LocalDate date) {
        return levelRepository.findBySymbolAndLevelTypeAndStatus(
                symbol, IntradayLevel.LevelType.PIVOT_DAILY, IntradayLevel.LevelStatus.ACTIVE);
    }

    private Integer calculatePipsDistance(BigDecimal price1, BigDecimal price2) {
        if (price1 == null || price2 == null) return null;
        return price1.subtract(price2)
                .abs()
                .multiply(BigDecimal.valueOf(10000))
                .intValue();
    }

    private long countHighQualityLevels(List<IntradayLevel> levels) {
        return levels.stream()
                .filter(level -> level.getImportanceScore().compareTo(BigDecimal.valueOf(0.8)) >= 0)
                .count();
    }

    private BigDecimal calculateAverageImportance(List<IntradayLevel> levels) {
        if (levels.isEmpty()) return BigDecimal.ZERO;

        return levels.stream()
                .map(IntradayLevel::getImportanceScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(levels.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    private IntradayLevel findMostReliableLevel(List<IntradayLevel> levels) {
        return levels.stream()
                .max((l1, l2) -> l1.calculateLevelStrength().compareTo(l2.calculateLevelStrength()))
                .orElse(null);
    }

    private List<Map<String, Object>> findRetestCandidates(List<IntradayLevel> levels) {
        return levels.stream()
                .filter(level -> level.getTouchCount() >= 2)
                .filter(level -> level.getImportanceScore().compareTo(BigDecimal.valueOf(0.6)) >= 0)
                .map(level -> {
                    Map<String, Object> candidate = new HashMap<>();
                    candidate.put("level", level);
                    candidate.put("retest_probability", calculateRetestProbability(level));
                    candidate.put("risk_reward_estimate", calculateRetestRiskReward(level));
                    return candidate;
                })
                .collect(Collectors.toList());
    }

    private BigDecimal calculateRetestProbability(IntradayLevel level) {
        // Probabilité basée sur importance + nombre de tests
        BigDecimal base = level.getImportanceScore();
        BigDecimal touchBonus = BigDecimal.valueOf(level.getTouchCount() * 0.1);
        return base.add(touchBonus).min(BigDecimal.ONE);
    }

    private BigDecimal calculateRetestRiskReward(IntradayLevel level) {
        // R/R estimé basé sur la force du niveau
        if (level.getImportanceScore().compareTo(BigDecimal.valueOf(0.8)) >= 0) {
            return BigDecimal.valueOf(2.5);
        } else if (level.getImportanceScore().compareTo(BigDecimal.valueOf(0.6)) >= 0) {
            return BigDecimal.valueOf(2.0);
        }
        return BigDecimal.valueOf(1.5);
    }
}