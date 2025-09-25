package com.scalper.service.broker;

import com.scalper.model.entity.MarketData;
import com.scalper.repository.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulateur de données de marché cTrader - ÉTAPE 3 Phase Simulateur
 * Génère des données EURUSD/XAUUSD réalistes avec sessions Asia/London/NY
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scalper.broker.simulation-mode", havingValue = "true", matchIfMissing = true)
public class MarketDataSimulatorService {

    private final MarketDataRepository marketDataRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // État du simulateur
    private final Map<String, MarketData> currentPrices = new ConcurrentHashMap<>();
    private final Map<String, SimulationContext> simulationContexts = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // Configuration simulateur (from application.yml)
    private static final Map<String, SymbolConfig> SYMBOL_CONFIGS = Map.of(
            "EURUSD", new SymbolConfig(new BigDecimal("1.0850"), 80, new BigDecimal("0.5")),
            "XAUUSD", new SymbolConfig(new BigDecimal("1950.0"), 1500, new BigDecimal("20"))
    );

    @PostConstruct
    public void initializeSimulator() {
        log.info("🎯 Initialisation Simulateur cTrader - ÉTAPE 3");

        // Initialiser contextes de simulation pour chaque symbole
        SYMBOL_CONFIGS.forEach((symbol, config) -> {
            SimulationContext context = new SimulationContext(symbol, config);
            simulationContexts.put(symbol, context);

            // Prix initial
            MarketData initialPrice = MarketData.builder()
                    .symbol(symbol)
                    .timeframe("M1")
                    .timestamp(LocalDateTime.now())
                    .openPrice(config.basePrice)
                    .highPrice(config.basePrice.add(BigDecimal.valueOf(0.0001)))
                    .lowPrice(config.basePrice.subtract(BigDecimal.valueOf(0.0001)))
                    .closePrice(config.basePrice)
                    .volume(1000L)
                    .sessionName(getCurrentSession())
                    .sessionProgress(calculateSessionProgress())
                    .dataSource("SIMULATOR")
                    .volatilityLevel("NORMAL")
                    .spreadPips(config.spreadPips)
                    .isMarketOpen(isMarketOpen())
                    .build();

            currentPrices.put(symbol, initialPrice);
            log.info("📊 Simulateur initialisé pour {} - Prix de base: {}",
                    symbol, config.basePrice);
        });

        log.info("✅ Simulateur cTrader démarré - {} symboles actifs", SYMBOL_CONFIGS.size());
    }


    /**
     * Génération continue des données M1 (toutes les minutes)
     */
    @Scheduled(fixedRate = 60000) // 60 seconds = 1 minute
    public void generateM1Data() {
        if (!isMarketOpen()) {
            return; // Pas de génération en dehors des heures de marché
        }

        SYMBOL_CONFIGS.keySet().forEach(this::generateNextCandle);
    }

