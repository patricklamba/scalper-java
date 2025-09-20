package com.scalper.repository;

import com.scalper.model.entity.SessionBreakout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les breakouts inter-sessions avec analyses de performance
 */
@Repository
public interface SessionBreakoutRepository extends JpaRepository<SessionBreakout, Long> {

    /**
     * Récupère les breakouts récents pour un symbole
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findRecentBreakouts(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts ASIA → LONDON
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.originSession = 'ASIA' AND sb.breakoutSession = 'LONDON' AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findAsiaToLondonBreakouts(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts LONDON → NY
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.originSession = 'LONDON' AND sb.breakoutSession = 'NEWYORK' AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findLondonToNYBreakouts(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les breakouts avec confirmation technique
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.volumeConfirmation = true AND sb.momentumStrength >= :minMomentum AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findTechnicallyConfirmedBreakouts(
            @Param("symbol") String symbol,
            @Param("minMomentum") java.math.BigDecimal minMomentum,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts avec catalyseur news
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND (sb.newsAlert IS NOT NULL OR sb.timeToNewsMinutes <= :maxMinutes) AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findNewsCatalyzedBreakouts(
            @Param("symbol") String symbol,
            @Param("maxMinutes") Integer maxMinutes,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les breakouts par direction
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutDirection = :direction AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findBreakoutsByDirection(
            @Param("symbol") String symbol,
            @Param("direction") SessionBreakout.BreakoutDirection direction,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts qui ont été retestés
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.retestOccurred = true AND sb.breakoutTimestamp >= :since ORDER BY sb.retestTimestamp DESC")
    List<SessionBreakout> findRetestedBreakouts(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les breakouts qui ont tenu leur retest
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.retestOccurred = true AND sb.retestHeld = true AND sb.breakoutTimestamp >= :since ORDER BY sb.maxFollowThroughPips DESC")
    List<SessionBreakout> findSuccessfulRetests(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts par session spécifique
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutSession = :session AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findBreakoutsBySession(
            @Param("symbol") String symbol,
            @Param("session") String session,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les breakouts avec signal généré
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.signalGenerated = true AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findBreakoutsWithSignals(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Statistiques de performance des breakouts par type
     */
    @Query("""
        SELECT new map(
            CONCAT(sb.originSession, '_TO_', sb.breakoutSession) as breakoutType,
            sb.breakoutDirection as direction,
            COUNT(sb) as totalCount,
            SUM(CASE WHEN sb.retestOccurred = true AND sb.retestHeld = true THEN 1 ELSE 0 END) as successfulCount,
            AVG(sb.maxFollowThroughPips) as avgFollowThrough,
            AVG(sb.momentumStrength) as avgMomentum
        )
        FROM SessionBreakout sb 
        WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since 
        GROUP BY sb.originSession, sb.breakoutSession, sb.breakoutDirection
    """)
    List<java.util.Map<String, Object>> getBreakoutPerformanceStats(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve le dernier breakout d'un niveau spécifique
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.brokenLevel.id = :levelId ORDER BY sb.breakoutTimestamp DESC LIMIT 1")
    Optional<SessionBreakout> findLastBreakoutOfLevel(@Param("levelId") Long levelId);

    /**
     * Calcule le taux de succès des breakouts par session
     */
    @Query("""
        SELECT new map(
            sb.breakoutSession as session,
            COUNT(sb) as total,
            AVG(CASE WHEN sb.maxFollowThroughPips > 20 THEN 1.0 ELSE 0.0 END) as successRate
        )
        FROM SessionBreakout sb 
        WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since 
        GROUP BY sb.breakoutSession
    """)
    List<java.util.Map<String, Object>> getBreakoutSuccessRateBySession(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les breakouts les plus performants
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.maxFollowThroughPips >= :minPips AND sb.breakoutTimestamp >= :since ORDER BY sb.maxFollowThroughPips DESC")
    List<SessionBreakout> findTopPerformingBreakouts(
            @Param("symbol") String symbol,
            @Param("minPips") Integer minPips,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts durant une fenêtre temporelle spécifique
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp BETWEEN :startTime AND :endTime ORDER BY sb.breakoutTimestamp")
    List<SessionBreakout> findBreakoutsInTimeWindow(
            @Param("symbol") String symbol,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Récupère les breakouts encore valides (non retestés négativement)
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND (sb.retestOccurred = false OR sb.retestHeld = true) AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findValidBreakouts(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Calcule la distribution temporelle des breakouts
     */
    @Query("""
        SELECT new map(
            HOUR(sb.breakoutTimestamp) as hour,
            COUNT(sb) as count,
            AVG(sb.maxFollowThroughPips) as avgFollowThrough
        )
        FROM SessionBreakout sb 
        WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since 
        GROUP BY HOUR(sb.breakoutTimestamp)
        ORDER BY HOUR(sb.breakoutTimestamp)
    """)
    List<java.util.Map<String, Object>> getBreakoutTimeDistribution(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les patterns de breakout récurrents
     */
    @Query("""
        SELECT new map(
            sb.originSession as originSession,
            sb.breakoutSession as breakoutSession,
            sb.breakoutDirection as direction,
            COUNT(sb) as frequency,
            AVG(sb.momentumStrength) as avgMomentum,
            (SUM(CASE WHEN sb.maxFollowThroughPips > 15 THEN 1 ELSE 0 END) * 100.0 / COUNT(sb)) as successRate
        )
        FROM SessionBreakout sb 
        WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since 
        GROUP BY sb.originSession, sb.breakoutSession, sb.breakoutDirection
        HAVING COUNT(sb) >= :minOccurrences
        ORDER BY COUNT(sb) DESC
    """)
    List<java.util.Map<String, Object>> findRecurringBreakoutPatterns(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since,
            @Param("minOccurrences") Long minOccurrences
    );

    /**
     * Récupère les breakouts avec momentum exceptionnel
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.momentumStrength >= :minStrength AND sb.volumeConfirmation = true AND sb.breakoutTimestamp >= :since ORDER BY sb.momentumStrength DESC")
    List<SessionBreakout> findHighMomentumBreakouts(
            @Param("symbol") String symbol,
            @Param("minStrength") java.math.BigDecimal minStrength,
            @Param("since") LocalDateTime since
    );

    /**
     * Compte les breakouts par niveau d'importance du niveau cassé
     */
    @Query("SELECT COUNT(sb) FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.brokenLevel.importanceScore >= :minImportance AND sb.breakoutTimestamp >= :since")
    Long countBreakoutsOfImportantLevels(
            @Param("symbol") String symbol,
            @Param("minImportance") java.math.BigDecimal minImportance,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les breakouts proches d'événements news majeurs
     */
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.timeToNewsMinutes IS NOT NULL AND sb.timeToNewsMinutes BETWEEN :minMinutes AND :maxMinutes AND sb.breakoutTimestamp >= :since ORDER BY sb.timeToNewsMinutes")
    List<SessionBreakout> findBreakoutsNearNews(
            @Param("symbol") String symbol,
            @Param("minMinutes") Integer minMinutes,
            @Param("maxMinutes") Integer maxMinutes,
            @Param("since") LocalDateTime since
    );

    /**
     * Analyse de corrélation entre force du breakout et performance
     */
    @Query("""
        SELECT new map(
            CASE 
                WHEN sb.momentumStrength >= 0.8 THEN 'HIGH'
                WHEN sb.momentumStrength >= 0.6 THEN 'MEDIUM'
                ELSE 'LOW'
            END as momentumCategory,
            COUNT(sb) as count,
            AVG(sb.maxFollowThroughPips) as avgPerformance,
            (SUM(CASE WHEN sb.maxFollowThroughPips > 20 THEN 1 ELSE 0 END) * 100.0 / COUNT(sb)) as successRate
        )
        FROM SessionBreakout sb 
        WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since 
        GROUP BY CASE 
            WHEN sb.momentumStrength >= 0.8 THEN 'HIGH'
            WHEN sb.momentumStrength >= 0.6 THEN 'MEDIUM'
            ELSE 'LOW'
        END
    """)
    List<java.util.Map<String, Object>> analyzeBreakoutMomentumCorrelation(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );
}