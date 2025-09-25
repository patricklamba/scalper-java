package com.scalper;

import com.scalper.model.entity.MarketData;
import com.scalper.repository.MarketDataRepository;
import com.scalper.service.MarketDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration ÉTAPE 3 - MarketData complet
 * Valide simulateur + API REST + services core
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Tests Intégration ÉTAPE 3 - Market Data")
public class MarketDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MarketData sampleEurUsd;
    private MarketData sampleXauUsd;

    @BeforeEach
    void setUp() {
        // Nettoyer et préparer données de test
        marketDataRepository.deleteAll();

        sampleEurUsd = createSampleMarketData("EURUSD", "M5", LocalDateTime.now().minusMinutes(5));
        sampleXauUsd = createSampleMarketData("XAUUSD", "M1", LocalDateTime.now().minusMinutes(1));

        marketDataRepository.save(sampleEurUsd);
        marketDataRepository.save(sampleXauUsd);
    }

    // ========== Tests Service MarketDataService ==========

    @Test
    @DisplayName("Service - Récupération prix actuel")
    void testGetCurrentPrice() {
        // EURUSD - doit retourner le prix le plus récent
        Optional<MarketData> currentEur = marketDataService.getCurrentPrice("EURUSD");
        assertThat(currentEur).isPresent();
        assertThat(currentEur.get().getSymbol()).isEqualTo("EURUSD");
        assertThat(currentEur.get().getClosePrice()).isEqualTo(new BigDecimal("1.08500"));

        // XAUUSD
        Optional<MarketData> currentXau = marketDataService.getCurrentPrice("XAUUSD");
        assertThat(currentXau).isPresent();
        assertThat(currentXau.get().getSymbol()).isEqualTo("XAUUSD");

        // Symbole invalide
        assertThatThrownBy(() -> marketDataService.getCurrentPrice("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbole non supporté");
    }

    @Test
    @DisplayName("Service - Récupération bougies historiques")
    void testGetLatestCandles() {
        // Ajouter plusieurs bougies pour test
        for (int i = 1; i <= 10; i++) {
            MarketData candle = createSampleMarketData("EURUSD", "M5",
                    LocalDateTime.now().minusMinutes(i * 5L));
            marketDataRepository.save(candle);
        }

        // Test récupération 5 dernières bougies
        List<MarketData> candles = marketDataService.getLatestCandles("EURUSD", "M5", 5);

        assertThat(candles).hasSize(5);
        assertThat(candles.get(0).getTimestamp())
                .isAfter(candles.get(1).getTimestamp()); // Ordre décroissant

        // Tous les EURUSD M5
        candles.forEach(candle -> {
            assertThat(candle.getSymbol()).isEqualTo("EURUSD");
            assertThat(candle.getTimeframe()).isEqualTo("M5");
        });
    }

    @Test
    @DisplayName("Service - Calcul niveaux session")
    void testCalculateSessionLevels() {
        // Ajouter données session London
        LocalDateTime londonStart = LocalDateTime.now().withHour(8).withMinute(0);

        MarketData londonHigh = createSampleMarketData("EURUSD", "M1", londonStart.plusMinutes(30));
        londonHigh.setHighPrice(new BigDecimal("1.08750"));
        londonHigh.setSessionName("LONDON");

        MarketData londonLow = createSampleMarketData("EURUSD", "M1", londonStart.plusMinutes(60));
        londonLow.setLowPrice(new BigDecimal("1.08200"));
        londonLow.setSessionName("LONDON");

        marketDataRepository.save(londonHigh);
        marketDataRepository.save(londonLow);

        // Test calcul niveaux
        Map<String, BigDecimal> levels = marketDataService.calculateSessionLevels("EURUSD", "LONDON");

        assertThat(levels).containsKey("LONDON_HIGH");
        assertThat(levels).containsKey("LONDON_LOW");
        assertThat(levels).containsKey("LONDON_MID");

        assertThat(levels.get("LONDON_HIGH")).isEqualTo(new BigDecimal("1.08750"));
        assertThat(levels.get("LONDON_LOW")).isEqualTo(new BigDecimal("1.08200"));
    }

    @Test
    @DisplayName("Service - Détection breakouts")
    void testDetectRecentBreakouts() {
        // Créer bougie breakout (range > 8 pips pour EURUSD)
        MarketData breakoutCandle = createSampleMarketData("EURUSD", "M5", LocalDateTime.now().minusHours(1));
        breakoutCandle.setHighPrice(new BigDecimal("1.08600"));
        breakoutCandle.setLowPrice(new BigDecimal("1.08400")); // Range = 20 pips
        marketDataRepository.save(breakoutCandle);

        // Créer bougie normale
        MarketData normalCandle = createSampleMarketData("EURUSD", "M5", LocalDateTime.now().minusHours(2));
        normalCandle.setHighPrice(new BigDecimal("1.08505"));
        normalCandle.setLowPrice(new BigDecimal("1.08495")); // Range = 1 pip
        marketDataRepository.save(normalCandle);

        // Test détection
        List<MarketData> breakouts = marketDataService.detectRecentBreakouts("EURUSD", "M5", 3);

        assertThat(breakouts).hasSize(1);
        assertThat(breakouts.get(0).getId()).isEqualTo(breakoutCandle.getId());
    }

    @Test
    @DisplayName("Service - Statistiques service")
    void testGetServiceStats() {
        Map<String, Object> stats = marketDataService.getServiceStats();

        assertThat(stats).containsKey("isRunning");
        assertThat(stats).containsKey("simulationMode");
        assertThat(stats).containsKey("symbols");

        assertThat((Boolean) stats.get("isRunning")).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> symbolStats = (Map<String, Object>) stats.get("symbols");
        assertThat(symbolStats).containsKey("EURUSD");
        assertThat(symbolStats).containsKey("XAUUSD");
    }

    // ========== Tests API REST ==========

    @Test
    @DisplayName("API - GET /api/market/current/{symbol}")
    void testGetCurrentPriceEndpoint() throws Exception {
        // Test EURUSD
        mockMvc.perform(get("/api/market/current/EURUSD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("EURUSD"))
                .andExpect(jsonPath("$.closePrice").value(1.08500))
                .andExpect(jsonPath("$.timeframe").value("M5"));

        // Test symbole invalide
        mockMvc.perform(get("/api/market/current/INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API - GET /api/market/candles/{symbol}/{timeframe}")
    void testGetLatestCandlesEndpoint() throws Exception {
        // Ajouter données de test
        for (int i = 1; i <= 5; i++) {
            MarketData candle = createSampleMarketData("XAUUSD", "M1",
                    LocalDateTime.now().minusMinutes(i));
            marketDataRepository.save(candle);
        }

        // Test récupération
        mockMvc.perform(get("/api/market/candles/XAUUSD/M1")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].symbol").value("XAUUSD"))
                .andExpect(jsonPath("$[0].timeframe").value("M1"));

        // Test validation paramètres
        mockMvc.perform(get("/api/market/candles/EURUSD/INVALID"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/market/candles/EURUSD/M1")
                        .param("limit", "2000")) // > 1000 max
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API - GET /api/market/levels/{symbol}/{sessionName}")
    void testGetSessionLevelsEndpoint() throws Exception {
        // Ajouter données session
        MarketData asiaData = createSampleMarketData("EURUSD", "M1", LocalDateTime.now().minusHours(2));
        asiaData.setSessionName("ASIA");
        asiaData.setHighPrice(new BigDecimal("1.08600"));
        asiaData.setLowPrice(new BigDecimal("1.08400"));
        marketDataRepository.save(asiaData);

        // Test endpoint
        mockMvc.perform(get("/api/market/levels/EURUSD/ASIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ASIA_HIGH").value(1.08600))
                .andExpect(jsonPath("$.ASIA_LOW").value(1.08400))
                .andExpect(jsonPath("$.ASIA_MID").exists());
    }

    @Test
    @DisplayName("API - GET /api/market/breakouts/{symbol}")
    void testGetRecentBreakoutsEndpoint() throws Exception {
        // Créer breakout
        MarketData breakout = createSampleMarketData("XAUUSD", "M5", LocalDateTime.now().minusMinutes(30));
        breakout.setHighPrice(new BigDecimal("1952.00"));
        breakout.setLowPrice(new BigDecimal("1950.00")); // Range significatif pour XAU
        marketDataRepository.save(breakout);

        mockMvc.perform(get("/api/market/breakouts/XAUUSD")
                        .param("timeframe", "M5")
                        .param("hours", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("XAUUSD"));
    }

    @Test
    @DisplayName("API - GET /api/market/stats")
    void testGetStatsEndpoint() throws Exception {
        mockMvc.perform(get("/api/market/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRunning").value(true))
                .andExpect(jsonPath("$.simulationMode").exists())
                .andExpect(jsonPath("$.symbols").exists())
                .andExpect(jsonPath("$.symbols.EURUSD").exists())
                .andExpect(jsonPath("$.symbols.XAUUSD").exists());
    }

    @Test
    @DisplayName("API - GET /api/market/health")
    void testHealthCheckEndpoint() throws Exception {
        mockMvc.perform(get("/api/market/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("MarketDataService"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("API - GET /api/market/broker/status")
    void testBrokerStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/market/broker/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").exists())
                .andExpect(jsonPath("$.connected").exists());
    }

    @Test
    @DisplayName("API - GET /api/market/symbols")
    void testGetSupportedSymbolsEndpoint() throws Exception {
        mockMvc.perform(get("/api/market/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbols[0]").value("EURUSD"))
                .andExpect(jsonPath("$.symbols[1]").value("XAUUSD"))
                .andExpect(jsonPath("$.timeframes[0]").value("M1"))
                .andExpect(jsonPath("$.sessions[0]").value("ASIA"));
    }

    // ========== Tests Performance et Stabilité ==========

    @Test
    @DisplayName("Performance - Service répond < 100ms")
    void testServicePerformance() {
        long startTime = System.currentTimeMillis();

        // Opérations typiques
        marketDataService.getCurrentPrice("EURUSD");
        marketDataService.getLatestCandles("EURUSD", "M5", 100);
        marketDataService.calculateSessionLevels("EURUSD", "LONDON");

        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(100); // < 100ms pour opérations de base
    }

    @Test
    @DisplayName("Stabilité - Gestion erreurs gracieuse")
    void testErrorHandling() {
        // Symboles invalides
        assertThatThrownBy(() -> marketDataService.getCurrentPrice("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> marketDataService.getLatestCandles("INVALID", "M1", 10))
                .isInstanceOf(IllegalArgumentException.class);

        // Timeframes invalides
        assertThatThrownBy(() -> marketDataService.getLatestCandles("EURUSD", "INVALID", 10))
                .isInstanceOf(IllegalArgumentException.class);

        // Sessions invalides
        assertThatCode(() -> marketDataService.calculateSessionLevels("EURUSD", "INVALID"))
                .doesNotThrowAnyException(); // Retourne map vide, pas d'exception
    }

    // ========== Méthodes Utilitaires Tests ==========

    private MarketData createSampleMarketData(String symbol, String timeframe, LocalDateTime timestamp) {
        BigDecimal basePrice = symbol.equals("EURUSD") ?
                new BigDecimal("1.08500") : new BigDecimal("1951.00");

        return MarketData.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .timestamp(timestamp)
                .openPrice(basePrice)
                .highPrice(basePrice.add(new BigDecimal("0.00050")))
                .lowPrice(basePrice.subtract(new BigDecimal("0.00050")))
                .closePrice(basePrice)
                .volume(1000L)
                .sessionName("LONDON")
                .sessionProgress(new BigDecimal("0.50"))
                .dataSource("SIMULATOR")
                .volatilityLevel("NORMAL")
                .spreadPips(new BigDecimal("0.5"))
                .isMarketOpen(true)
                .build();
    }
}