    /**
     * Génération des bougies M5 et M30 basées sur les données M1
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void generateM5Data() {
        if (!isMarketOpen()) return;

        SYMBOL_CONFIGS.keySet().forEach(symbol -> {
            generateAggregatedCandle(symbol, "M5", 5);
        });
    }

    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void generateM30Data() {
        if (!isMarketOpen()) return;

        SYMBOL_CONFIGS.keySet().forEach(symbol -> {
            generateAggregatedCandle(symbol, "M30", 30);
        });
    }

    /**
     * Génère la prochaine bougie pour un symbole
     */
    private void generateNextCandle(String symbol) {
        try {
            SimulationContext context = simulationContexts.get(symbol);
            MarketData lastCandle = currentPrices.get(symbol);

            if (lastCandle == null) {
                log.warn("⚠️ Pas de prix précédent pour {}, initialisation...", symbol);
                return;
            }

            // Calculer nouveau prix basé sur volatilité de session et tendance
            BigDecimal newPrice = calculateNextPrice(symbol, lastCandle, context);

            // Générer bougie réaliste avec spread et volatilité
            MarketData newCandle = generateRealisticCandle(symbol, newPrice, context);

            // Sauvegarder en base et cache
            marketDataRepository.save(newCandle);
            currentPrices.put(symbol, newCandle);
            cacheLatestPrice(symbol, newCandle);

            // Mise à jour contexte simulation
            context.updateContext(newCandle);

            // Log occasionnel pour monitoring
            if (random.nextInt(10) == 0) { // 10% des fois
                log.info("📈 {} M1: O:{} H:{} L:{} C:{} | Session: {} | Vol: {}",
                        symbol, newCandle.getOpenPrice(), newCandle.getHighPrice(),
                        newCandle.getLowPrice(), newCandle.getClosePrice(),
                        newCandle.getSessionName(), newCandle.getVolatilityLevel());
            }

        } catch (Exception e) {
            log.error("❌ Erreur génération bougie pour {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Calcule le prochain prix basé sur volatilité de session et momentum
     */
    private BigDecimal calculateNextPrice(String symbol, MarketData lastCandle, SimulationContext context) {
        SymbolConfig config = SYMBOL_CONFIGS.get(symbol);
        BigDecimal currentPrice = lastCandle.getClosePrice();

        // Facteurs d'influence sur le prix
        double sessionVolatility = getSessionVolatilityMultiplier();
        double trendMomentum = context.getTrendMomentum();
        double newsImpact = simulateNewsImpact();

        // Calcul du movement (en pourcentage du daily range)
        double maxMovement = config.dailyRangePips * 0.1; // Max 10% du range quotidien par bougie
        double movement = (random.nextGaussian() * maxMovement * sessionVolatility) +
                (trendMomentum * maxMovement * 0.3) +
                (newsImpact * maxMovement * 0.5);

        // Conversion en prix
        BigDecimal pipSize = symbol.equals("EURUSD") ?
                BigDecimal.valueOf(0.0001) : BigDecimal.valueOf(0.01);
        BigDecimal priceMovement = BigDecimal.valueOf(movement).multiply(pipSize);

        BigDecimal newPrice = currentPrice.add(priceMovement);

        // Contraintes réalistes (éviter prix aberrants)
        BigDecimal minPrice = config.basePrice.multiply(BigDecimal.valueOf(0.95));
        BigDecimal maxPrice = config.basePrice.multiply(BigDecimal.valueOf(1.05));

        if (newPrice.compareTo(minPrice) < 0) newPrice = minPrice;
        if (newPrice.compareTo(maxPrice) > 0) newPrice = maxPrice;

        return newPrice;
    }

    /**
     * Génère une bougie réaliste avec OHLC cohérent
     */
    private MarketData generateRealisticCandle(String symbol, BigDecimal targetPrice,
                                               SimulationContext context) {
        MarketData lastCandle = currentPrices.get(symbol);
        BigDecimal open = lastCandle.getClosePrice();
        BigDecimal close = targetPrice;

        // Génération High/Low réaliste
        BigDecimal range = close.subtract(open).abs();
        BigDecimal maxRange = range.multiply(BigDecimal.valueOf(1.5)); // 50% de range supplémentaire max

        BigDecimal high = open.max(close).add(
                BigDecimal.valueOf(random.nextDouble() * maxRange.doubleValue()));
        BigDecimal low = open.min(close).subtract(
                BigDecimal.valueOf(random.nextDouble() * maxRange.doubleValue()));

        // Assurer cohérence OHLC
        high = high.max(open).max(close);
        low = low.min(open).min(close);

        // Volume simulé basé sur session
        long volume = generateRealisticVolume(symbol, getCurrentSession());

        return MarketData.builder()
                .symbol(symbol)
                .timeframe("M1")
                .timestamp(LocalDateTime.now())
                .openPrice(open.setScale(5, RoundingMode.HALF_UP))
                .highPrice(high.setScale(5, RoundingMode.HALF_UP))
                .lowPrice(low.setScale(5, RoundingMode.HALF_UP))
                .closePrice(close.setScale(5, RoundingMode.HALF_UP))
                .volume(volume)
                .sessionName(getCurrentSession())
                .sessionProgress(calculateSessionProgress())
                .vwapSession(context.calculateCurrentVWAP())
                .distanceToVwapPips(calculateDistanceToVWAP(close, context.calculateCurrentVWAP(), symbol))
                .volatilityLevel(determineVolatilityLevel(high.subtract(low)))
                .majorNewsProximityMinutes(simulateNewsProximity())
                .dataSource("SIMULATOR")
                .spreadPips(SYMBOL_CONFIGS.get(symbol).spreadPips)
                .isMarketOpen(true)
                .build();
    }

    /**
     * Génère bougies aggregées (M5, M30) basées sur données M1
     */
    private void generateAggregatedCandle(String symbol, String timeframe, int minutes) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(minutes);

        List<MarketData> m1Candles = marketDataRepository
                .findBySymbolAndTimeframeAndTimestampBetween(symbol, "M1", startTime, endTime);

        if (m1Candles.isEmpty()) {
            log.warn("⚠️ Pas de données M1 pour générer {} {}", symbol, timeframe);
            return;
        }

        // Agrégation OHLCV
        BigDecimal open = m1Candles.get(0).getOpenPrice();
        BigDecimal close = m1Candles.get(m1Candles.size() - 1).getClosePrice();
        BigDecimal high = m1Candles.stream()
                .map(MarketData::getHighPrice)
                .max(BigDecimal::compareTo)
                .orElse(open);
        BigDecimal low = m1Candles.stream()
                .map(MarketData::getLowPrice)
                .min(BigDecimal::compareTo)
                .orElse(open);
        long totalVolume = m1Candles.stream()
                .mapToLong(MarketData::getVolume)
                .sum();

        MarketData aggregatedCandle = MarketData.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .timestamp(endTime)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(totalVolume)
                .sessionName(getCurrentSession())
                .sessionProgress(calculateSessionProgress())
                .dataSource("SIMULATOR")
                .volatilityLevel(determineVolatilityLevel(high.subtract(low)))
                .spreadPips(SYMBOL_CONFIGS.get(symbol).spreadPips)
                .isMarketOpen(true)
                .build();

        marketDataRepository.save(aggregatedCandle);
        cacheLatestPrice(symbol + "_" + timeframe, aggregatedCandle);
    }

    // ========== Méthodes Utilitaires ==========

    private String getCurrentSession() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 6) return "ASIA";
        if (hour >= 7 && hour < 11) return "LONDON";
        if (hour >= 12 && hour < 16) return "NEWYORK";
        return "OVERLAP";
    }

