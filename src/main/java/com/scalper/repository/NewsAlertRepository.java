package com.scalper.repository;

import com.scalper.model.entity.NewsAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les alertes news avec filtrage et analyse contextuelle
 */
@Repository
public interface NewsAlertRepository extends JpaRepository<NewsAlert, Long> {

    /**
     * Récupère les news non traitées
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.processed = false ORDER BY na.eventTime")
    List<NewsAlert> findUnprocessedNews();

    /**
     * Trouve les news HIGH impact récentes
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findRecentHighImpactNews(@Param("since") LocalDateTime since);

    /**
     * Récupère les news pour une devise spécifique
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.currency = :currency AND na.eventTime >= :since ORDER BY na.eventTime DESC")
    List<NewsAlert> findNewsByCurrency(
            @Param("currency") String currency,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les news HIGH impact pour EUR et USD
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.currency IN ('EUR', 'USD') AND na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findMajorCurrencyHighImpactNews(@Param("since") LocalDateTime since);

    /**
     * Récupère les news dans une fenêtre temporelle
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.eventTime BETWEEN :startTime AND :endTime ORDER BY na.eventTime")
    List<NewsAlert> findNewsInTimeWindow(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Trouve les news proches d'un moment donné
     */
    @Query(value = """
    SELECT * FROM news_alerts na 
    WHERE na.impact_level = 'HIGH' 
    AND ABS(EXTRACT(EPOCH FROM (na.event_time - :targetTime))) <= :toleranceMinutes * 60 
    ORDER BY ABS(EXTRACT(EPOCH FROM (na.event_time - :targetTime)))
""", nativeQuery = true)
    List<NewsAlert> findNewsNearTime(
            @Param("targetTime") LocalDateTime targetTime,
            @Param("toleranceMinutes") int toleranceMinutes
    );

    /**
     * Récupère les prochaines news importantes
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.eventTime > :now AND na.impactLevel = 'HIGH' ORDER BY na.eventTime LIMIT :limit")
    List<NewsAlert> findUpcomingHighImpactNews(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * Trouve les news par source
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.source = :source AND na.eventTime >= :since ORDER BY na.eventTime DESC")
    List<NewsAlert> findNewsBySource(
            @Param("source") String source,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les news avec contexte de marché spécifique
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.marketContext = :context AND na.eventTime >= :since ORDER BY na.eventTime DESC")
    List<NewsAlert> findNewsByMarketContext(
            @Param("context") NewsAlert.MarketContext context,
            @Param("since") LocalDateTime since
    );

    /**
     * Statistiques des news par devise et impact
     */
    @Query("""
        SELECT new map(
            na.currency as currency,
            na.impactLevel as impact,
            COUNT(na) as count,
            AVG(CASE WHEN na.actualValue IS NOT NULL AND na.forecastValue IS NOT NULL THEN 1.0 ELSE 0.0 END) as dataCompleteness
        )
        FROM NewsAlert na 
        WHERE na.eventTime >= :since 
        GROUP BY na.currency, na.impactLevel
        ORDER BY na.currency, na.impactLevel
    """)
    List<java.util.Map<String, Object>> getNewsStatisticsByCurrencyAndImpact(@Param("since") LocalDateTime since);

    /**
     * Trouve les news avec surprises (actual != forecast)
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.actualValue IS NOT NULL AND na.forecastValue IS NOT NULL AND na.actualValue != na.forecastValue AND na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime DESC")
    List<NewsAlert> findNewsSurprises(@Param("since") LocalDateTime since);

    /**
     * Récupère les news durant les sessions de trading spécifiques
     */
    @Query("SELECT na FROM NewsAlert na WHERE HOUR(na.eventTime) BETWEEN :startHour AND :endHour AND na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findNewsDuringSession(
            @Param("startHour") int startHour,
            @Param("endHour") int endHour,
            @Param("since") LocalDateTime since
    );

