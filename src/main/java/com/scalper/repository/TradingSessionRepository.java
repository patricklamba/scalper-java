package com.scalper.repository;

import com.scalper.model.entity.TradingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les sessions de trading avec requêtes optimisées multi-sessions
 */
@Repository
public interface TradingSessionRepository extends JpaRepository<TradingSession, Long> {

    /**
     * Trouve une session spécifique par symbole, nom et date
     */
    Optional<TradingSession> findBySymbolAndSessionNameAndSessionDate(
            String symbol,
            TradingSession.SessionName sessionName,
            LocalDate sessionDate
    );

    /**
     * Récupère toutes les sessions d'une date pour un symbole
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionDate = :date ORDER BY ts.sessionStart")
    List<TradingSession> findAllSessionsBySymbolAndDate(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date
    );

    /**
     * Récupère les sessions actives (en cours)
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.sessionStart <= :now AND ts.sessionEnd >= :now")
    List<TradingSession> findActiveSessions(@Param("now") LocalDateTime now);

    /**
     * Trouve la session active pour un symbole spécifique
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionStart <= :now AND ts.sessionEnd >= :now")
    Optional<TradingSession> findActiveSessionForSymbol(
            @Param("symbol") String symbol,
            @Param("now") LocalDateTime now
    );

    /**
     * Récupère la dernière session complète par type
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionName = :sessionName AND ts.sessionDate < :date ORDER BY ts.sessionDate DESC LIMIT 1")
    Optional<TradingSession> findLastCompletedSession(
            @Param("symbol") String symbol,
            @Param("sessionName") TradingSession.SessionName sessionName,
            @Param("date") LocalDate date
    );

    /**
     * Récupère les sessions avec breakouts pour analyse historique
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.breakoutOccurred = true AND ts.sessionDate >= :fromDate ORDER BY ts.sessionDate DESC")
    List<TradingSession> findSessionsWithBreakouts(
            @Param("symbol") String symbol,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Calcule la volatilité moyenne par session sur une période
     */
    @Query("SELECT AVG(ts.volatilityScore) FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionName = :sessionName AND ts.sessionDate >= :fromDate")
    Double calculateAverageVolatility(
            @Param("symbol") String symbol,
            @Param("sessionName") TradingSession.SessionName sessionName,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Trouve les sessions avec range dans une fourchette spécifique
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.rangeSizePips BETWEEN :minPips AND :maxPips AND ts.sessionDate >= :fromDate ORDER BY ts.sessionDate DESC")
    List<TradingSession> findSessionsByRangeSize(
            @Param("symbol") String symbol,
            @Param("minPips") Integer minPips,
            @Param("maxPips") Integer maxPips,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Récupère les statistiques de session pour un symbole
     */
    @Query("""
        SELECT new map(
            ts.sessionName as sessionName,
            AVG(ts.rangeSizePips) as avgRange,
            AVG(ts.volatilityScore) as avgVolatility,
            COUNT(ts) as totalSessions,
            SUM(CASE WHEN ts.breakoutOccurred = true THEN 1 ELSE 0 END) as breakoutCount
        )
        FROM TradingSession ts 
        WHERE ts.symbol = :symbol AND ts.sessionDate >= :fromDate 
        GROUP BY ts.sessionName
    """)
    List<java.util.Map<String, Object>> getSessionStatistics(
            @Param("symbol") String symbol,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Trouve la session Asia de la journée (pour breakouts London)
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionName = 'ASIA' AND ts.sessionDate = :date")
    Optional<TradingSession> findAsiaSessionForDate(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date
    );

    /**
     * Trouve la session London de la journée (pour retests NY)
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionName = 'LONDON' AND ts.sessionDate = :date")
    Optional<TradingSession> findLondonSessionForDate(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date
    );

    /**
     * Vérifie si une session existe déjà
     */
    boolean existsBySymbolAndSessionNameAndSessionDate(
            String symbol,
            TradingSession.SessionName sessionName,
            LocalDate sessionDate
    );

    /**
     * Récupère les sessions par qualité pour analyse
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionQuality = :quality AND ts.sessionDate >= :fromDate ORDER BY ts.sessionDate DESC")
    List<TradingSession> findSessionsByQuality(
            @Param("symbol") String symbol,
            @Param("quality") TradingSession.SessionQuality quality,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Trouve les sessions avec news impact significatif
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.newsImpactScore >= :minImpact AND ts.sessionDate >= :fromDate ORDER BY ts.newsImpactScore DESC")
    List<TradingSession> findSessionsWithHighNewsImpact(
            @Param("symbol") String symbol,
            @Param("minImpact") java.math.BigDecimal minImpact,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Calcule les statistiques de performance des breakouts par session
     */
    @Query("""
        SELECT new map(
            ts.sessionName as session,
            ts.breakoutDirection as direction,
            COUNT(ts) as count,
            AVG(ts.rangeSizePips) as avgRangeBeforeBreakout
        )
        FROM TradingSession ts 
        WHERE ts.symbol = :symbol AND ts.breakoutOccurred = true AND ts.sessionDate >= :fromDate 
        GROUP BY ts.sessionName, ts.breakoutDirection
    """)
    List<java.util.Map<String, Object>> getBreakoutStatistics(
            @Param("symbol") String symbol,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Trouve les sessions précédant une date (pour contexte historique)
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionDate < :date ORDER BY ts.sessionDate DESC LIMIT :limit")
    List<TradingSession> findPreviousSessions(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date,
            @Param("limit") int limit
    );

    /**
     * Récupère les sessions pour une plage de dates
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionDate BETWEEN :startDate AND :endDate ORDER BY ts.sessionDate, ts.sessionStart")
    List<TradingSession> findSessionsInDateRange(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Compte les breakouts par type de session sur une période
     */
    @Query("SELECT COUNT(ts) FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionName = :sessionName AND ts.breakoutOccurred = true AND ts.sessionDate >= :fromDate")
    Long countBreakoutsBySession(
            @Param("symbol") String symbol,
            @Param("sessionName") TradingSession.SessionName sessionName,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Trouve les sessions avec le plus grand range (pour volatilité)
     */
    @Query("SELECT ts FROM TradingSession ts WHERE ts.symbol = :symbol AND ts.sessionDate >= :fromDate ORDER BY ts.rangeSizePips DESC LIMIT :limit")
    List<TradingSession> findHighestVolatilitySessions(
            @Param("symbol") String symbol,
            @Param("fromDate") LocalDate fromDate,
            @Param("limit") int limit
    );
}