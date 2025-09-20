package com.scalper.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entité représentant une alerte news économique reçue de n8n
 */
@Entity
@Table(name = "news_alerts",
        indexes = {
                @Index(name = "idx_news_alerts_time", columnList = "event_time DESC"),
                @Index(name = "idx_news_alerts_currency", columnList = "currency"),
                @Index(name = "idx_news_alerts_impact", columnList = "impact_level"),
                @Index(name = "idx_news_alerts_received", columnList = "received_at DESC"),
                @Index(name = "idx_news_alerts_processed", columnList = "processed")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"source", "eventTime", "eventTitle"})
@ToString(exclude = {"sessionBreakouts", "tradingSignals"})
public class NewsAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "source", nullable = false, length = 50)
    private String source; // ForexFactory, Investing.com, etc.

    @NotNull
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @NotBlank
    @Size(max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency; // EUR, USD, XAU

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 10)
    private ImpactLevel impactLevel;

    @NotBlank
    @Size(max = 200)
    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    @Size(max = 20)
    @Column(name = "actual_value", length = 20)
    private String actualValue;

    @Size(max = 20)
    @Column(name = "forecast_value", length = 20)
    private String forecastValue;

    @Size(max = 20)
    @Column(name = "previous_value", length = 20)
    private String previousValue;

    // Context pour pattern filtering
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "market_context", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'NEUTRAL'")
    private MarketContext marketContext = MarketContext.NEUTRAL;

    @CreatedDate
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Builder.Default
    @Column(name = "processed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean processed = false;

    // Relations
    @OneToMany(mappedBy = "newsAlert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.Set<SessionBreakout> sessionBreakouts;

    @OneToMany(mappedBy = "newsAlert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.Set<TradingSignalEnriched> tradingSignals;

    /**
     * Niveau d'impact de la news
     */
    public enum ImpactLevel {
        LOW, MEDIUM, HIGH
    }

    /**
     * Contexte de marché de la news
     */
    public enum MarketContext {
        BULLISH_USD, BEARISH_USD,
        BULLISH_EUR, BEARISH_EUR,
        RISK_ON, RISK_OFF,
        NEUTRAL
    }

    /**
     * Vérifie si la news est de type HIGH impact
     */
    public boolean isHighImpact() {
        return impactLevel == ImpactLevel.HIGH;
    }

    /**
     * Calcule le temps jusqu'à l'événement
     */
    public long getMinutesUntilEvent() {
        if (eventTime == null) return 0;
        return java.time.Duration.between(LocalDateTime.now(), eventTime).toMinutes();
    }

    /**
     * Calcule le temps écoulé depuis l'événement
     */
    public long getMinutesSinceEvent() {
        if (eventTime == null) return 0;
        return java.time.Duration.between(eventTime, LocalDateTime.now()).toMinutes();
    }

    /**
     * Vérifie si l'événement est dans une fenêtre de temps critique
     */
    public boolean isInCriticalWindow(int beforeMinutes, int afterMinutes) {
        long minutesUntil = getMinutesUntilEvent();
        long minutesSince = getMinutesSinceEvent();

        if (minutesUntil > 0) {
            return minutesUntil <= beforeMinutes;
        } else {
            return minutesSince <= afterMinutes;
        }
    }

    /**
     * Détermine la session de trading appropriée pour cette news
     */
    public String getTargetTradingSession() {
        if (eventTime == null) return null;

        int hour = eventTime.getHour();

        if (hour >= 0 && hour < 6) {
            return "ASIA";
        } else if (hour >= 7 && hour < 12) {
            return "LONDON";
        } else if (hour >= 12 && hour < 17) {
            return "NEWYORK";
        } else {
            return "AFTER_HOURS";
        }
    }

    /**
     * Évalue l'impact potentiel sur les patterns de trading
     */
    public String evaluatePatternImpact() {
        if (!isHighImpact()) {
            return "MINIMAL";
        }

        // News USD pendant session NY = impact fort
        if ("USD".equals(currency) && "NEWYORK".equals(getTargetTradingSession())) {
            return "HIGH";
        }

        // News EUR pendant session London = impact fort
        if ("EUR".equals(currency) && "LONDON".equals(getTargetTradingSession())) {
            return "HIGH";
        }

        // News importantes hors session principale = impact modéré
        return "MEDIUM";
    }

    /**
     * Vérifie si la news confirme ou contredit un bias de marché
     */
    public boolean isMarketContextAligned(String expectedDirection) {
        if (marketContext == MarketContext.NEUTRAL) return true;

        return switch (expectedDirection.toUpperCase()) {
            case "BULLISH_USD", "USD_STRENGTH" ->
                    marketContext == MarketContext.BULLISH_USD || marketContext == MarketContext.BEARISH_EUR;
            case "BEARISH_USD", "USD_WEAKNESS" ->
                    marketContext == MarketContext.BEARISH_USD || marketContext == MarketContext.BULLISH_EUR;
            case "BULLISH_EUR", "EUR_STRENGTH" ->
                    marketContext == MarketContext.BULLISH_EUR || marketContext == MarketContext.BEARISH_USD;
            case "BEARISH_EUR", "EUR_WEAKNESS" ->
                    marketContext == MarketContext.BEARISH_EUR || marketContext == MarketContext.BULLISH_USD;
            default -> true;
        };
    }

    /**
     * Génère une description courte pour l'IA
     */
    public String generateShortDescription() {
        StringBuilder desc = new StringBuilder();

        desc.append(String.format("%s %s", currency, impactLevel));

        if (eventTitle.length() > 30) {
            desc.append(String.format(": %s...", eventTitle.substring(0, 27)));
        } else {
            desc.append(String.format(": %s", eventTitle));
        }

        if (actualValue != null && forecastValue != null && !actualValue.equals(forecastValue)) {
            desc.append(" (surprise)");
        }

        return desc.toString();
    }

    /**
     * Détermine le sentiment de la news basé sur actual vs forecast
     */
    public String determineNewsSentiment() {
        if (actualValue == null || forecastValue == null) {
            return "NEUTRAL";
        }

        try {
            double actual = Double.parseDouble(actualValue.replace("%", ""));
            double forecast = Double.parseDouble(forecastValue.replace("%", ""));

            if (actual > forecast) {
                return currency.equals("USD") ? "USD_POSITIVE" : "CURRENCY_POSITIVE";
            } else if (actual < forecast) {
                return currency.equals("USD") ? "USD_NEGATIVE" : "CURRENCY_NEGATIVE";
            } else {
                return "NEUTRAL";
            }
        } catch (NumberFormatException e) {
            return "NEUTRAL";
        }
    }

    /**
     * Vérifie si cette news peut servir de catalyseur pour un breakout
     */
    public boolean canActAsBreakoutCatalyst() {
        return isHighImpact() &&
                ("USD".equals(currency) || "EUR".equals(currency)) &&
                !getTargetTradingSession().equals("AFTER_HOURS");
    }

    /**
     * Marque la news comme traitée
     */
    public void markAsProcessed() {
        this.processed = true;
    }

    /**
     * Retourne le contexte enrichi pour l'IA
     */
    public java.util.Map<String, Object> getAIContext() {
        java.util.Map<String, Object> context = new java.util.HashMap<>();

        context.put("source", source);
        context.put("currency", currency);
        context.put("impact_level", impactLevel.toString());
        context.put("event_title", eventTitle);
        context.put("target_session", getTargetTradingSession());
        context.put("pattern_impact", evaluatePatternImpact());
        context.put("news_sentiment", determineNewsSentiment());
        context.put("can_catalyze_breakout", canActAsBreakoutCatalyst());
        context.put("short_description", generateShortDescription());

        if (actualValue != null) context.put("actual_value", actualValue);
        if (forecastValue != null) context.put("forecast_value", forecastValue);
        if (previousValue != null) context.put("previous_value", previousValue);

        context.put("minutes_until_event", getMinutesUntilEvent());
        context.put("minutes_since_event", getMinutesSinceEvent());

        return context;
    }

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}