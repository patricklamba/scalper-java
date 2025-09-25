package com.scalper.service;

import com.scalper.model.entity.MarketData;
import com.scalper.repository.MarketDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service principal de gestion des données de marché - ÉTAPE 3
 * Version corrigée compatible avec repository fixé
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketDataRepository marketDataRepository;
 //   private final RedisTemplate<String, Object> redisTemplate;

    @Value("${scalper.market-data.collection.enabled:true}")
    private boolean collectionEnabled;

    @Value("${scalper.market-data.collection.timeframes:M1,M5,M30}")
    private List<String> timeframes;

    @Value("${scalper.market-data.collection.max-history-candles:1000}")
    private int maxHistoryCandles;

    @Value("${scalper.market-data.cache.ttl-seconds:300}")
    private int cacheTtlSeconds;

    // Symboles supportés
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.asList("EURUSD", "XAUUSD");

    // Cache local pour performance
    private final Map<String, MarketData> latestPricesCache = new ConcurrentHashMap<>();
    private final Map<String, List<MarketData>> sessionDataCache = new ConcurrentHashMap<>();

    // État du service
    private volatile boolean isRunning = false;
    private volatile LocalDateTime lastUpdateTime = LocalDateTime.now();

    @PostConstruct
    public void initialize() {
        log.info("Initialisation MarketDataService - ÉTAPE 3");
        log.info("Collection: {} | Timeframes: {}", collectionEnabled, timeframes);

        if (collectionEnabled) {
            // Charger derniers prix depuis DB
            loadLatestPricesFromDatabase();

            // Valider configuration
            validateConfiguration();

            isRunning = true;
            log.info("MarketDataService démarré avec succès");
        } else {
            log.info("Collection de données désactivée");
        }
    }

    /**
     * Récupère les dernières bougies pour un symbole et timeframe
     */
    public List<MarketData> getLatestCandles(String symbol, String timeframe, int limit) {
        validateSymbol(symbol);
        validateTimeframe(timeframe);

//        String cacheKey = String.format("candles:%s:%s:%d", symbol, timeframe, limit);
//
//        // Essayer le cache Redis d'abord
//        try {
//            @SuppressWarnings("unchecked")
//            List<MarketData> cached = (List<MarketData>) redisTemplate.opsForValue().get(cacheKey);
//            if (cached != null && !cached.isEmpty()) {
//                log.debug("Cache hit pour {} {} - {} bougies", symbol, timeframe, cached.size());
//                return cached;
//            }
//        } catch (Exception e) {
//            log.warn("Erreur accès cache pour {}: {}", cacheKey, e.getMessage());
//        }

        // Récupérer depuis DB - CORRIGÉ : utilisation méthode Spring Data
        List<MarketData> candles = marketDataRepository
                .findBySymbolAndTimeframeOrderByTimestampDesc(symbol, timeframe)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());

        // Mettre en cache