    private BigDecimal calculateSessionProgress() {
        int hour = LocalDateTime.now().getHour();
        int minute = LocalDateTime.now().getMinute();
        double totalMinutes = hour * 60.0 + minute;

        return switch (getCurrentSession()) {
            case "ASIA" -> BigDecimal.valueOf((totalMinutes - 0) / 360.0).setScale(2, RoundingMode.HALF_UP);
            case "LONDON" -> BigDecimal.valueOf((totalMinutes - 420) / 240.0).setScale(2, RoundingMode.HALF_UP);
            case "NEWYORK" -> BigDecimal.valueOf((totalMinutes - 720) / 240.0).setScale(2, RoundingMode.HALF_UP);
            default -> BigDecimal.valueOf(0.5);
        };
    }

    private boolean isMarketOpen() {
        int hour = LocalDateTime.now().getHour();
        int dayOfWeek = LocalDateTime.now().getDayOfWeek().getValue();

        // Fermé weekend (samedi-dimanche)
        if (dayOfWeek >= 6) return false;

        // Ouvert 24h en semaine sauf pause entre NY close et Asia open
        return !(hour >= 17 && hour < 23); // Pause 17h-23h UTC
    }

    private double getSessionVolatilityMultiplier() {
        return switch (getCurrentSession()) {
            case "ASIA" -> 0.7; // Session calme
            case "LONDON" -> 1.2; // Session active
            case "NEWYORK" -> 1.0; // Session normale
            default -> 0.9;
        };
    }

