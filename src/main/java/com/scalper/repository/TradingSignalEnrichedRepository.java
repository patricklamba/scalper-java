package com.scalper.repository;

import com.scalper.model.entity.TradingSignalEnriched;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les signaux de trading enrichis avec analyses de performance
 */
@Repository
public interface TradingSignalEnrichedRepository extends JpaRepository<TradingSignalEnriched, Long> {

    /**
     * Récupère les signaux actifs pour un symbole
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.status = 'ACTIVE' ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findActiveSignals(@Param("symbol") String symbol);

    /**
     * Récupère les derniers signaux pour un symbole
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol ORDER BY tse.createdAt DESC LIMIT :limit")
    List<TradingSignalEnriched> findLatestSignals(
            @Param("symbol") String symbol,
            @Param("limit") int limit
    );

    /**
     * Trouve les signaux par type de setup
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.setupType = :setupType AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findSignalsBySetupType(
            @Param("symbol") String symbol,
            @Param("setupType") String setupType,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux par catégorie
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.setupCategory = :category AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findSignalsByCategory(
            @Param("symbol") String symbol,
            @Param("category") TradingSignalEnriched.SetupCategory category,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux de haute qualité
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.confidenceScore >= :minConfidence AND tse.riskRewardRatio >= :minRR AND tse.createdAt >= :since ORDER BY tse.confidenceScore DESC")
    List<TradingSignalEnriched> findHighQualitySignals(
            @Param("symbol") String symbol,
            @Param("minConfidence") BigDecimal minConfidence,
            @Param("minRR") BigDecimal minRR,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux par session
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.primarySession = :session AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findSignalsBySession(
            @Param("symbol") String symbol,
            @Param("session") String session,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux breakout Asia → London
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.setupType = 'ASIA_BREAKOUT_AT_LONDON' AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findAsiaBreakoutSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux retest London → NY
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.setupType = 'LONDON_RETEST_AT_NY' AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findLondonRetestSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux avec contexte news
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.newsContext != 'CLEAR' AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findNewsInfluencedSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Statistiques de performance par type de setup
     */
    @Query("""
        SELECT new map(
            tse.setupType as setupType,
            tse.signalDirection as direction,
            COUNT(tse) as totalCount,
            AVG(tse.confidenceScore) as avgConfidence,
            AVG(tse.riskRewardRatio) as avgRiskReward,
            SUM(CASE WHEN tse.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedCount
        )
        FROM TradingSignalEnriched tse 
        WHERE tse.symbol = :symbol AND tse.createdAt >= :since 
        GROUP BY tse.setupType, tse.signalDirection
    """)
    List<java.util.Map<String, Object>> getSetupPerformanceStats(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux par statut
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.status = :status AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findSignalsByStatus(
            @Param("symbol") String symbol,
            @Param("status") TradingSignalEnriched.SignalStatus status,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux avec probabilité de succès élevée
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.probabilitySuccess >= :minProbability AND tse.createdAt >= :since ORDER BY tse.probabilitySuccess DESC")
    List<TradingSignalEnriched> findHighProbabilitySignals(
            @Param("symbol") String symbol,
            @Param("minProbability") BigDecimal minProbability,
            @Param("since") LocalDateTime since
    );

    /**
     * Analyse de distribution temporelle des signaux
     */
    @Query("""
        SELECT new map(
            HOUR(tse.createdAt) as hour,
            COUNT(tse) as count,
            AVG(tse.confidenceScore) as avgConfidence,
            tse.primarySession as session
        )
        FROM TradingSignalEnriched tse 
        WHERE tse.symbol = :symbol AND tse.createdAt >= :since 
        GROUP BY HOUR(tse.createdAt), tse.primarySession
        ORDER BY HOUR(tse.createdAt)
    """)
    List<java.util.Map<String, Object>> getSignalTimeDistribution(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux avec screenshots disponibles
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.screenshotPath IS NOT NULL AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findSignalsWithScreenshots(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux envoyés à Telegram
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.telegramMessageId IS NOT NULL AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findTelegramSentSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Calcule les métriques de qualité par session
     */
    @Query("""
        SELECT new map(
            tse.primarySession as session,
            COUNT(tse) as totalSignals,
            AVG(tse.confidenceScore) as avgConfidence,
            AVG(tse.riskRewardRatio) as avgRiskReward,
            SUM(CASE WHEN tse.confidenceScore >= 0.8 THEN 1 ELSE 0 END) as highConfidenceCount
        )
        FROM TradingSignalEnriched tse 
        WHERE tse.symbol = :symbol AND tse.createdAt >= :since 
        GROUP BY tse.primarySession
    """)
    List<java.util.Map<String, Object>> getQualityMetricsBySession(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux liés à des breakouts spécifiques
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.relatedBreakout IS NOT NULL AND tse.symbol = :symbol AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findBreakoutLinkedSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux liés à des niveaux spécifiques
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.relatedLevel IS NOT NULL AND tse.symbol = :symbol AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findLevelLinkedSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Analyse de corrélation confidence vs performance
     */
    @Query("""
        SELECT new map(
            CASE 
                WHEN tse.confidenceScore >= 0.8 THEN 'HIGH'
                WHEN tse.confidenceScore >= 0.6 THEN 'MEDIUM'
                ELSE 'LOW'
            END as confidenceCategory,
            COUNT(tse) as count,
            AVG(tse.riskRewardRatio) as avgRiskReward,
            SUM(CASE WHEN tse.status = 'COMPLETED' THEN 1 ELSE 0 END) as successCount
        )
        FROM TradingSignalEnriched tse 
        WHERE tse.symbol = :symbol AND tse.createdAt >= :since 
        GROUP BY CASE 
            WHEN tse.confidenceScore >= 0.8 THEN 'HIGH'
            WHEN tse.confidenceScore >= 0.6 THEN 'MEDIUM'
            ELSE 'LOW'
        END
    """)
    List<java.util.Map<String, Object>> analyzeConfidencePerformanceCorrelation(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve le dernier signal pour un breakout spécifique
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.relatedBreakout.id = :breakoutId ORDER BY tse.createdAt DESC LIMIT 1")
    Optional<TradingSignalEnriched> findLastSignalForBreakout(@Param("breakoutId") Long breakoutId);

    /**
     * Récupère les signaux par qualité de session
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.sessionQuality = :quality AND tse.createdAt >= :since ORDER BY tse.createdAt DESC")
    List<TradingSignalEnriched> findSignalsBySessionQuality(
            @Param("symbol") String symbol,
            @Param("quality") com.scalper.model.entity.TradingSession.SessionQuality quality,
            @Param("since") LocalDateTime since
    );

    /**
     * Compte les signaux par direction et session
     */
    @Query("""
        SELECT new map(
            tse.primarySession as session,
            tse.signalDirection as direction,
            COUNT(tse) as count
        )
        FROM TradingSignalEnriched tse 
        WHERE tse.symbol = :symbol AND tse.createdAt >= :since 
        GROUP BY tse.primarySession, tse.signalDirection
    """)
    List<java.util.Map<String, Object>> countSignalsByDirectionAndSession(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les signaux avec risk/reward exceptionnel
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.riskRewardRatio >= :minRR AND tse.confidenceScore >= :minConfidence AND tse.createdAt >= :since ORDER BY tse.riskRewardRatio DESC")
    List<TradingSignalEnriched> findExceptionalRiskRewardSignals(
            @Param("symbol") String symbol,
            @Param("minRR") BigDecimal minRR,
            @Param("minConfidence") BigDecimal minConfidence,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les signaux durant une fenêtre temporelle
     */
    @Query("SELECT tse FROM TradingSignalEnriched tse WHERE tse.symbol = :symbol AND tse.createdAt BETWEEN :startTime AND :endTime ORDER BY tse.createdAt")
    List<TradingSignalEnriched> findSignalsInTimeWindow(
            @Param("symbol") String symbol,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Analyse des patterns de signaux récurrents
     */
    @Query("""
        SELECT new map(
            tse.setupType as setupType,
            tse.primarySession as session,
            COUNT(tse) as frequency,
            AVG(tse.confidenceScore) as avgConfidence,
            AVG(tse.riskRewardRatio) as avgRiskReward
        )
        FROM TradingSignalEnriched tse 
        WHERE tse.symbol = :symbol AND tse.createdAt >= :since 
        GROUP BY tse.setupType, tse.primarySession
        HAVING COUNT(tse) >= :minOccurrences
        ORDER BY COUNT(tse) DESC
    """)
    List<java.util.Map<String, Object>> findRecurringSignalPatterns(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since,
            @Param("minOccurrences") Long minOccurrences
    );
}