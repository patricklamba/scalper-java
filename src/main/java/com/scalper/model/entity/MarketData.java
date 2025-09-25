package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité JPA pour données de marché (OHLCV) multi-timeframes
 * Support simulateur cTrader + API réelle
 */
@Entity
@Table(name = "market_data_sessions",
        indexes = {
                @Index(name = "idx_market_data_symbol_timeframe", columnList = "symbol, timeframe"),
                @Index(name = "idx_market_data_timestamp", columnList = "timestamp DESC"),
                @Index(name = "idx_market_data_session", columnList = "session_name, timestamp DESC")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"symbol", "timeframe", "timestamp"})
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identification
    @Column(nullable = false, length = 10)
    @NotBlank(message = "Symbol is required")
    @Pattern(regexp = "^(EURUSD|XAUUSD)$", message = "Only EURUSD and XAUUSD supported")
    private String symbol;

    @Column(nullable = false, length = 5)
    @NotBlank(message = "Timeframe is required")
    @Pattern(regexp = "^(M1|M5|M30)$", message = "Only M1, M5, M30 timeframes supported")
    private String timeframe;

    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    // OHLCV Standard
    @Column(name = "open_price", nullable = false, precision = 10, scale = 5)
    @NotNull(message = "Open price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Open price must be positive")
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 10, scale = 5)
    @NotNull(message = "High price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "High price must be positive")
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 10, scale = 5)
    @NotNull(message = "Low price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Low price must be positive")
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 10, scale = 5)
    @NotNull(message = "Close price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Close price must be positive")
    private BigDecimal closePrice;

    @Column(nullable = false)
    @Builder.Default
    private Long volume = 0L;

    // Enrichissement Sessions (multi-sessions)
    @Column(name = "session_name", length = 10)
    @Pattern(regexp = "^(ASIA|LONDON|NEWYORK|OVERLAP)$",
            message = "Session must be ASIA, LONDON, NEWYORK or OVERLAP")
    private String sessionName;

    @Column(name = "session_progress", precision = 3, scale = 2)
    @DecimalMin(value = "0.00", message = "Session progress must be >= 0")
    @DecimalMax(value = "1.00", message = "Session progress must be <= 1")
    private BigDecimal sessionProgress; // 0.00 à 1.00

    // Calculs Techniques Enrichis
    @Column(name = "vwap_session", precision = 10, scale = 5)
    private BigDecimal vwapSession;

    @Column(name = "distance_to_vwap_pips")
    private Integer distanceToVwapPips;

    @Column(name = "distance_to_session_high_pips")
    private Integer distanceToSessionHighPips;

    @Column(name = "distance_to_session_low_pips")
    private Integer distanceToSessionLowPips;

    // Contexte Marché et News
    @Column(name = "major_news_proximity_minutes")
    private Integer majorNewsProximityMinutes; // Minutes jusqu'à prochaine news HIGH impact

    @Column(name = "volatility_level", length = 10)
    @Pattern(regexp = "^(LOW|NORMAL|HIGH|EXTREME)$",
            message = "Volatility level must be LOW, NORMAL, HIGH or EXTREME")
    private String volatilityLevel;

    // Source de données (simulateur ou API réelle)
    @Column(name = "data_source", length = 15)
    @Builder.Default
    @Pattern(regexp = "^(SIMULATOR|CTRADER_API|HISTORICAL)$",
            message = "Data source must be SIMULATOR, CTRADER_API or HISTORICAL")
    private String dataSource = "SIMULATOR";

    // Métadonnées techniques
    @Column(name = "spread_pips", precision = 4, scale = 1)
    private BigDecimal spreadPips;

    @Column(name = "is_market_open")
    @Builder.Default
    private Boolean isMarketOpen = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // Méthodes utilitaires
    public BigDecimal getTypicalPrice() {
        return highPrice.add(lowPrice).add(closePrice)
                .divide(BigDecimal.valueOf(3), 5, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getRangeInPips() {
        BigDecimal range = highPrice.subtract(lowPrice);
        // EURUSD : 1 pip = 0.0001, XAUUSD : 1 pip = 0.01
        BigDecimal pipSize = symbol.equals("EURUSD") ?
                BigDecimal.valueOf(0.0001) : BigDecimal.valueOf(0.01);
        return range.divide(pipSize, 1, java.math.RoundingMode.HALF_UP);
    }

    public boolean isBreakoutCandle() {
        BigDecimal range = getRangeInPips();
        BigDecimal threshold = symbol.equals("EURUSD") ?
                BigDecimal.valueOf(8) : BigDecimal.valueOf(80); // 8 pips EURUSD, 80 cents XAUUSD
        return range.compareTo(threshold) > 0;
    }
}