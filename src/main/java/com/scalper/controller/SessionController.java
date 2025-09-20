package com.scalper.controller;

import com.scalper.model.entity.TradingSession;
import com.scalper.repository.TradingSessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller REST pour la gestion des sessions de trading multi-sessions
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Sessions", description = "API de gestion des sessions de trading (Asia, London, NY)")
public class SessionController {

    private final TradingSessionRepository sessionRepository;

    /**
     * Récupère l'aperçu complet des sessions pour un symbole et une date
     */
    @GetMapping("/overview/{symbol}")
    @Operation(
            summary = "Aperçu des sessions",
            description = "Récupère l'aperçu complet des sessions Asia/London/NY pour un symbole et une date donnée"
    )
    @ApiResponse(responseCode = "200", description = "Aperçu des sessions récupéré avec succès")
    public ResponseEntity<Map<String, Object>> getSessionsOverview(
            @Parameter(description = "Symbole de trading (ex: EURUSD)", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Date des sessions (format: YYYY-MM-DD)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        log.info("Récupération aperçu sessions pour {} le {}", symbol, targetDate);

        List<TradingSession> sessions = sessionRepository.findAllSessionsBySymbolAndDate(symbol, targetDate);

        // Construction de la réponse structurée
        Map<String, Object> overview = buildSessionsOverview(sessions, symbol, targetDate);

        return ResponseEntity.ok(overview);
    }

    /**
     * Récupère une session spécifique
     */
    @GetMapping("/{symbol}/{sessionName}")
    @Operation(
            summary = "Session spécifique",
            description = "Récupère les détails d'une session spécifique (ASIA, LONDON, NEWYORK)"
    )
    public ResponseEntity<TradingSession> getSpecificSession(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Nom de la session", required = true)
            @PathVariable @NotNull TradingSession.SessionName sessionName,

            @Parameter(description = "Date de la session")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        Optional<TradingSession> session = sessionRepository
                .findBySymbolAndSessionNameAndSessionDate(symbol, sessionName, targetDate);

        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Récupère la session active actuelle
     */
    @GetMapping("/active/{symbol}")
    @Operation(
            summary = "Session active",
            description = "Récupère la session de trading actuellement active pour un symbole"
    )
    public ResponseEntity<Map<String, Object>> getActiveSession(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol) {

        LocalDateTime now = LocalDateTime.now();

        Optional<TradingSession> activeSession = sessionRepository
                .findActiveSessionForSymbol(symbol, now);

        if (activeSession.isPresent()) {
            TradingSession session = activeSession.get();

            Map<String, Object> response = Map.of(
                    "active_session", session,
                    "session_progress", calculateSessionProgress(session, now),
                    "time_remaining_minutes", calculateTimeRemaining(session, now),
                    "next_session_start", calculateNextSessionStart(now)
            );

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(Map.of(
                "active_session", null,
                "message", "Aucune session active actuellement",
                "next_session_start", calculateNextSessionStart(now)
        ));
    }

    /**
     * Récupère les sessions avec breakouts
     */
    @GetMapping("/breakouts/{symbol}")
    @Operation(
            summary = "Sessions avec breakouts",
            description = "Récupère les sessions ayant eu des breakouts sur une période donnée"
    )
    public ResponseEntity<List<TradingSession>> getSessionsWithBreakouts(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Date de début de recherche")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {

        LocalDate searchFrom = fromDate != null ? fromDate : LocalDate.now().minusDays(30);

        List<TradingSession> sessions = sessionRepository
                .findSessionsWithBreakouts(symbol, searchFrom);

        return ResponseEntity.ok(sessions);
    }

    /**
     * Récupère les statistiques de sessions
     */
    @GetMapping("/statistics/{symbol}")
    @Operation(
            summary = "Statistiques des sessions",
            description = "Récupère les statistiques détaillées des sessions sur une période"
    )
    public ResponseEntity<List<Map<String, Object>>> getSessionStatistics(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Date de début d'analyse")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {

        LocalDate analysisFrom = fromDate != null ? fromDate : LocalDate.now().minusDays(30);

        List<Map<String, Object>> statistics = sessionRepository
                .getSessionStatistics(symbol, analysisFrom);

        return ResponseEntity.ok(statistics);
    }

    /**
     * Récupère les sessions par qualité
     */
    @GetMapping("/quality/{symbol}")
    @Operation(
            summary = "Sessions par qualité",
            description = "Récupère les sessions filtrées par niveau de qualité"
    )
    public ResponseEntity<List<TradingSession>> getSessionsByQuality(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Qualité de session", required = true)
            @RequestParam @NotNull TradingSession.SessionQuality quality,

            @Parameter(description = "Date de début de recherche")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {

        LocalDate searchFrom = fromDate != null ? fromDate : LocalDate.now().minusDays(7);

        List<TradingSession> sessions = sessionRepository
                .findSessionsByQuality(symbol, quality, searchFrom);

        return ResponseEntity.ok(sessions);
    }

    /**
     * Récupère les sessions dans une fourchette de range
     */
    @GetMapping("/range/{symbol}")
    @Operation(
            summary = "Sessions par taille de range",
            description = "Récupère les sessions ayant un range dans une fourchette spécifiée"
    )
    public ResponseEntity<List<TradingSession>> getSessionsByRangeSize(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Range minimum en pips", required = true)
            @RequestParam @NotNull Integer minPips,

            @Parameter(description = "Range maximum en pips", required = true)
            @RequestParam @NotNull Integer maxPips,

            @Parameter(description = "Date de début de recherche")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {

        LocalDate searchFrom = fromDate != null ? fromDate : LocalDate.now().minusDays(14);

        List<TradingSession> sessions = sessionRepository
                .findSessionsByRangeSize(symbol, minPips, maxPips, searchFrom);

        return ResponseEntity.ok(sessions);
    }

    /**
     * Récupère les statistiques de breakouts par session
     */
    @GetMapping("/breakout-stats/{symbol}")
    @Operation(
            summary = "Statistiques de breakouts",
            description = "Analyse des breakouts par type de session et direction"
    )
    public ResponseEntity<List<Map<String, Object>>> getBreakoutStatistics(
            @Parameter(description = "Symbole de trading", required = true)
            @PathVariable @NotBlank String symbol,

            @Parameter(description = "Date de début d'analyse")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {

        LocalDate analysisFrom = fromDate != null ? fromDate : LocalDate.now().minusDays(60);

        List<Map<String, Object>> stats = sessionRepository
                .getBreakoutStatistics(symbol, analysisFrom);

        return ResponseEntity.ok(stats);
    }

    /**
     * Helper methods pour la construction des réponses
     */
    private Map<String, Object> buildSessionsOverview(List<TradingSession> sessions, String symbol, LocalDate date) {
        Map<String, Object> overview = new HashMap<>();

        overview.put("symbol", symbol);
        overview.put("date", date);
        overview.put("current_session", getCurrentSessionName());
        overview.put("session_progress", getCurrentSessionProgress());

        // Organisation des sessions par type
        Map<String, Object> sessionData = new HashMap<>();

        for (TradingSession session : sessions) {
            String sessionKey = session.getSessionName().name().toLowerCase();

            // Utilisation de HashMap au lieu de Map.of() pour éviter la limitation de 10 paires
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("status", determineSessionStatus(session));
            sessionInfo.put("open", session.getSessionOpen());
            sessionInfo.put("high", session.getSessionHigh());
            sessionInfo.put("low", session.getSessionLow());
            sessionInfo.put("close", session.getSessionClose());
            sessionInfo.put("range_pips", session.getRangeSizePips());
            sessionInfo.put("volatility", session.getVolatilityScore());
            sessionInfo.put("quality", session.getSessionQuality());
            sessionInfo.put("vwap", session.getVwapPrice());
            sessionInfo.put("breakout_occurred", session.getBreakoutOccurred());
            sessionInfo.put("breakout_direction", session.getBreakoutDirection());

            sessionData.put(sessionKey, sessionInfo);
        }

        overview.put("daily_sessions", sessionData);
        overview.put("next_session_start", calculateNextSessionStart(LocalDateTime.now()));

        return overview;
    }

    private String getCurrentSessionName() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        if (hour >= 0 && hour < 6) return "ASIA";
        if (hour >= 7 && hour < 12) return "LONDON";
        if (hour >= 12 && hour < 17) return "NEWYORK";
        return "AFTER_HOURS";
    }

    private double getCurrentSessionProgress() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();

        return switch (getCurrentSessionName()) {
            case "ASIA" -> (hour * 60 + minute) / 360.0; // 6 heures = 360 minutes
            case "LONDON" -> ((hour - 7) * 60 + minute) / 240.0; // 4 heures = 240 minutes
            case "NEWYORK" -> ((hour - 12) * 60 + minute) / 240.0; // 4 heures = 240 minutes
            default -> 0.0;
        };
    }

    private String determineSessionStatus(TradingSession session) {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(session.getSessionStart())) {
            return "PENDING";
        } else if (now.isAfter(session.getSessionEnd())) {
            return "COMPLETED";
        } else {
            return "ACTIVE";
        }
    }

    private double calculateSessionProgress(TradingSession session, LocalDateTime now) {
        if (now.isBefore(session.getSessionStart())) return 0.0;
        if (now.isAfter(session.getSessionEnd())) return 1.0;

        long totalMinutes = java.time.Duration.between(session.getSessionStart(), session.getSessionEnd()).toMinutes();
        long elapsedMinutes = java.time.Duration.between(session.getSessionStart(), now).toMinutes();

        return (double) elapsedMinutes / totalMinutes;
    }

    private long calculateTimeRemaining(TradingSession session, LocalDateTime now) {
        if (now.isAfter(session.getSessionEnd())) return 0;
        return java.time.Duration.between(now, session.getSessionEnd()).toMinutes();
    }

    private LocalDateTime calculateNextSessionStart(LocalDateTime now) {
        int hour = now.getHour();
        LocalDateTime nextDay = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        if (hour < 7) {
            return now.withHour(7).withMinute(0).withSecond(0).withNano(0);
        } else if (hour < 12) {
            return now.withHour(12).withMinute(0).withSecond(0).withNano(0);
        } else if (hour < 17) {
            return nextDay; // Prochaine session Asia
        } else {
            return nextDay; // Prochaine session Asia
        }
    }
}