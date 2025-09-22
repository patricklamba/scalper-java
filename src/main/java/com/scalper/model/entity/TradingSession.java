package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entité représentant une session de trading (Asia, London, New York)
 * avec ses métriques OHLC et contexte de marché
 */
@Entity
@Table(name = "trading_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "session_name", "session_date"}),
        indexes = {
                @Index(name = "idx_trading_sessions_symbol_date", columnList = "symbol, session_date DESC"),
                @Index(name = "idx_trading_sessions_session_name", columnList = "session_name"),
                @Index(name = "idx_trading_sessions_date", columnList = "session_date DESC")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"symbol", "sessionName", "sessionDate"})
@ToString(exclude = {"intradayLevels", "sessionBreakouts"})
public class TradingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @NotNull(message = "Le nom de session ne peut pas être null")
    @Enumerated(EnumType.STRING)
    @Column(name = "session_name", nullable = false, length = 10)
    private SessionName sessionName;

    @NotNull
    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @NotNull
    @Column(name = "session_start", nullable = false)
    private LocalDateTime sessionStart;

    @NotNull
    @Column(name = "session_end", nullable = false)
    private LocalDateTime sessionEnd;

    // OHLC de la session
    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "session_open", nullable = false, precision = 10, scale = 5)
    private BigDecimal sessionOpen;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "session_high", nullable = false, precision = 10, scale = 5)
    private BigDecimal sessionHigh;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "session_low", nullable = false, precision = 10, scale = 5)
    private BigDecimal sessionLow;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "session_close", nullable = false, precision = 10, scale = 5)
    private BigDecimal sessionClose;

    // Métriques de session
    @NotNull
    @Min(0)
    @Column(name = "range_size_pips", nullable = false)
    private Integer rangeSizePips;

    @Min(0)
    @Column(name = "volume_total", columnDefinition = "BIGINT DEFAULT 0")
    @Builder.Default
    private Long volumeTotal = 0L;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "volatility_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal volatilityScore;

    @Builder.Default
    @Column(name = "breakout_occurred", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean breakoutOccurred = false;

    @Size(max = 5)
    @Column(name = "breakout_direction", length = 5)
    private String breakoutDirection; // LONG, SHORT

    // Contexte de trading
    @Min(0)
    @Builder.Default
    @Column(name = "major_news_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer majorNewsCount = 0;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Builder.Default
    @Column(name = "news_impact_score", precision = 3, scale = 2, columnDefinition = "DECIMAL(3,2) DEFAULT 0.00")
    private BigDecimal newsImpactScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "session_quality", length = 15, columnDefinition = "VARCHAR(15) DEFAULT 'NORMAL'")
    private SessionQuality sessionQuality = SessionQuality.NORMAL;

    // Métriques calculées
    @Digits(integer = 5, fraction = 5)
    @Column(name = "vwap_price", precision = 10, scale = 5)
    private BigDecimal vwapPrice;

    @Digits(integer = 5, fraction = 5)
    @Column(name = "pivot_point", precision = 10, scale = 5)
    private BigDecimal pivotPoint;

    @Digits(integer = 5, fraction = 5)
    @Column(name = "support_1", precision = 10, scale = 5)
    private BigDecimal support1;

    @Digits(integer = 5, fraction = 5)
    @Column(name = "resistance_1", precision = 10, scale = 5)
    private BigDecimal resistance1;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relations
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.Set<IntradayLevel> intradayLevels;

    @OneToMany(mappedBy = "originSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.Set<SessionBreakout> sessionBreakouts;

    /**
     * Énumération des noms de sessions
     */
    public enum SessionName {
        ASIA, LONDON, NEWYORK
    }

    /**
     * Énumération de la qualité de session
     */
    public enum SessionQuality {
        QUIET, NORMAL, ACTIVE, VOLATILE
    }

    /**
     * Calcule la taille du range en pourcentage du prix
     */
    public BigDecimal getRangeSizePercent() {
        if (sessionHigh == null || sessionLow == null || sessionOpen == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal range = sessionHigh.subtract(sessionLow);
        return range.divide(sessionOpen, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Vérifie si la session a un range valide pour les breakouts
     */
    public boolean hasValidRange() {
        return rangeSizePips != null && rangeSizePips >= 15 && rangeSizePips <= 100;
    }

    /**
     * Calcule le milieu du range de la session
     */
    public BigDecimal getRangeMidpoint() {
        if (sessionHigh == null || sessionLow == null) {
            return null;
        }
        return sessionHigh.add(sessionLow).divide(BigDecimal.valueOf(2), 5, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Vérifie si un prix est au-dessus du high de la session
     */
    public boolean isPriceAboveHigh(BigDecimal price) {
        return sessionHigh != null && price != null && price.compareTo(sessionHigh) > 0;
    }

    /**
     * Vérifie si un prix est en-dessous du low de la session
     */
    public boolean isPriceBelowLow(BigDecimal price) {
        return sessionLow != null && price != null && price.compareTo(sessionLow) < 0;
    }

    /**
     * Calcule la distance d'un prix par rapport au high/low (en pips)
     */
    public Integer getDistanceFromHigh(BigDecimal price) {
        if (sessionHigh == null || price == null) return null;
        return sessionHigh.subtract(price)
                .multiply(BigDecimal.valueOf(10000)) // Conversion en pips pour EURUSD
                .intValue();
    }

    public Integer getDistanceFromLow(BigDecimal price) {
        if (sessionLow == null || price == null) return null;
        return price.subtract(sessionLow)
                .multiply(BigDecimal.valueOf(10000)) // Conversion en pips pour EURUSD
                .intValue();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // Calcul automatique du range en pips si non défini
        if (rangeSizePips == null && sessionHigh != null && sessionLow != null) {
            BigDecimal range = sessionHigh.subtract(sessionLow);
            rangeSizePips = range.multiply(BigDecimal.valueOf(10000)).intValue();
        }

        // Calcul automatique du pivot point
        if (pivotPoint == null && sessionHigh != null && sessionLow != null && sessionClose != null) {
            pivotPoint = sessionHigh.add(sessionLow).add(sessionClose)
                    .divide(BigDecimal.valueOf(3), 5, java.math.RoundingMode.HALF_UP);
        }
    }
}