    /**
     * News pendant session Londres (7h-11h UTC)
     */
    @Query("SELECT na FROM NewsAlert na WHERE HOUR(na.eventTime) BETWEEN 7 AND 11 AND na.currency IN ('EUR', 'GBP') AND na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findLondonSessionNews(@Param("since") LocalDateTime since);

    /**
     * News pendant session NY (12h-16h UTC)
     */
    @Query("SELECT na FROM NewsAlert na WHERE HOUR(na.eventTime) BETWEEN 12 AND 16 AND na.currency = 'USD' AND na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findNYSessionNews(@Param("since") LocalDateTime since);

    /**
     * Compte les news par niveau d'impact sur une période
     */
    @Query("SELECT COUNT(na) FROM NewsAlert na WHERE na.impactLevel = :impact AND na.eventTime >= :since")
    Long countNewsByImpact(
            @Param("impact") NewsAlert.ImpactLevel impact,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les événements récurrents (même titre)
     */
    @Query("SELECT na FROM NewsAlert na WHERE LOWER(na.eventTitle) LIKE LOWER(CONCAT('%', :titleKeyword, '%')) AND na.eventTime >= :since ORDER BY na.eventTime DESC")
    List<NewsAlert> findRecurringEvents(
            @Param("titleKeyword") String titleKeyword,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les news avec données complètes (actual + forecast)
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.actualValue IS NOT NULL AND na.forecastValue IS NOT NULL AND na.currency = :currency AND na.eventTime >= :since ORDER BY na.eventTime DESC")
    List<NewsAlert> findCompleteNewsData(
            @Param("currency") String currency,
            @Param("since") LocalDateTime since
    );

    /**
     * Distribution temporelle des news HIGH impact
     */
    @Query("""
        SELECT new map(
            HOUR(na.eventTime) as hour,
            COUNT(na) as count,
            na.currency as currency
        )
        FROM NewsAlert na 
        WHERE na.impactLevel = 'HIGH' AND na.eventTime >= :since 
        GROUP BY HOUR(na.eventTime), na.currency
        ORDER BY HOUR(na.eventTime), na.currency
    """)
    List<java.util.Map<String, Object>> getNewsTimeDistribution(@Param("since") LocalDateTime since);

    /**
     * Trouve les news pouvant servir de catalyseur pour breakouts
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.currency IN ('EUR', 'USD') AND na.impactLevel = 'HIGH' AND HOUR(na.eventTime) BETWEEN 7 AND 16 AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findBreakoutCatalystCandidates(@Param("since") LocalDateTime since);

    /**
     * Récupère les dernières news traitées
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.processed = true ORDER BY na.receivedAt DESC LIMIT :limit")
    List<NewsAlert> findLastProcessedNews(@Param("limit") int limit);

    /**
     * Analyse de corrélation entre news et sentiment de marché
     */
    @Query("""
        SELECT new map(
            na.currency as currency,
            na.marketContext as marketContext,
            COUNT(na) as count,
            SUM(CASE WHEN na.actualValue IS NOT NULL AND na.forecastValue IS NOT NULL THEN 1 ELSE 0 END) as withData
        )
        FROM NewsAlert na 
        WHERE na.impactLevel = 'HIGH' AND na.eventTime >= :since 
        GROUP BY na.currency, na.marketContext
        ORDER BY na.currency, COUNT(na) DESC
    """)
    List<java.util.Map<String, Object>> analyzeNewsMarketContextCorrelation(@Param("since") LocalDateTime since);

    /**
     * Trouve les news avec impact sur EURUSD
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.currency IN ('EUR', 'USD') AND na.impactLevel = 'HIGH' AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findEURUSDImpactNews(@Param("since") LocalDateTime since);

    /**
     * Vérifie s'il y a des news HIGH impact dans une fenêtre
     */
    @Query("SELECT COUNT(na) > 0 FROM NewsAlert na WHERE na.impactLevel = 'HIGH' AND na.eventTime BETWEEN :startTime AND :endTime")
    boolean hasHighImpactNewsInWindow(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Récupère la prochaine news HIGH impact pour une devise
     */
    @Query("SELECT na FROM NewsAlert na WHERE na.currency = :currency AND na.impactLevel = 'HIGH' AND na.eventTime > :now ORDER BY na.eventTime LIMIT 1")
    Optional<NewsAlert> findNextHighImpactNewsForCurrency(
            @Param("currency") String currency,
            @Param("now") LocalDateTime now
    );

    /**
     * Trouve les news événements majeurs (NFP, CPI, FOMC, etc.)
     */
    @Query("SELECT na FROM NewsAlert na WHERE (LOWER(na.eventTitle) LIKE '%nfp%' OR LOWER(na.eventTitle) LIKE '%payroll%' OR LOWER(na.eventTitle) LIKE '%cpi%' OR LOWER(na.eventTitle) LIKE '%fomc%' OR LOWER(na.eventTitle) LIKE '%interest rate%') AND na.eventTime >= :since ORDER BY na.eventTime")
    List<NewsAlert> findMajorEconomicEvents(@Param("since") LocalDateTime since);

    /**
     * Analyse de fréquence des news par source
     */
    @Query("""
        SELECT new map(
            na.source as source,
            COUNT(na) as totalNews,
            SUM(CASE WHEN na.impactLevel = 'HIGH' THEN 1 ELSE 0 END) as highImpactNews,
            AVG(CASE WHEN na.processed = true THEN 1.0 ELSE 0.0 END) as processingRate
        )
        FROM NewsAlert na 
        WHERE na.eventTime >= :since 
        GROUP BY na.source
        ORDER BY COUNT(na) DESC
    """)
    List<java.util.Map<String, Object>> getNewsSourceAnalysis(@Param("since") LocalDateTime since);

    /**
     * Marque les news comme traitées en batch
     */
    @Query("UPDATE NewsAlert na SET na.processed = true WHERE na.id IN :ids")
    void markNewsAsProcessed(@Param("ids") List<Long> ids);

    /**
     * Supprime les anciennes news pour maintenance
     */
    @Query("DELETE FROM NewsAlert na WHERE na.eventTime < :cutoffDate AND na.processed = true")
    void cleanupOldNews(@Param("cutoffDate") LocalDateTime cutoffDate);
}