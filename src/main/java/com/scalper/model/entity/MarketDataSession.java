package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité représentant les données de marché enrichies avec contexte multi-sessions
 */
@Entity
@Table(name = "market_data_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "timeframe", "timestamp"}),
        indexes = {
                @Index(name = "idx_market_data_sessions_symbol_timeframe_timestamp",
                        columnList = "symbol, timeframe, timestamp DESC"),
                @Index(name = "idx_market_data_sessions_session_name", columnList = "session_name"),
                @Index(name = "idx_market_data_sessions_timestamp", columnList = "timestamp DESC")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"symbol", "timeframe", "timestamp"})
public class MarketDataSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @NotBlank
    @Size(max = 5)
    @Column(name = "timeframe", nullable = false, length = 5)
    private String timeframe;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    // OHLCV standard
    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "open_price", nullable = false, precision = 10, scale = 5)
    private BigDecimal openPrice;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "high_price", nullable = false, precision = 10, scale = 5)
    private BigDecimal highPrice;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "low_price", nullable = false, precision = 10, scale = 5)
    private BigDecimal lowPrice;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "close_price", nullable = false, precision = 10, scale = 5)
    private BigDecimal closePrice;

    @Min(0)
    @Builder.Default
    @Column(name = "volume", columnDefinition = "BIGINT DEFAULT 0")
    private Long volume = 0L;

    // Enrichissement sessions
    @Size(max = 10)
    @Column(name = "session_name", length = 10)
    private String sessionName; // ASIA, LONDON, NEWYORK, OVERLAP

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "session_progress", precision = 3, scale = 2)
    private BigDecimal sessionProgress; // 0.00 à 1.00

    // Calculs techniques
    @Digits(integer = 5, fraction = 5)
    @Column(name = "vwap_session", precision = 10, scale = 5)
    private BigDecimal vwapSession;

    @Column(name = "distance_to_vwap_pips")
    private Integer distanceToVwapPips;

    @Column(name = "distance_to_session_high_pips")
    private Integer distanceToSessionHighPips;

    @Column(name = "distance_to_session_low_pips")
    private Integer distanceToSessionLowPips;

    // Métadonnées de contexte
    @Column(name = "major_news_proximity_minutes")
    private Integer majorNewsProximityMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "volatility_level", length = 10)
    private VolatilityLevel volatilityLevel;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Niveau de volatilité
     */
    public enum VolatilityLevel {
        LOW, NORMAL, HIGH, EXTREME
    }

    /**
     * Calcule la taille de la bougie en pips
     */
    public Integer getCandleSizePips() {
        if (highPrice == null || lowPrice == null) return null;
        return highPrice.subtract(lowPrice)
                .multiply(BigDecimal.valueOf(10000))
                .intValue();
    }

    /**
     * Calcule le body de la bougie en pips
     */
    public Integer getCandleBodyPips() {
        if (openPrice == null || closePrice == null) return null;
        return openPrice.subtract(closePrice)
                .abs()
                .multiply(BigDecimal.valueOf(10000))
                .intValue();
    }

    /**
     * Vérifie si c'est une bougie haussière
     */
    public boolean isBullishCandle() {
        return closePrice != null && openPrice != null &&
                closePrice.compareTo(openPrice) > 0;
    }

    /**
     * Vérifie si c'est une bougie baissière
     */
    public boolean isBearishCandle() {
        return closePrice != null && openPrice != null &&
                closePrice.compareTo(openPrice) < 0;
    }

    /**
     * Calcule le pourcentage du body par rapport à la taille totale
     */
    public BigDecimal getBodyRatio() {
        Integer candleSize = getCandleSizePips();
        Integer bodySize = getCandleBodyPips();

        if (candleSize == null || bodySize == null || candleSize == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(bodySize)
                .divide(BigDecimal.valueOf(candleSize), 3, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Vérifie si la bougie est proche du VWAP (dans X pips)
     */
    public boolean isNearVWAP(int tolerancePips) {
        return distanceToVwapPips != null && distanceToVwapPips <= tolerancePips;
    }

    /**
     * Vérifie si la bougie est proche des extrêmes de session
     */
    public boolean isNearSessionHigh(int tolerancePips) {
        return distanceToSessionHighPips != null && distanceToSessionHighPips <= tolerancePips;
    }

    public boolean isNearSessionLow(int tolerancePips) {
        return distanceToSessionLowPips != null && distanceToSessionLowPips <= tolerancePips;
    }

    /**
     * Détermine le caractère de la bougie pour l'analyse
     */
    public String getCandleCharacter() {
        Integer bodySize = getCandleBodyPips();
        Integer candleSize = getCandleSizePips();
        BigDecimal bodyRatio = getBodyRatio();

        if (bodySize == null || candleSize == null) return "UNKNOWN";

        // Doji (petit body)
        if (bodySize <= 2) {
            return "DOJI";
        }

        // Bougie avec beaucoup de wicks
        if (bodyRatio.compareTo(BigDecimal.valueOf(0.3)) <= 0) {
            return "WICK_DOMINATED";
        }

        // Bougie impulsive (gros body, peu de wicks)
        if (bodyRatio.compareTo(BigDecimal.valueOf(0.8)) >= 0 && bodySize >= 8) {
            return isBullishCandle() ? "BULLISH_IMPULSE" : "BEARISH_IMPULSE";
        }

        // Bougie normale
        if (bodySize >= 5) {
            return isBullishCandle() ? "BULLISH" : "BEARISH";
        }

        return "SMALL";
    }

    /**
     * Vérifie si la bougie montre une rejection (long wick)
     */
    public boolean showsRejection() {
        Integer bodySize = getCandleBodyPips();
        Integer candleSize = getCandleSizePips();

        if (bodySize == null || candleSize == null || candleSize == 0) return false;

        BigDecimal bodyRatio = getBodyRatio();
        return bodyRatio.compareTo(BigDecimal.valueOf(0.5)) <= 0 && candleSize >= 8;
    }

    /**
     * Génère le contexte de trading pour cette bougie
     */
    public String getTradingContext() {
        StringBuilder context = new StringBuilder();

        context.append(String.format("%s %s candle", sessionName, getCandleCharacter()));

        if (isNearVWAP(5)) {
            context.append(" near VWAP");
        }

        if (isNearSessionHigh(10)) {
            context.append(" near session high");
        } else if (isNearSessionLow(10)) {
            context.append(" near session low");
        }

        if (majorNewsProximityMinutes != null && majorNewsProximityMinutes <= 30) {
            context.append(" near news event");
        }

        if (volatilityLevel == VolatilityLevel.HIGH || volatilityLevel == VolatilityLevel.EXTREME) {
            context.append(" high volatility");
        }

        return context.toString();
    }

    /**
     * Calcule l'importance de cette bougie pour le pattern detection
     */
    public BigDecimal getImportanceScore() {
        BigDecimal score = BigDecimal.ZERO;

        // Taille de la bougie
        Integer bodySize = getCandleBodyPips();
        if (bodySize != null) {
            if (bodySize >= 15) score = score.add(BigDecimal.valueOf(0.3));
            else if (bodySize >= 8) score = score.add(BigDecimal.valueOf(0.2));
            else if (bodySize >= 5) score = score.add(BigDecimal.valueOf(0.1));
        }

        // Proximité des niveaux clés
        if (isNearVWAP(3)) score = score.add(BigDecimal.valueOf(0.2));
        if (isNearSessionHigh(5) || isNearSessionLow(5)) score = score.add(BigDecimal.valueOf(0.2));

        // Volume relatif
        if (volume != null && volume > 0) {
            score = score.add(BigDecimal.valueOf(0.1));
        }

        // Rejection strength
        if (showsRejection()) score = score.add(BigDecimal.valueOf(0.15));

        // News proximity
        if (majorNewsProximityMinutes != null && majorNewsProximityMinutes <= 15) {
            score = score.add(BigDecimal.valueOf(0.1));
        }

        return score.min(BigDecimal.ONE);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // Calcul automatique des distances si les données de session sont disponibles
        calculateSessionDistances();
    }

    /**
     * Calcule les distances aux niveaux de session (à implémenter avec les données de session)
     */
    private void calculateSessionDistances() {
        // Cette méthode sera enrichie avec les données de session réelles
        // Pour l'instant, on initialise à null - sera calculé par les services
    }
}