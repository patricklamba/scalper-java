package com.scalper.controller;

import com.scalper.model.entity.MarketData;
import com.scalper.repository.MarketDataRepository;
import com.scalper.service.MarketDataService;
import com.scalper.service.broker.BrokerConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Controller REST pour données de marché - ÉTAPE 3 CORRIGÉ
 * Endpoints pour consultation des prix, niveaux intraday et breakouts
 * Callback OAuth2 déplacé vers AuthController dédié
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Market Data", description = "API pour données de marché temps réel et historiques")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final Optional<BrokerConnectionService> brokerConnectionService;
    private final MarketDataRepository marketDataRepository;

    // ========== Endpoints Prix Temps Réel ==========

    @GetMapping("/current/{symbol}")
    @Operation(summary = "Prix actuel", description = "Récupère le prix actuel pour un symbole")
    public ResponseEntity<MarketData> getCurrentPrice(
            @Parameter(description = "Symbole (EURUSD ou XAUUSD)", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol) {

        try {
            Optional<MarketData> currentPrice = marketDataService.getCurrentPrice(symbol);

            if (currentPrice.isPresent()) {
                return ResponseEntity.ok(currentPrice.get());
            } else {
                log.debug("Aucun prix actuel disponible pour {}", symbol);
                return ResponseEntity.noContent().build();
            }

        } catch (IllegalArgumentException e) {
            log.warn("Symbole invalide: {}", symbol);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur récupération prix actuel {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/candles/{symbol}/{timeframe}")
    @Operation(summary = "Bougies historiques", description = "Récupère les dernières bougies pour un symbole et timeframe")
    public ResponseEntity<List<MarketData>> getLatestCandles(
            @Parameter(description = "Symbole", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol,

            @Parameter(description = "Timeframe", example = "M5")
            @PathVariable @Pattern(regexp = "^(M1|M5|M30)$") String timeframe,

            @Parameter(description = "Nombre de bougies (1-1000)", example = "100")
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {

        try {
            List<MarketData> candles = marketDataService.getLatestCandles(symbol, timeframe, limit);

            if (candles.isEmpty()) {
                log.debug("Aucune bougie disponible pour {} {} (limit: {})", symbol, timeframe, limit);
                return ResponseEntity.noContent().build();
            }

            log.debug("Retour de {} bougies {} {} (demandé: {})",
                    candles.size(), symbol, timeframe, limit);
            return ResponseEntity.ok(candles);

        } catch (IllegalArgumentException e) {
            log.warn("Paramètres invalides - Symbol: {}, Timeframe: {}, Limit: {}",
                    symbol, timeframe, limit);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur récupération bougies {} {}: {}", symbol, timeframe, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== Endpoints Sessions Multi-Sessions ==========

    @GetMapping("/session/{symbol}/{sessionName}")
    @Operation(summary = "Données session", description = "Récupère données pour une session spécifique")
    public ResponseEntity<List<MarketData>> getSessionData(
            @Parameter(description = "Symbole", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol,

            @Parameter(description = "Session", example = "LONDON")
            @PathVariable @Pattern(regexp = "^(ASIA|LONDON|NEWYORK|OVERLAP)$") String sessionName,

            @Parameter(description = "Timeframe", example = "M5")
            @RequestParam(defaultValue = "M5") @Pattern(regexp = "^(M1|M5|M30)$") String timeframe) {

        try {
            List<MarketData> sessionData = marketDataService.getSessionData(symbol, sessionName, timeframe);

            if (sessionData.isEmpty()) {
                log.debug("Aucune donnée session disponible pour {} {} {}",
                        symbol, sessionName, timeframe);
                return ResponseEntity.noContent().build();
            }

            log.debug("Retour de {} points de données pour session {} {} {}",
                    sessionData.size(), sessionName, symbol, timeframe);
            return ResponseEntity.ok(sessionData);

        } catch (IllegalArgumentException e) {
            log.warn("Paramètres session invalides - Symbol: {}, Session: {}, Timeframe: {}",
                    symbol, sessionName, timeframe);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur récupération session {} {}: {}", symbol, sessionName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/levels/{symbol}/{sessionName}")
    @Operation(summary = "Niveaux session", description = "Calcule niveaux intraday (High/Low/VWAP) pour une session")
    public ResponseEntity<Map<String, BigDecimal>> getSessionLevels(
            @Parameter(description = "Symbole", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol,

            @Parameter(description = "Session", example = "LONDON")
            @PathVariable @Pattern(regexp = "^(ASIA|LONDON|NEWYORK)$") String sessionName) {

        try {
            Map<String, BigDecimal> levels = marketDataService.calculateSessionLevels(symbol, sessionName);

            if (levels.isEmpty()) {
                log.debug("Aucun niveau calculé pour {} {}", symbol, sessionName);
                return ResponseEntity.noContent().build();
            }

            log.debug("Niveaux calculés pour {} {}: {}", symbol, sessionName, levels.keySet());
            return ResponseEntity.ok(levels);

        } catch (IllegalArgumentException e) {
            log.warn("Paramètres niveaux invalides - Symbol: {}, Session: {}", symbol, sessionName);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur calcul niveaux {} {}: {}", symbol, sessionName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== Endpoints Pattern Detection ==========

    @GetMapping("/breakouts/{symbol}")
    @Operation(summary = "Breakouts récents", description = "Détecte bougies de breakout récentes")
    public ResponseEntity<List<MarketData>> getRecentBreakouts(
            @Parameter(description = "Symbole", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol,

            @Parameter(description = "Timeframe", example = "M5")
            @RequestParam(defaultValue = "M5") @Pattern(regexp = "^(M1|M5|M30)$") String timeframe,

            @Parameter(description = "Nombre d'heures à analyser", example = "24")
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {

        try {
            List<MarketData> breakouts = marketDataService.detectRecentBreakouts(symbol, timeframe, hours);

            log.debug("Détecté {} breakouts pour {} {} (dernières {}h)",
                    breakouts.size(), symbol, timeframe, hours);
            return ResponseEntity.ok(breakouts);

        } catch (IllegalArgumentException e) {
            log.warn("Paramètres breakout invalides - Symbol: {}, Timeframe: {}, Hours: {}",
                    symbol, timeframe, hours);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur détection breakouts {} {}: {}", symbol, timeframe, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/pivots/{symbol}")
    @Operation(summary = "Niveaux pivot", description = "Trouve niveaux de support/résistance potentiels")
    public ResponseEntity<List<MarketData>> getPivotLevels(
            @Parameter(description = "Symbole", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol,

            @Parameter(description = "Nombre d'heures à analyser", example = "72")
            @RequestParam(defaultValue = "72") @Min(1) @Max(168) int hours) {

        try {
            List<MarketData> pivots = marketDataService.findPivotLevels(symbol, hours);

            log.debug("Trouvé {} niveaux pivot pour {} (dernières {}h)",
                    pivots.size(), symbol, hours);
            return ResponseEntity.ok(pivots);

        } catch (IllegalArgumentException e) {
            log.warn("Paramètres pivot invalides - Symbol: {}, Hours: {}", symbol, hours);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur recherche pivots {} {}: {}", symbol, hours, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== Endpoints Gestion et Contrôle ==========

    @PostMapping("/update/{symbol}")
    @Operation(summary = "Force mise à jour", description = "Force la mise à jour des données")
    public ResponseEntity<Map<String, Object>> forceUpdate(
            @Parameter(description = "Symbole", example = "EURUSD")
            @PathVariable @Pattern(regexp = "^(EURUSD|XAUUSD)$") String symbol) {

        try {
            log.info("Force update demandé pour {}", symbol);

            // CORRIGÉ: Appel correct avec gestion timeout
            CompletableFuture<Boolean> updateFuture = marketDataService.forceDataUpdate(symbol);

            // Attendre jusqu'à 5 secondes (plus court pour le simulateur)
            Boolean success = updateFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("success", success);
            response.put("timestamp", LocalDateTime.now());
            response.put("mode", determineMode());

            log.info("Force update {} terminé: {}", symbol, success);
            return ResponseEntity.ok(response);

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Force update {} timeout", symbol);
            Map<String, Object> response = createErrorResponse("timeout",
                    "Opération trop longue", symbol);
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Force update symbole invalide: {}", symbol);
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("Erreur force update {}: {}", symbol, e.getMessage());
            Map<String, Object> response = createErrorResponse("error",
                    e.getMessage(), symbol);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques", description = "Statistiques du service MarketData")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = marketDataService.getServiceStats();

            // Ajouter infos broker si disponible
            stats.put("brokerAvailable", brokerConnectionService.isPresent());
            if (brokerConnectionService.isPresent()) {
                BrokerConnectionService broker = brokerConnectionService.get();
                Map<String, Object> brokerStats = new HashMap<>();
                brokerStats.put("connected", broker.isConnected());
                brokerStats.put("hasValidToken", broker.hasValidToken());
                brokerStats.put("lastCheck", broker.getLastConnectionCheck());
                stats.put("broker", brokerStats);
            }

            log.debug("Statistiques demandées: {} entrées", stats.size());
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Erreur récupération statistiques: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== Endpoints Broker Connection - CORRIGÉS ==========

    @GetMapping("/broker/status")
    @Operation(summary = "État broker", description = "Vérifier l'état de la connexion broker")
    public ResponseEntity<Map<String, Object>> getBrokerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", LocalDateTime.now());

        if (brokerConnectionService.isPresent()) {
            try {
                BrokerConnectionService broker = brokerConnectionService.get();

                status.put("available", true);
                status.put("connected", broker.isConnected());
                status.put("hasValidToken", broker.hasValidToken());
                status.put("lastConnectionCheck", broker.getLastConnectionCheck());

                if (broker.getTokenExpiration() != null) {
                    status.put("tokenExpiration", broker.getTokenExpiration());
                }

                // Test connexion en temps réel
                boolean connectionTest = broker.checkConnection();
                status.put("connectionTest", connectionTest ? "passed" : "failed");

            } catch (Exception e) {
                log.error("Erreur vérification statut broker: {}", e.getMessage());
                status.put("available", true);
                status.put("connected", false);
                status.put("error", e.getMessage());
            }

        } else {
            status.put("available", false);
            status.put("connected", false);
            status.put("reason", "Mode simulation activé ou service non configuré");
        }

        return ResponseEntity.ok(status);
    }

    @PostMapping("/broker/auth-url")
    @Operation(summary = "URL d'autorisation", description = "Génère URL d'autorisation OAuth2 cTrader")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        if (brokerConnectionService.isEmpty()) {
            log.warn("Service broker non disponible pour auth-url");
            Map<String, String> response = new HashMap<>();
            response.put("error", "Service broker non disponible");
            response.put("reason", "Mode simulation ou configuration manquante");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try {
            String authUrl = brokerConnectionService.get().getAuthorizationUrl();
            Map<String, String> response = new HashMap<>();
            response.put("authUrl", authUrl);
            response.put("instruction", "Visitez cette URL pour autoriser l'application");

            log.info("URL d'autorisation générée via market endpoint");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur génération auth URL: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Erreur génération URL");
            response.put("details", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ========== Endpoints Utilitaires ==========

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Vérification santé du service MarketData")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "MarketDataService");

        try {
            // Vérifier que le service répond
            Map<String, Object> stats = marketDataService.getServiceStats();
            boolean isHealthy = (Boolean) stats.get("isRunning");

            health.put("status", isHealthy ? "UP" : "DOWN");
            health.put("details", stats);

            // Vérifier broker si disponible
            if (brokerConnectionService.isPresent()) {
                boolean brokerHealthy = brokerConnectionService.get().isConnected();
                health.put("brokerStatus", brokerHealthy ? "UP" : "DOWN");
            } else {
                health.put("brokerStatus", "SIMULATION");
            }

            HttpStatus status = isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(health);

        } catch (Exception e) {
            log.error("Erreur health check: {}", e.getMessage());
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    @GetMapping("/symbols")
    @Operation(summary = "Symboles supportés", description = "Liste des symboles supportés par l'API")
    public ResponseEntity<Map<String, Object>> getSupportedSymbols() {
        Map<String, Object> symbols = new HashMap<>();
        symbols.put("symbols", Arrays.asList("EURUSD", "XAUUSD"));
        symbols.put("timeframes", Arrays.asList("M1", "M5", "M30"));
        symbols.put("sessions", Arrays.asList("ASIA", "LONDON", "NEWYORK", "OVERLAP"));
        symbols.put("mode", determineMode());
        symbols.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(symbols);
    }

    @GetMapping("/version")
    @Operation(summary = "Version API", description = "Informations sur la version de l'API MarketData")
    public ResponseEntity<Map<String, Object>> getVersion() {
        Map<String, Object> version = new HashMap<>();
        version.put("api", "MarketData API");
        version.put("version", "3.0.0");
        version.put("stage", "ÉTAPE 3 - Intégration Broker");
        version.put("mode", determineMode());
        version.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(version);
    }

    // ========== Méthodes Utilitaires Privées ==========

    private Map<String, Object> createErrorResponse(String type, String message, String symbol) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", type);
        response.put("message", message);
        response.put("symbol", symbol);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    private String determineMode() {
        return brokerConnectionService.isPresent() ? "API_REELLE" : "SIMULATEUR";
    }
}