    private double simulateNewsImpact() {
        // 5% de chance d'avoir un impact news significatif
        return random.nextDouble() < 0.05 ?
                (random.nextGaussian() * 2.0) : 0.0;
    }

    private long generateRealisticVolume(String symbol, String session) {
        long baseVolume = symbol.equals("EURUSD") ? 1000L : 500L;
        double sessionMultiplier = switch (session) {
            case "LONDON" -> 1.5;
            case "NEWYORK" -> 1.2;
            default -> 0.8;
        };

        return (long) (baseVolume * sessionMultiplier * (0.5 + random.nextDouble()));
    }

    private String determineVolatilityLevel(BigDecimal range) {
        double rangeValue = range.doubleValue();

        if (rangeValue < 0.0005) return "LOW";
        if (rangeValue < 0.002) return "NORMAL";
        if (rangeValue < 0.005) return "HIGH";
        return "EXTREME";
    }

    private Integer simulateNewsProximity() {
        // Simuler proximité news (0-360 minutes)
        return random.nextInt(360);
    }

    private Integer calculateDistanceToVWAP(BigDecimal currentPrice, BigDecimal vwap, String symbol) {
        if (vwap == null) return null;

        BigDecimal distance = currentPrice.subtract(vwap).abs();
        BigDecimal pipSize = symbol.equals("EURUSD") ?
                BigDecimal.valueOf(0.0001) : BigDecimal.valueOf(0.01);

        return distance.divide(pipSize, 0, RoundingMode.HALF_UP).intValue();
    }

    private void cacheLatestPrice(String key, MarketData candle) {
        try {
            redisTemplate.opsForValue().set("market:latest:" + key, candle);
            redisTemplate.expire("market:latest:" + key,
                    java.time.Duration.ofMinutes(10));
        } catch (Exception e) {
            log.warn("⚠️ Erreur cache Redis pour {}: {}", key, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("🔚 Arrêt Simulateur cTrader");
    }

    // ========== Classes Internes ==========

    private static class SymbolConfig {
        final BigDecimal basePrice;
        final int dailyRangePips;
        final BigDecimal spreadPips;

        SymbolConfig(BigDecimal basePrice, int dailyRangePips, BigDecimal spreadPips) {
            this.basePrice = basePrice;
            this.dailyRangePips = dailyRangePips;
            this.spreadPips = spreadPips;
        }
    }

    private static class SimulationContext {
        private final String symbol;
        private final SymbolConfig config;
        private final List<BigDecimal> recentPrices = new ArrayList<>();
        private double momentum = 0.0;

        SimulationContext(String symbol, SymbolConfig config) {
            this.symbol = symbol;
            this.config = config;
        }

        void updateContext(MarketData newCandle) {
            recentPrices.add(newCandle.getClosePrice());
            if (recentPrices.size() > 20) {
                recentPrices.remove(0);
            }

            // Calcul simple du momentum
            if (recentPrices.size() >= 2) {
                BigDecimal current = recentPrices.get(recentPrices.size() - 1);
                BigDecimal previous = recentPrices.get(recentPrices.size() - 2);
                momentum = current.subtract(previous).doubleValue() * 10000; // En pips
            }
        }

        double getTrendMomentum() {
            return Math.tanh(momentum / 10.0); // Normalisation entre -1 et 1
        }

        BigDecimal calculateCurrentVWAP() {
            if (recentPrices.isEmpty()) return config.basePrice;

            return recentPrices.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(recentPrices.size()), 5, RoundingMode.HALF_UP);
        }
    }
}