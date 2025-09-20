package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité représentant un breakout inter-sessions avec suivi complet
 * du contexte et de la performance post-breakout
 */
@Entity
@Table(name = "session_breakouts",
        indexes = {
                @Index(name = "idx_session_breakouts_timestamp", columnList = "breakout_timestamp DESC"),
                @Index(name = "idx_session_breakouts_symbol", columnList = "symbol"),
                @Index(name = "idx_session_breakouts_session", columnList = "breakout_session"),
                @Index(name = "idx_session_breakouts_direction", columnList = "breakout_direction")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"symbol", "breakoutTimestamp", "brokenLevel"})
@ToString(exclude = {"brokenLevel", "originSession", "newsAlert", "tradingSignal"})
public class SessionBreakout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broken_level_id", foreignKey = @ForeignKey(name = "fk_session_breakout_level"))
    private IntradayLevel brokenLevel;

    // Détails du breakout
    @NotBlank
    @Size(max = 10)
    @Column(name = "breakout_session", nullable = false, length = 10)
    private String breakoutSession;

    @NotBlank
    @Size(max = 10)
    @Column(name = "origin_session", nullable = false, length = 10)
    private String originSession;

    @NotNull
    @Column(name = "breakout_timestamp", nullable = false)
    private LocalDateTime breakoutTimestamp;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "breakout_price", nullable = false, precision = 10, scale = 5)
    private BigDecimal breakoutPrice;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "breakout_direction", nullable = false, length = 5)
    private BreakoutDirection breakoutDirection;

    // Confirmation technique
    @Builder.Default
    @Column(name = "volume_confirmation", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean volumeConfirmation = false;

    @DecimalMin("0.0")
    @Digits(integer = 2, fraction = 2)
    @Column(name = "volume_ratio", precision = 4, scale = 2)
    private BigDecimal volumeRatio; // Volume breakout vs moyenne session

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "momentum_strength", precision = 3, scale = 2)
    private BigDecimal momentumStrength;

    // Contexte fondamental
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_catalyst_id", foreignKey = @ForeignKey(name = "fk_session_breakout_news"))
    private NewsAlert newsAlert;

    @Size(max = 10)
    @Column(name = "news_impact_level", length = 10)
    private String newsImpactLevel;

    @Column(name = "time_to_news_minutes")
    private Integer timeToNewsMinutes;

    // Suivi post-breakout
    @Builder.Default
    @Column(name = "retest_occurred", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean retestOccurred = false;

    @Column(name = "retest_timestamp")
    private LocalDateTime retestTimestamp;

    @Digits(integer = 5, fraction = 5)
    @Column(name = "retest_price", precision = 10, scale = 5)
    private BigDecimal retestPrice;

    @Column(name = "retest_held")
    private Boolean retestHeld;

    @Min(0)
    @Column(name = "max_follow_through_pips")
    private Integer maxFollowThroughPips;

    @Column(name = "max_follow_through_time")
    private LocalDateTime maxFollowThroughTime;

    // Performance tracking
    @Builder.Default
    @Column(name = "signal_generated", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean signalGenerated = false;

    @OneToOne(mappedBy = "relatedBreakout", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TradingSignalEnriched tradingSignal;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relations additionnelles
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_session_id", foreignKey = @ForeignKey(name = "fk_session_breakout_origin"))
    private TradingSession originSessionEntity;

    /**
     * Direction du breakout
     */
    public enum BreakoutDirection {
        LONG, SHORT
    }

    /**
     * Vérifie si le breakout est confirmé techniquement
     */
    public boolean isTechnicallyConfirmed() {
        return Boolean.TRUE.equals(volumeConfirmation) &&
                momentumStrength != null &&
                momentumStrength.compareTo(BigDecimal.valueOf(0.6)) >= 0;
    }

    /**
     * Vérifie si le breakout a un catalyseur news
     */
    public boolean hasNewsCatalyst() {
        return newsAlert != null ||
                (timeToNewsMinutes != null && timeToNewsMinutes <= 60);
    }

    /**
     * Calcule la force globale du breakout (0.0 à 1.0)
     */
    public BigDecimal calculateBreakoutStrength() {
        BigDecimal strength = BigDecimal.ZERO;

        // Volume confirmation (30%)
        if (Boolean.TRUE.equals(volumeConfirmation)) {
            strength = strength.add(BigDecimal.valueOf(0.30));
        }

        // Momentum strength (40%)
        if (momentumStrength != null) {
            strength = strength.add(momentumStrength.multiply(BigDecimal.valueOf(0.40)));
        }

        // News catalyst (20%)
        if (hasNewsCatalyst()) {
            strength = strength.add(BigDecimal.valueOf(0.20));
        }

        // Session timing (10%)
        if (isOptimalSessionTiming()) {
            strength = strength.add(BigDecimal.valueOf(0.10));
        }

        return strength.min(BigDecimal.ONE);
    }

    /**
     * Vérifie si le timing du breakout est optimal
     */
    public boolean isOptimalSessionTiming() {
        if (breakoutTimestamp == null) return false;

        int hour = breakoutTimestamp.getHour();

        return switch (breakoutSession) {
            case "LONDON" -> hour >= 7 && hour <= 9; // Première partie de Londres
            case "NEWYORK" -> hour >= 12 && hour <= 14; // Première partie de NY
            default -> false;
        };
    }

    /**
     * Enregistre un retest du niveau cassé
     */
    public void recordRetest(BigDecimal testPrice, boolean held) {
        this.retestOccurred = true;
        this.retestTimestamp = LocalDateTime.now();
        this.retestPrice = testPrice;
        this.retestHeld = held;
    }

    /**
     * Met à jour le follow-through maximum
     */
    public void updateMaxFollowThrough(BigDecimal currentPrice) {
        if (breakoutPrice == null || currentPrice == null) return;

        BigDecimal movement = breakoutDirection == BreakoutDirection.LONG ?
                currentPrice.subtract(breakoutPrice) :
                breakoutPrice.subtract(currentPrice);

        int pips = movement.multiply(BigDecimal.valueOf(10000)).intValue();

        if (maxFollowThroughPips == null || pips > maxFollowThroughPips) {
            maxFollowThroughPips = pips;
            maxFollowThroughTime = LocalDateTime.now();
        }
    }

    /**
     * Génère une description du setup pour l'IA
     */
    public String generateSetupDescription() {
        StringBuilder desc = new StringBuilder();

        desc.append(String.format("%s %s breakout at %s session",
                symbol, breakoutDirection.name().toLowerCase(), breakoutSession));

        if (brokenLevel != null) {
            desc.append(String.format(" (broke %s from %s session)",
                    brokenLevel.getLevelType(), originSession));
        }

        if (Boolean.TRUE.equals(volumeConfirmation)) {
            desc.append(" with volume confirmation");
        }

        if (hasNewsCatalyst()) {
            desc.append(" near news event");
        }

        return desc.toString();
    }

    /**
     * Calcule la performance du breakout (success rate historique)
     */
    public String getPerformanceCategory() {
        BigDecimal strength = calculateBreakoutStrength();

        if (strength.compareTo(BigDecimal.valueOf(0.8)) >= 0) {
            return "HIGH_PROBABILITY";
        } else if (strength.compareTo(BigDecimal.valueOf(0.6)) >= 0) {
            return "MEDIUM_PROBABILITY";
        } else {
            return "LOW_PROBABILITY";
        }
    }

    /**
     * Vérifie si le breakout est encore valide (pas encore retesté négativement)
     */
    public boolean isStillValid() {
        return !Boolean.TRUE.equals(retestOccurred) || Boolean.TRUE.equals(retestHeld);
    }

    /**
     * Calcule le temps écoulé depuis le breakout
     */
    public long getMinutesSinceBreakout() {
        if (breakoutTimestamp == null) return 0;
        return java.time.Duration.between(breakoutTimestamp, LocalDateTime.now()).toMinutes();
    }

    /**
     * Retourne le contexte enrichi pour l'analyse IA
     */
    public java.util.Map<String, Object> getAIContext() {
        java.util.Map<String, Object> context = new java.util.HashMap<>();

        context.put("setup_type", String.format("%s_BREAKOUT_AT_%s", originSession, breakoutSession));
        context.put("breakout_strength", calculateBreakoutStrength());
        context.put("performance_category", getPerformanceCategory());
        context.put("technical_confirmation", isTechnicallyConfirmed());
        context.put("news_context", hasNewsCatalyst());
        context.put("session_timing", isOptimalSessionTiming());
        context.put("setup_description", generateSetupDescription());
        context.put("minutes_since_breakout", getMinutesSinceBreakout());
        context.put("still_valid", isStillValid());

        if (maxFollowThroughPips != null) {
            context.put("max_follow_through_pips", maxFollowThroughPips);
        }

        if (Boolean.TRUE.equals(retestOccurred)) {
            context.put("retest_outcome", Boolean.TRUE.equals(retestHeld) ? "HELD" : "FAILED");
        }

        return context;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}