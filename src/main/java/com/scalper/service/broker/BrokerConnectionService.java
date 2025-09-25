package com.scalper.service.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;

/**
 * Service de connexion cTrader API - ÉTAPE 3 Phase API Réelle
 * URLs corrigées selon documentation officielle cTrader
 * Gestion OAuth2, récupération données temps réel, historiques
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scalper.broker.simulation-mode", havingValue = "false")
public class BrokerConnectionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Configuration OAuth2 cTrader
    @Value("${scalper.broker.ctrader.client-id}")
    private String clientId;

    @Value("${scalper.broker.ctrader.client-secret}")
    private String clientSecret;

    @Value("${scalper.broker.ctrader.redirect-uri:http://localhost:8080/api/auth/ctrader/callback}")
    private String redirectUri;

    @Value("${scalper.broker.ctrader.base-url:https://openapi.ctrader.com}")
    private String ctraderBaseUrl;

    @Value("${scalper.broker.ctrader.account-id:}")
    private String accountId;

    @Value("${scalper.broker.ctrader.connection-timeout:30000}")
    private int connectionTimeout;

    @Value("${scalper.broker.ctrader.read-timeout:60000}")
    private int readTimeout;

    @Value("${scalper.broker.ctrader.max-retries:3}")
    private int maxRetries;

    // État de connexion
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile LocalDateTime tokenExpiration;
    private volatile LocalDateTime lastConnectionCheck;
    private volatile boolean isConnected = false;

    // URLs API cTrader CORRIGÉES selon documentation officielle
    private static final String OAUTH_BASE_URL = "https://id.ctrader.com";
    private static final String OAUTH_AUTHORIZE_PATH = "/my/settings/openapi/grantingaccess/";
    private static final String OAUTH_TOKEN_PATH = "/apps/token";

    // API Endpoints - Version REST simplifiée pour market data
    private static final String API_VERSION = "v1";
    private static final String API_ACCOUNTS_PATH = "/" + API_VERSION + "/accounts";

    // Note: Les endpoints ci-dessous sont des approximations
    // Car cTrader privilégie ProtoOA pour la plupart des opérations
    private static final String API_SYMBOLS_PATH = "/" + API_VERSION + "/symbols";
    private static final String API_HISTORICAL_PATH = "/" + API_VERSION + "/historical";
    private static final String API_SPOT_PATH = "/" + API_VERSION + "/spot";

    @PostConstruct
    public void initialize() {
        log.info("🔗 Initialisation BrokerConnectionService cTrader");
        log.info("Client ID: {} | Base URL: {}", clientId, ctraderBaseUrl);
        log.info("Redirect URI: {}", redirectUri);

        // Vérifier configuration
        validateConfiguration();

        log.info("✅ BrokerConnectionService configuré - Authentification requise");
        log.info("📋 URLs OAuth2: Auth={}, Token={}",
                OAUTH_BASE_URL + OAUTH_AUTHORIZE_PATH, ctraderBaseUrl + OAUTH_TOKEN_PATH);
    }

    /**
     * Génère URL d'autorisation OAuth2 pour cTrader - CORRIGÉE
     */
    public String getAuthorizationUrl() {
        String state = generateStateParameter();

        // URL corrigée selon documentation cTrader
        String authUrl = UriComponentsBuilder.fromHttpUrl(OAUTH_BASE_URL + OAUTH_AUTHORIZE_PATH)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "trading") // CORRIGÉ: "trading" au lieu de "trading accounts"
                .queryParam("product", "web") // Recommandé pour mobile/web
                .queryParam("state", state)
                .build()
                .toUriString();

        log.debug("URL d'autorisation générée: {}", authUrl);
        return authUrl;
    }

    /**
     * Échange le code d'autorisation contre un access token - CORRIGÉE
     */
    public boolean exchangeCodeForToken(String authorizationCode) {
        log.info("🔑 Échange code autorisation contre access token...");

        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            log.error("Code d'autorisation vide");
            return false;
        }

        try {
            // Préparer requête token selon documentation cTrader
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

            // Construction du body selon doc cTrader
            String body = UriComponentsBuilder.newInstance()
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("code", authorizationCode)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .build()
                    .getQuery(); // Récupère la query string

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // Appel API cTrader avec URL corrigée
            String tokenUrl = ctraderBaseUrl + OAUTH_TOKEN_PATH;
            log.debug("Appel token endpoint: {}", tokenUrl);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.GET, // Note: GET selon la doc cTrader
                    request,
                    JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode tokenResponse = response.getBody();

                // Vérifier présence des champs requis
                if (!tokenResponse.has("accessToken")) {
                    log.error("Réponse token manque 'accessToken'");
                    return false;
                }

                accessToken = tokenResponse.get("accessToken").asText();

                if (tokenResponse.has("refreshToken")) {
                    refreshToken = tokenResponse.get("refreshToken").asText();
                }

                int expiresIn = tokenResponse.has("expiresIn") ?
                        tokenResponse.get("expiresIn").asInt() : 2628000; // 30 jours par défaut

                tokenExpiration = LocalDateTime.now().plusSeconds(expiresIn);

                isConnected = true;
                lastConnectionCheck = LocalDateTime.now();

                log.info("✅ Token obtenu avec succès - Expiration: {}", tokenExpiration);
                log.debug("Access token: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));

                return true;

            } else {
                log.error("❌ Échec obtention token - Status: {} - Body: {}",
                        response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (HttpClientErrorException e) {
            log.error("❌ Erreur HTTP échange code: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;

        } catch (Exception e) {
            log.error("❌ Erreur échange code: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Vérifie et rafraîchit le token si nécessaire - AMÉLIORÉE
     */
    public boolean checkConnection() {
        lastConnectionCheck = LocalDateTime.now();

        if (!hasValidToken()) {
            log.warn("⚠️ Pas de token valide - reconnexion requise");
            isConnected = false;
            return false;
        }

        // Vérifier validité token avec un appel API simple
        try {
            log.debug("Vérification connexion avec appel API...");
            ResponseEntity<JsonNode> response = makeAuthenticatedRequest(
                    ctraderBaseUrl + API_ACCOUNTS_PATH, HttpMethod.GET, null
            );

            boolean connectionOk = response.getStatusCode().is2xxSuccessful();
            isConnected = connectionOk;

            if (!isConnected) {
                log.warn("⚠️ Token invalide - Status: {}", response.getStatusCode());

                // Essayer de rafraîchir le token automatiquement
                if (refreshToken != null) {
                    log.info("Tentative rafraîchissement automatique du token...");
                    boolean refreshed = refreshAccessToken();
                    if (refreshed) {
                        isConnected = true;
                        log.info("✅ Token rafraîchi automatiquement");
                    }
                }
            } else {
                log.debug("✅ Connexion vérifiée avec succès");
            }

            return isConnected;

        } catch (Exception e) {
            log.error("❌ Erreur vérification connexion: {}", e.getMessage());
            isConnected = false;
            return false;
        }
    }

    /**
     * Récupère données historiques pour un symbole - SIMPLIFIÉE pour REST
     * Note: cTrader privilégie ProtoOA pour les données avancées
     */
    public CompletableFuture<JsonNode> getHistoricalData(String symbol, String timeframe, int count) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected() || !hasValidToken()) {
                throw new CompletionException(new RuntimeException("Non connecté à cTrader"));
            }

            try {
                // Construction URL simplifiée - peut nécessiter ajustements selon API réelle
                String url = UriComponentsBuilder.fromHttpUrl(ctraderBaseUrl + API_HISTORICAL_PATH)
                        .queryParam("symbol", symbol)
                        .queryParam("timeframe", mapTimeframeToCtrader(timeframe))
                        .queryParam("count", count)
                        .queryParam("to", Instant.now().getEpochSecond())
                        .build()
                        .toUriString();

                log.debug("Demande données historiques: {}", url);
                ResponseEntity<JsonNode> response = makeAuthenticatedRequest(url, HttpMethod.GET, null);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.debug("📊 Données historiques récupérées: {} {} - {} points",
                            symbol, timeframe, count);
                    return response.getBody();
                } else {
                    throw new RuntimeException("Échec récupération données: " + response.getStatusCode());
                }

            } catch (Exception e) {
                log.error("❌ Erreur récupération historique {} {}: {}", symbol, timeframe, e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Récupère prix temps réel (spot) - SIMPLIFIÉE
     */
    public CompletableFuture<JsonNode> getSpotPrices(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected() || !hasValidToken()) {
                throw new CompletionException(new RuntimeException("Non connecté à cTrader"));
            }

            try {
                String url = UriComponentsBuilder.fromHttpUrl(ctraderBaseUrl + API_SPOT_PATH)
                        .queryParam("symbol", symbol)
                        .build()
                        .toUriString();

                ResponseEntity<JsonNode> response = makeAuthenticatedRequest(url, HttpMethod.GET, null);

                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                } else {
                    throw new RuntimeException("Échec récupération spot: " + response.getStatusCode());
                }

            } catch (Exception e) {
                log.error("❌ Erreur récupération spot {}: {}", symbol, e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Rafraîchit le token d'accès - CORRIGÉE
     */
    public boolean refreshAccessToken() {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            log.error("❌ Pas de refresh token disponible");
            return false;
        }

        try {
            log.info("🔄 Rafraîchissement du token...");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

            String body = UriComponentsBuilder.newInstance()
                    .queryParam("grant_type", "refresh_token")
                    .queryParam("refresh_token", refreshToken)
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .build()
                    .getQuery();

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    ctraderBaseUrl + OAUTH_TOKEN_PATH,
                    HttpMethod.GET,
                    request,
                    JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode tokenResponse = response.getBody();

                if (tokenResponse.has("accessToken")) {
                    accessToken = tokenResponse.get("accessToken").asText();
                    int expiresIn = tokenResponse.has("expiresIn") ?
                            tokenResponse.get("expiresIn").asInt() : 2628000;
                    tokenExpiration = LocalDateTime.now().plusSeconds(expiresIn);

                    if (tokenResponse.has("refreshToken")) {
                        refreshToken = tokenResponse.get("refreshToken").asText();
                    }

                    log.info("🔄 Token rafraîchi avec succès");
                    return true;
                }
            }

        } catch (Exception e) {
            log.error("❌ Erreur rafraîchissement token: {}", e.getMessage());
        }

        return false;
    }

    // ========== Méthodes Utilitaires ==========

    public boolean isConnected() {
        return isConnected && hasValidToken();
    }

    public boolean hasValidToken() {
        return accessToken != null && !accessToken.trim().isEmpty() &&
                tokenExpiration != null &&
                LocalDateTime.now().isBefore(tokenExpiration.minusMinutes(5)); // Marge 5 min
    }

    public LocalDateTime getTokenExpiration() {
        return tokenExpiration;
    }

    public LocalDateTime getLastConnectionCheck() {
        return lastConnectionCheck;
    }

    // ========== Méthodes Privées ==========

    private ResponseEntity<JsonNode> makeAuthenticatedRequest(String url, HttpMethod method, Object body) {
        if (accessToken == null) {
            throw new IllegalStateException("Pas d'access token disponible");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<?> request = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(url, method, request, JsonNode.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Token expiré ou invalide - Status 401");
                isConnected = false;
            }
            throw e;
        }
    }

    private String mapTimeframeToCtrader(String timeframe) {
        // Mapping timeframes internes vers cTrader
        // Note: Ces valeurs peuvent nécessiter des ajustements selon l'API réelle
        return switch (timeframe) {
            case "M1" -> "MINUTE1";
            case "M5" -> "MINUTE5";
            case "M30" -> "MINUTE30";
            case "H1" -> "HOUR1";
            case "D1" -> "DAILY";
            default -> throw new IllegalArgumentException("Timeframe non supporté: " + timeframe);
        };
    }

    private String generateStateParameter() {
        return Base64.getEncoder().encodeToString(
                String.valueOf(System.currentTimeMillis()).getBytes()
        );
    }

    private void validateConfiguration() {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("cTrader Client ID manquant - vérifiez scalper.broker.ctrader.client-id");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("cTrader Client Secret manquant - vérifiez scalper.broker.ctrader.client-secret");
        }
        if (ctraderBaseUrl == null || ctraderBaseUrl.isEmpty()) {
            throw new IllegalStateException("cTrader Base URL manquante - vérifiez scalper.broker.ctrader.base-url");
        }
        if (redirectUri == null || !redirectUri.contains("/api/auth/ctrader/callback")) {
            throw new IllegalStateException("Redirect URI incorrecte - doit pointer vers /api/auth/ctrader/callback");
        }

        log.info("✅ Configuration cTrader validée");
    }
}