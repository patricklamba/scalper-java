package com.scalper.controller;

import com.scalper.service.broker.BrokerConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller dédié à l'authentification OAuth2 cTrader - ÉTAPE 3
 * Séparé du MarketDataController pour une architecture propre
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "API d'authentification OAuth2 cTrader")
public class AuthController {

    private final Optional<BrokerConnectionService> brokerConnectionService;

    // ========== OAuth2 Flow Endpoints ==========

    @PostMapping("/ctrader/auth-url")
    @Operation(summary = "Générer URL d'autorisation", description = "Génère l'URL d'autorisation OAuth2 cTrader")
    public ResponseEntity<Map<String, Object>> generateAuthUrl() {
        log.info("Demande génération URL d'autorisation cTrader");

        if (brokerConnectionService.isEmpty()) {
            log.warn("Service broker non disponible - mode simulation activé");
            return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service broker non disponible", "Mode simulation ou configuration manquante");
        }

        try {
            String authUrl = brokerConnectionService.get().getAuthorizationUrl();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("authUrl", authUrl);
            response.put("instruction", "Visitez cette URL pour autoriser l'application");
            response.put("timestamp", LocalDateTime.now());
            response.put("expiresIn", 300); // URL valide 5 minutes

            log.info("URL d'autorisation générée avec succès");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur génération auth URL: {}", e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur génération URL", e.getMessage());
        }
    }

    @GetMapping("/ctrader/callback")
    @Operation(summary = "Callback OAuth2 cTrader",
            description = "Endpoint de callback automatique pour l'autorisation cTrader")
    public ResponseEntity<Map<String, Object>> handleOAuthCallback(
            @Parameter(description = "Code d'autorisation OAuth2", required = true)
            @RequestParam("code") @NotBlank String authorizationCode,

            @Parameter(description = "Paramètre state pour sécurité", required = false)
            @RequestParam(value = "state", required = false) String state,

            @Parameter(description = "Code d'erreur si échec", required = false)
            @RequestParam(value = "error", required = false) String error,

            @Parameter(description = "Description de l'erreur", required = false)
            @RequestParam(value = "error_description", required = false) String errorDescription) {

        log.info("Callback OAuth2 reçu - Code: {}..., State: {}",
                authorizationCode != null ? authorizationCode.substring(0, Math.min(10, authorizationCode.length())) : "null",
                state);

        // Vérifier erreurs OAuth2
        if (error != null) {
            log.error("Erreur OAuth2: {} - {}", error, errorDescription);
            return createErrorResponse(HttpStatus.BAD_REQUEST,
                    "Erreur autorisation OAuth2: " + error, errorDescription);
        }

        // Vérifier présence du service broker
        if (brokerConnectionService.isEmpty()) {
            log.error("Service broker non disponible pour callback");
            return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service non disponible", "Configuration broker manquante");
        }

        // Valider paramètres
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            log.error("Code d'autorisation manquant");
            return createErrorResponse(HttpStatus.BAD_REQUEST,
                    "Code d'autorisation manquant", "Le paramètre 'code' est requis");
        }

        try {
            // Échanger le code contre un access token
            log.info("Échange du code d'autorisation contre access token...");
            boolean success = brokerConnectionService.get().exchangeCodeForToken(authorizationCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("timestamp", LocalDateTime.now());
            response.put("authorizationCode", authorizationCode.substring(0, Math.min(10, authorizationCode.length())) + "...");

            if (success) {
                response.put("message", "Authentification réussie!");
                response.put("status", "authenticated");

                // Ajouter infos sur le token si disponible
                if (brokerConnectionService.get().hasValidToken()) {
                    response.put("tokenExpiration", brokerConnectionService.get().getTokenExpiration());
                    response.put("connectionStatus", "connected");
                }

                log.info("Authentification cTrader réussie");
                return ResponseEntity.ok(response);

            } else {
                response.put("message", "Échec de l'authentification");
                response.put("status", "failed");

                log.error("Échec échange code d'autorisation");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

        } catch (IllegalArgumentException e) {
            log.error("Erreur validation callback: {}", e.getMessage());
            return createErrorResponse(HttpStatus.BAD_REQUEST,
                    "Erreur validation", e.getMessage());

        } catch (Exception e) {
            log.error("Erreur traitement callback: {}", e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur serveur", "Erreur interne lors du traitement du callback");
        }
    }

    @PostMapping("/ctrader/exchange-code")
    @Operation(summary = "Échange manuel de code",
            description = "Permet d'échanger manuellement un code d'autorisation")
    public ResponseEntity<Map<String, Object>> exchangeAuthorizationCode(
            @Parameter(description = "Code d'autorisation OAuth2", required = true)
            @RequestParam("code") @NotBlank String authorizationCode) {

        log.info("Demande échange manuel de code: {}...",
                authorizationCode.substring(0, Math.min(10, authorizationCode.length())));

        if (brokerConnectionService.isEmpty()) {
            return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service non disponible", "Configuration broker manquante");
        }

        try {
            boolean success = brokerConnectionService.get().exchangeCodeForToken(authorizationCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("timestamp", LocalDateTime.now());
            response.put("operation", "manual_token_exchange");

            if (success) {
                response.put("message", "Token obtenu avec succès");
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Échec échange de code");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

        } catch (Exception e) {
            log.error("Erreur échange manuel: {}", e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur échange", e.getMessage());
        }
    }

    // ========== Status et Gestion ==========

    @GetMapping("/ctrader/status")
    @Operation(summary = "Statut authentification",
            description = "Vérifie le statut de l'authentification cTrader")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", LocalDateTime.now());

        if (brokerConnectionService.isEmpty()) {
            status.put("available", false);
            status.put("reason", "Mode simulation ou service non configuré");
            status.put("connected", false);
            status.put("hasValidToken", false);
            return ResponseEntity.ok(status);
        }

        try {
            BrokerConnectionService broker = brokerConnectionService.get();

            status.put("available", true);
            status.put("connected", broker.isConnected());
            status.put("hasValidToken", broker.hasValidToken());
            status.put("lastConnectionCheck", broker.getLastConnectionCheck());

            if (broker.getTokenExpiration() != null) {
                status.put("tokenExpiration", broker.getTokenExpiration());
                status.put("tokenValid", broker.hasValidToken());
            }

            // Vérifier connection en temps réel
            boolean connectionOk = broker.checkConnection();
            status.put("connectionTest", connectionOk ? "passed" : "failed");

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Erreur vérification statut: {}", e.getMessage());
            status.put("available", true);
            status.put("connected", false);
            status.put("error", e.getMessage());
            return ResponseEntity.ok(status);
        }
    }

    @PostMapping("/ctrader/refresh-token")
    @Operation(summary = "Rafraîchir token",
            description = "Rafraîchit l'access token avec le refresh token")
    public ResponseEntity<Map<String, Object>> refreshToken() {
        log.info("Demande rafraîchissement token");

        if (brokerConnectionService.isEmpty()) {
            return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service non disponible", "Configuration broker manquante");
        }

        try {
            boolean success = brokerConnectionService.get().refreshAccessToken();

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("timestamp", LocalDateTime.now());
            response.put("operation", "token_refresh");

            if (success) {
                response.put("message", "Token rafraîchi avec succès");
                response.put("newExpiration", brokerConnectionService.get().getTokenExpiration());
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Échec rafraîchissement token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

        } catch (Exception e) {
            log.error("Erreur rafraîchissement token: {}", e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur rafraîchissement", e.getMessage());
        }
    }

    @DeleteMapping("/ctrader/disconnect")
    @Operation(summary = "Déconnexion",
            description = "Déconnecte et invalide les tokens cTrader")
    public ResponseEntity<Map<String, Object>> disconnect() {
        log.info("Demande déconnexion cTrader");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("operation", "disconnect");

        if (brokerConnectionService.isEmpty()) {
            response.put("success", false);
            response.put("message", "Service non disponible");
            return ResponseEntity.ok(response);
        }

        try {
            // Pour l'instant, pas de méthode disconnect dans le service
            // On pourrait l'ajouter plus tard
            response.put("success", true);
            response.put("message", "Déconnexion demandée - redémarrez l'application pour effet complet");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur déconnexion: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ========== Méthodes Utilitaires ==========

    private ResponseEntity<Map<String, Object>> createErrorResponse(
            HttpStatus status, String message, String details) {

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);
        errorResponse.put("details", details);
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", status.value());

        return ResponseEntity.status(status).body(errorResponse);
    }
}