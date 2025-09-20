package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entité représentant un niveau intraday (Asia_High, London_Low, VWAP, etc.)
 * avec tracking des cassures et tests
 */
@Entity
@Table(name = "intraday_levels",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "level_type", "session_id"}),
        indexes = {
                @Index(name = "idx_intraday_levels_symbol_status", columnList = "symbol, status"),
                @Index(name = "idx_intraday_levels_level_type", columnList = "level_type"),
                @Index(name = "idx_intraday_levels_establishment_time", columnList = "establishment_time DESC"),
                @Index(name = "idx_intraday_levels_importance", columnList = "importance_score DESC")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"symbol", "levelType", "session"})
@ToString(exclude = {"session", "sessionBreakouts"})
public class IntradayLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "level_type", nullable = false, length = 30)
    private LevelType levelType;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "price", nullable = false, precision = 10, scale = 5)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", foreignKey = @ForeignKey(name = "fk_intraday_level_session"))
    private TradingSession session;

    @NotNull
    @Column(name = "establishment_time", nullable = false)
    private LocalDateTime establishmentTime;

    // Force et fiabilité du niveau
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "importance_score", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal importanceScore = BigDecimal.valueOf(0.50);

    @Min(1)
    @Column(name = "touch_count")
    @Builder.Default
    private Integer touchCount = 1;

    @Min(0)
    @Column(name = "max_rejection_pips")
    @Builder.Default
    private Integer maxRejectionPips = 0;

    @Min(0)
    @Column(name = "volume_at_establishment")
    @Builder.Default
    private Long volumeAtEstablishment = 0L;

    // État du niveau
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private LevelStatus status = LevelStatus.ACTIVE;

    @Column(name = "broken_at")
    private LocalDateTime brokenAt;

    @Size(max = 10)
    @Column(name = "broken_by_session", length = 10)
    private String brokenBySession;

    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "broken_price", precision = 10, scale = 5)
    private BigDecimal brokenPrice;

    @Min(0)
    @Column(name = "retest_count")
    @Builder.Default
    private Integer retestCount = 0;

    @Column(name = "last_retest_time")
    private LocalDateTime lastRetestTime;

    // Prédictions IA
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "break_probability", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal breakProbability = BigDecimal.valueOf(0.50);

    @Column(name = "next_test_prediction")
    private LocalDateTime nextTestPrediction;

    // Métadonnées pour contexte IA
    @Size(max = 500)
    @Column(name = "level_context", length = 500)
    private String levelContext;

    @Size(max = 100)
    @Column(name = "break_catalyst", length = 100)
    private String breakCatalyst;

    // Timestamps d'audit
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relations
    @OneToMany(mappedBy = "brokenLevel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SessionBreakout> sessionBreakouts;

    // Énumérations
    public enum LevelType {
        ASIA_HIGH, ASIA_LOW,
        LONDON_HIGH, LONDON_LOW,
        NY_HIGH, NY_LOW,
        VWAP_ASIA, VWAP_LONDON, VWAP_NY,
        PIVOT_DAILY, ROUND_NUMBER,
        PREVIOUS_DAY_HIGH, PREVIOUS_DAY_LOW,
        WEEKLY_HIGH, WEEKLY_LOW
    }

    public enum LevelStatus {
        ACTIVE, BROKEN, RETESTED, WEAKENED, INACTIVE
    }

    // Méthodes métier

    /**
     * Vérifie si le niveau est proche d'un prix donné (en pips)
     */
    public boolean isPriceNear(BigDecimal currentPrice, int tolerancePips) {
        if (currentPrice == null) return false;

        BigDecimal tolerance = BigDecimal.valueOf(tolerancePips).divide(BigDecimal.valueOf(10000));
        BigDecimal diff = currentPrice.subtract(price).abs();

        return diff.compareTo(tolerance) <= 0;
    }

    /**
     * Calcule la distance en pips par rapport à ce niveau
     */
    public Integer getDistanceInPips(BigDecimal currentPrice) {
        if (currentPrice == null) return null;

        return currentPrice.subtract(price)
                .multiply(BigDecimal.valueOf(10000))
                .abs()
                .intValue();
    }

    /**
     * Détermine si le prix a cassé ce niveau
     */
    public boolean isPriceBroken(BigDecimal currentPrice, String direction) {
        if (currentPrice == null || direction == null) return false;

        if ("LONG".equals(direction)) {
            return currentPrice.compareTo(price) > 0;
        } else if ("SHORT".equals(direction)) {
            return currentPrice.compareTo(price) < 0;
        }

        return false;
    }

    /**
     * Marque le niveau comme cassé
     */
    public void markAsBroken(BigDecimal breakPrice, String breakSession, LocalDateTime breakTime) {
        this.status = LevelStatus.BROKEN;
        this.brokenPrice = breakPrice;
        this.brokenBySession = breakSession;
        this.brokenAt = breakTime;
    }

    /**
     * Ajoute un retest à ce niveau
     */
    public void addRetest() {
        this.retestCount++;
        this.lastRetestTime = LocalDateTime.now();
        this.status = LevelStatus.RETESTED;
    }

    /**
     * Évalue la force du niveau basée sur les métriques
     */
    public BigDecimal calculateLevelStrength() {
        BigDecimal base = importanceScore;

        // Bonus pour les tests multiples (jusqu'à +0.2)
        BigDecimal testBonus = BigDecimal.valueOf(Math.min(touchCount * 0.05, 0.2));

        // Bonus pour les rejections fortes (jusqu'à +0.1)
        BigDecimal rejectionBonus = BigDecimal.valueOf(Math.min(maxRejectionPips * 0.001, 0.1));

        return base.add(testBonus).add(rejectionBonus).min(BigDecimal.ONE);
    }

    /**
     * Génère une description contextuelle pour l'IA
     */
    public String generateContextDescription() {
        StringBuilder context = new StringBuilder();

        context.append(levelType.toString().replace("_", " ")).append(" level at ").append(price);
        context.append(" (").append(touchCount).append(" touches, ");
        context.append("importance: ").append(importanceScore).append(")");

        if (retestCount > 0) {
            context.append(", retested ").append(retestCount).append(" times");
        }

        if (status == LevelStatus.BROKEN) {
            context.append(", BROKEN by ").append(brokenBySession).append(" session");
        }

        return context.toString();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}