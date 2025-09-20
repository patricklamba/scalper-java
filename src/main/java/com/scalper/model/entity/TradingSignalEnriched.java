package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entité représentant un signal de trading enrichi avec contexte multi-sessions complet
 */
@Entity
@Table(name = "trading_signals_enriched",
        indexes = {
                @Index(name = "idx_trading_signals_enriched_created", columnList = "created_at DESC"),
                @Index(name = "idx_trading_signals_enriched_setup_type", columnList = "setup_type"),
                @Index(name = "idx_trading_signals_enriched_symbol", columnList = "symbol"),
                @Index(name = "idx_trading_signals_enriched_status", columnList = "status"),
                @Index(name = "idx_trading_signals_enriched_confidence", columnList = "confidence_score DESC")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"symbol", "createdAt", "setupType"})
@ToString(exclude = {"relatedLevel", "relatedBreakout", "newsAlert"})
public class TradingSignalEnriched {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    // Classification du setup
    @NotBlank
    @Size(max = 40)
    @Column(name = "setup_type", nullable = false, length = 40)
    private String setupType; // ASIA_BREAKOUT_AT_LONDON, LONDON_RETEST_AT_NY, etc.

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "setup_category", nullable = false, length = 20)
    private SetupCategory setupCategory;

    // Détails du trade
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_direction", nullable = false, length = 5)
    private SignalDirection signalDirection;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "entry_price", nullable = false, precision = 10, scale = 5)
    private BigDecimal entryPrice;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "stop_loss", nullable = false, precision = 10, scale = 5)
    private BigDecimal stopLoss;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 5)
    @Column(name = "take_profit_1", nullable = false, precision = 10, scale = 5)
    private BigDecimal takeProfit1;

    @Digits(integer = 5, fraction = 5)
    @Column(name = "take_profit_2", precision = 10, scale = 5)
    private BigDecimal takeProfit2;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 2, fraction = 2)
    @Column(name = "risk_reward_ratio", nullable = false, precision = 4, scale = 2)
    private BigDecimal riskRewardRatio;

    // Contexte multi-sessions
    @NotBlank
    @Size(max = 10)
    @Column(name = "primary_session", nullable = false, length = 10)
    private String primarySession; // Session de génération du signal

    @Size(max = 10)
    @Column(name = "origin_session", length = 10)
    private String originSession; // Session d'origine du niveau

    @Size(max = 200)
    @Column(name = "session_context", length = 200)
    private String sessionContext; // Description contexte session

    // Références aux niveaux et breakouts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_level_id", foreignKey = @ForeignKey(name = "fk_signal_level"))
    private IntradayLevel relatedLevel;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_breakout_id", foreignKey = @ForeignKey(name = "fk_signal_breakout"))
    private SessionBreakout relatedBreakout;

    // Contexte news et marché
    @Size(max = 50)
    @Column(name = "news_context", length = 50)
    private String newsContext; // CLEAR, BEFORE_HIGH_IMPACT, AFTER_NEWS, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "market_volatility", length = 10)
    private MarketDataSession.VolatilityLevel marketVolatility;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_quality", length = 15)
    private TradingSession.SessionQuality sessionQuality;

    // Métriques de confiance
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "confidence_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 2)
    @Column(name = "probability_success", precision = 3, scale = 2)
    private BigDecimal probabilitySuccess;

    // IA et explication
    @Size(max = 1000)
    @Column(name = "ai_explanation", length = 1000)
    private String aiExplanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_factors", columnDefinition = "jsonb")
    private Map<String, Object> keyFactors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private Map<String, Object> riskFactors;

    // Métadonnées
    @Size(max = 255)
    @Column(name = "screenshot_path", length = 255)
    private String screenshotPath;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 15, columnDefinition = "VARCHAR(15) DEFAULT 'ACTIVE'")
    private SignalStatus status = SignalStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_alert_id", foreignKey = @ForeignKey(name = "fk_signal_news"))
    private NewsAlert newsAlert;

    /**
     * Catégorie de setup
     */
    public enum SetupCategory {
        BREAKOUT, RETEST, BOUNCE, CONTINUATION
    }

    /**
     * Direction du signal
     */
    public enum SignalDirection {
        LONG, SHORT
    }

    /**
     * Statut du signal
     */
    public enum SignalStatus {
        ACTIVE, TRIGGERED, EXPIRED, CANCELLED, COMPLETED
    }

    /**
     * Calcule la distance au stop loss en pips
     */
    public Integer getStopLossDistancePips() {
        if (entryPrice == null || stopLoss == null) return null;

        return entryPrice.subtract(stopLoss)
                .abs()
                .multiply(BigDecimal.valueOf(10000))
                .intValue();
    }

    /**
     * Calcule la distance au take profit 1 en pips
     */
    public Integer getTakeProfit1DistancePips() {
        if (entryPrice == null || takeProfit1 == null) return null;

        return takeProfit1.subtract(entryPrice)
                .abs()
                .multiply(BigDecimal.valueOf(10000))
                .intValue();
    }

    /**
     * Vérifie si le signal a un take profit 2
     */
    public boolean hasSecondTarget() {
        return takeProfit2 != null;
    }

    /**
     * Calcule le risque en pourcentage du compte (basé sur 1% standard)
     */
    public BigDecimal calculateRiskPercent() {
        // Logique à implémenter avec la taille du compte
        return BigDecimal.valueOf(0.01); // 1% par défaut
    }

    /**
     * Vérifie si le signal est de haute qualité
     */
    public boolean isHighQualitySignal() {
        return confidenceScore != null &&
                confidenceScore.compareTo(BigDecimal.valueOf(0.8)) >= 0 &&
                riskRewardRatio != null &&
                riskRewardRatio.compareTo(BigDecimal.valueOf(1.5)) >= 0;
    }

    /**
     * Génère l'ID unique du signal pour tracking
     */
    public String generateSignalId() {
        if (createdAt == null) return null;

        String timestamp = createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s_%s_%s", symbol, setupType.replace("_", ""), timestamp);
    }

    /**
     * Génère le message Telegram formaté
     */
    public String generateTelegramMessage() {
        StringBuilder message = new StringBuilder();

        // Emoji basé sur la direction
        String emoji = signalDirection == SignalDirection.LONG ? "🚀" : "📉";

        message.append(String.format("%s %s SIGNAL DETECTED\n\n", emoji, setupType.replace("_", " ")));

        // Détails du trade
        message.append(String.format("📈 %s %s @ %.5f\n", symbol, signalDirection, entryPrice));
        message.append(String.format("🎯 Setup: %s\n\n", setupType.replace("_", " ")));

        // Contexte
        message.append("📊 Context:\n");
        if (sessionContext != null) {
            message.append(String.format("• %s\n", sessionContext));
        }
        if (newsContext != null && !newsContext.equals("CLEAR")) {
            message.append(String.format("• News: %s\n", newsContext.replace("_", " ")));
        }

        // Setup de trade
        message.append("\n⚙️ Trade Setup:\n");
        message.append(String.format("• Entry: %.5f\n", entryPrice));
        message.append(String.format("• Stop: %.5f (%d pips)\n", stopLoss, getStopLossDistancePips()));
        message.append(String.format("• TP1: %.5f (%d pips)\n", takeProfit1, getTakeProfit1DistancePips()));

        if (hasSecondTarget()) {
            Integer tp2Distance = takeProfit2.subtract(entryPrice).abs()
                    .multiply(BigDecimal.valueOf(10000)).intValue();
            message.append(String.format("• TP2: %.5f (%d pips)\n", takeProfit2, tp2Distance));
        }

        message.append(String.format("• R/R: %.1f:1\n", riskRewardRatio));

        // Analyse IA
        if (aiExplanation != null && !aiExplanation.isEmpty()) {
            message.append(String.format("\n🤖 AI Analysis:\n\"%s\"\n", aiExplanation));
        }

        // Confiance
        message.append(String.format("\n📊 Confidence: %.0f%%", confidenceScore.multiply(BigDecimal.valueOf(100))));

        return message.toString();
    }

    /**
     * Génère le contexte JSON pour l'IA externe
     */
    public Map<String, Object> generateAIContext() {
        Map<String, Object> context = new java.util.HashMap<>();

        // Informations de base
        context.put("signal_id", generateSignalId());
        context.put("setup_type", setupType);
        context.put("setup_category", setupCategory.toString());
        context.put("signal_direction", signalDirection.toString());
        context.put("confidence_score", confidenceScore);

        // Contexte multi-sessions
        context.put("primary_session", primarySession);
        context.put("origin_session", originSession);
        context.put("session_context", sessionContext);

        // Paramètres de trade
        Map<String, Object> tradeParams = new java.util.HashMap<>();
        tradeParams.put("entry_price", entryPrice);
        tradeParams.put("stop_loss", stopLoss);
        tradeParams.put("take_profit_1", takeProfit1);
        if (hasSecondTarget()) tradeParams.put("take_profit_2", takeProfit2);
        tradeParams.put("risk_reward_ratio", riskRewardRatio);
        tradeParams.put("stop_distance_pips", getStopLossDistancePips());
        tradeParams.put("tp1_distance_pips", getTakeProfit1DistancePips());
        context.put("trade_parameters", tradeParams);

        // Contexte de marché
        Map<String, Object> marketContext = new java.util.HashMap<>();
        marketContext.put("news_environment", newsContext);
        if (marketVolatility != null) marketContext.put("volatility", marketVolatility.toString());
        if (sessionQuality != null) marketContext.put("session_quality", sessionQuality.toString());
        context.put("market_context", marketContext);

        // Facteurs clés et risques
        if (keyFactors != null) context.put("key_success_factors", keyFactors);
        if (riskFactors != null) context.put("risk_factors", riskFactors);

        // Métadonnées
        context.put("signal_quality", isHighQualitySignal() ? "HIGH" : "MEDIUM");
        context.put("ai_explanation", aiExplanation);
        context.put("created_at", createdAt);

        return context;
    }

    /**
     * Met à jour le statut du signal
     */
    public void updateStatus(SignalStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * Ajoute un facteur de succès clé
     */
    public void addKeyFactor(String factor, Object value) {
        if (this.keyFactors == null) {
            this.keyFactors = new java.util.HashMap<>();
        }
        this.keyFactors.put(factor, value);
    }

    /**
     * Ajoute un facteur de risque
     */
    public void addRiskFactor(String factor, Object value) {
        if (this.riskFactors == null) {
            this.riskFactors = new java.util.HashMap<>();
        }
        this.riskFactors.put(factor, value);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // Génération automatique de l'explication si vide
        if (aiExplanation == null || aiExplanation.isEmpty()) {
            generateDefaultExplanation();
        }
    }

    /**
     * Génère une explication par défaut basée sur les données disponibles
     */
    private void generateDefaultExplanation() {
        StringBuilder explanation = new StringBuilder();

        explanation.append(String.format("%s setup on %s", setupType.replace("_", " "), symbol));

        if (sessionContext != null) {
            explanation.append(String.format(" during %s", sessionContext));
        }

        if (confidenceScore.compareTo(BigDecimal.valueOf(0.8)) >= 0) {
            explanation.append(". High confidence setup");
        } else if (confidenceScore.compareTo(BigDecimal.valueOf(0.6)) >= 0) {
            explanation.append(". Medium confidence setup");
        }

        if (riskRewardRatio.compareTo(BigDecimal.valueOf(2.0)) >= 0) {
            explanation.append(" with favorable risk/reward");
        }

        this.aiExplanation = explanation.toString();
    }
}