//        if (!candles.isEmpty()) {
//            cacheData(cacheKey, candles, Duration.ofSeconds(cacheTtlSeconds));
//        }

        log.debug("Récupéré {} bougies {} {} depuis DB", candles.size(), symbol, timeframe);
        return candles;
    }

    /**
     * Récupère le prix actuel pour un symbole
     */
    public Optional<MarketData> getCurrentPrice(String symbol) {
        validateSymbol(symbol);

        // Vérifier cache local
        MarketData cached = latestPricesCache.get(symbol);
        if (cached != null && isRecentData(cached)) {
            return Optional.of(cached);
        }

        // CORRIGÉ : Utilisation méthode Spring Data générée
        Optional<MarketData> latest = marketDataRepository
                .findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "M1");
        latest.ifPresent(price -> latestPricesCache.put(symbol, price));

        return latest;
    }

    /**
     * Récupère données pour une session spécifique
     */
    public List<MarketData> getSessionData(String symbol, String sessionName, String timeframe) {
        validateSymbol(symbol);
        validateTimeframe(timeframe);

        String cacheKey = String.format("session:%s:%s:%s", symbol, sessionName, timeframe);

        // Vérifier cache session
        List<MarketData> cached = sessionDataCache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            MarketData lastCandle = cached.get(cached.size() - 1);
            if (isRecentData(lastCandle)) {
                return cached;
            }
        }

        // CORRECTION : Utiliser la nouvelle méthode du repository
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<MarketData> sessionData = marketDataRepository
                .findSessionDataBySymbolAndSessionAndDate(symbol, timeframe, sessionName, startOfDay);

        // Mettre en cache
        if (!sessionData.isEmpty()) {
            sessionDataCache.put(cacheKey, sessionData);
        }

        return sessionData;
    }

    /**
     * Calcule niveaux intraday pour une session
     */
    public Map<String, BigDecimal> calculateSessionLevels(String symbol, String sessionName) {
        validateSymbol(symbol);

        // CORRIGÉ : Utilisation startOfDay comme paramètre
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Object[]> results = marketDataRepository.findSessionHighLow(symbol, sessionName, startOfDay);
        Map<String, BigDecimal> levels = new HashMap<>();

        if (!results.isEmpty() && results.get(0).length >= 2) {
            Object[] result = results.get(0);
            BigDecimal high = (BigDecimal) result[0];
            BigDecimal low = (BigDecimal) result[1];

            if (high != null && low != null) {
                levels.put(sessionName + "_HIGH", high);
                levels.put(sessionName + "_LOW", low);
                levels.put(sessionName + "_MID",
                        high.add(low).divide(BigDecimal.valueOf(2), 5, RoundingMode.HALF_UP));

                // Calcul VWAP si disponible - CORRIGÉ
                Optional<BigDecimal> vwap = marketDataRepository
                        .calculateSessionVWAP(symbol, sessionName, startOfDay);
                vwap.ifPresent(v -> levels.put(sessionName + "_VWAP", v));
            }
        }

        return levels;
    }

    /**
     * Détecte bougies de breakout récentes
     */
    public List<MarketData> detectRecentBreakouts(String symbol, String timeframe, int hours) {
        validateSymbol(symbol);
        validateTimeframe(timeframe);

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        BigDecimal threshold = symbol.equals("EURUSD") ?
                BigDecimal.valueOf(0.0008) : BigDecimal.valueOf(0.08); // 8 pips ou 80 cents

        return marketDataRepository.findBreakoutCandles(symbol, timeframe, since, threshold);
    }

    /**
     * Détecte niveaux de pivot simplifiés
     */
    public List<MarketData> findPivotLevels(String symbol, int hours) {
        validateSymbol(symbol);

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<MarketData> potentialPivots = marketDataRepository
                .findPotentialPivotLevels(symbol, since);

        // Logique simplifiée pour identifier les pivots
        // En production, cette logique serait plus sophistiquée
        return potentialPivots.stream()
                .filter(this::isPivotCandidate)
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Statistiques du service
     */
    public Map<String, Object> getServiceStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("isRunning", isRunning);
        stats.put("mode", "SIMULATOR");
        stats.put("lastUpdateTime", lastUpdateTime);
        stats.put("cachedSymbols", latestPricesCache.size());
        stats.put("sessionCaches", sessionDataCache.size());

        // Statistiques par symbole
        Map<String, Object> symbolStats = new HashMap<>();
        for (String symbol : SUPPORTED_SYMBOLS) {
            Map<String, Object> symbolInfo = new HashMap<>();

            // Compter bougies récentes
            LocalDateTime since24h = LocalDateTime.now().minusHours(24);
            long count24h = marketDataRepository.countCandlesSince(symbol, "M1", since24h);
            symbolInfo.put("candles24h", count24h);

            // Prix actuel
            getCurrentPrice(symbol).ifPresent(price -> {
                symbolInfo.put("currentPrice", price.getClosePrice());
                symbolInfo.put("lastUpdate", price.getTimestamp());
                symbolInfo.put("session", price.getSessionName());
            });

            symbolStats.put(symbol, symbolInfo);
        }
        stats.put("symbols", symbolStats);

        return stats;
    }

    /**
     * Nettoyage périodique des données anciennes
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2h du matin chaque jour
    public void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            marketDataRepository.deleteOldData(cutoff);

            // Nettoyer caches
            latestPricesCache.clear();
            sessionDataCache.clear();

            log.info("Nettoyage données anciennes terminé - cutoff: {}", cutoff);

        } catch (Exception e) {
            log.error("Erreur nettoyage données: {}", e.getMessage());
        }
    }

    // Ajouter cette méthode dans MarketDataService
    public CompletableFuture<Boolean> forceDataUpdate(String symbol) {
        validateSymbol(symbol);

        // Pour l'instant, en mode simulateur, on retourne simplement true
        // Cette méthode sera implémentée plus tard pour l'API cTrader réelle
        log.info("Force update demandé pour {} - Mode simulateur activé", symbol);

        return CompletableFuture.completedFuture(true);
    }

    // ========== Méthodes Privées ==========

    private void loadLatestPricesFromDatabase() {
        for (String symbol : SUPPORTED_SYMBOLS) {
            Optional<MarketData> latest = marketDataRepository
                    .findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "M1");
            latest.ifPresent(price -> {
                latestPricesCache.put(symbol, price);
                log.debug("Prix initial chargé pour {}: {}", symbol, price.getClosePrice());
            });
        }
    }

    private void validateConfiguration() {
        if (timeframes.isEmpty()) {
            throw new IllegalStateException("Aucun timeframe configuré");
        }

        for (String timeframe : timeframes) {
            if (!Arrays.asList("M1", "M5", "M30").contains(timeframe)) {
                throw new IllegalArgumentException("Timeframe non supporté: " + timeframe);
            }
        }
    }

    private void validateSymbol(String symbol) {
        if (!SUPPORTED_SYMBOLS.contains(symbol)) {
            throw new IllegalArgumentException("Symbole non supporté: " + symbol);
        }
    }

    private void validateTimeframe(String timeframe) {
        if (!Arrays.asList("M1", "M5", "M30").contains(timeframe)) {
            throw new IllegalArgumentException("Timeframe non supporté: " + timeframe);
        }
    }

    private boolean isRecentData(MarketData data) {
        return Duration.between(data.getTimestamp(), LocalDateTime.now()).toMinutes() < 5;
    }

    private boolean isPivotCandidate(MarketData candle) {
        // Logique simple pour identifier les pivots
        // En réalité, cela nécessiterait une analyse plus complexe
        BigDecimal range = candle.getHighPrice().subtract(candle.getLowPrice());
        BigDecimal threshold = candle.getSymbol().equals("EURUSD") ?
                BigDecimal.valueOf(0.0005) : BigDecimal.valueOf(0.50);
        return range.compareTo(threshold) > 0;
    }

    private void cacheData(String key, Object data, Duration ttl) {
       // try {
         //   redisTemplate.opsForValue().set(key, data, ttl);
        //} catch (Exception e) {
          //  log.warn("Erreur mise en cache {}: {}", key, e.getMessage());
        //}
        log.debug("Cache Redis désactivé temporairement - clé: {}", key);
    }
}