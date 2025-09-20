package com.scalper.repository;

import com.scalper.model.entity.IntradayLevel;
import com.scalper.model.entity.TradingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les niveaux intraday avec requêtes optimisées multi-sessions
 */
@Repository
public interface IntradayLevelRepository extends JpaRepository<IntradayLevel, Long> {

    /**
     * Récupère tous les niveaux actifs pour un symbole
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findActiveLevels(@Param("symbol") String symbol);

    /**
     * Récupère les niveaux actifs d'une session spécifique
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.levelType LIKE CONCAT(:sessionPrefix, '%') ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findActiveSessionLevels(
            @Param("symbol") String symbol,
            @Param("sessionPrefix") String sessionPrefix // 'ASIA', 'LONDON', 'NY'
    );

    /**
     * NOUVELLE - Méthode pour compatibilité avec LevelController
     * Trouve les niveaux par préfixe de type et statut
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = :status AND il.levelType LIKE CONCAT(:levelTypePrefix, '%') ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findBySymbolAndLevelTypeStartingWithAndStatus(
            @Param("symbol") String symbol,
            @Param("levelTypePrefix") String levelTypePrefix,
            @Param("status") IntradayLevel.LevelStatus status
    );

    /**
     * NOUVELLE - Méthode pour compatibilité avec LevelController
     * Trouve les niveaux par type exact et statut
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.levelType = :levelType AND il.status = :status ORDER BY il.establishmentTime DESC")
    List<IntradayLevel> findBySymbolAndLevelTypeAndStatus(
            @Param("symbol") String symbol,
            @Param("levelType") IntradayLevel.LevelType levelType,
            @Param("status") IntradayLevel.LevelStatus status
    );

    /**
     * MODIFIÉE - Corrigée pour compatibilité avec LevelController
     * Récupère les niveaux cassés récemment
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'BROKEN' AND il.brokenAt >= :since ORDER BY il.brokenAt DESC")
    List<IntradayLevel> findBrokenLevelsSince(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les niveaux proches d'un prix donné
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND ABS(il.price - :price) <= :tolerance ORDER BY ABS(il.price - :price)")
    List<IntradayLevel> findLevelsNearPrice(
            @Param("symbol") String symbol,
            @Param("price") BigDecimal price,
            @Param("tolerance") BigDecimal tolerance
    );

    /**
     * Récupère les niveaux VWAP actifs
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.levelType LIKE 'VWAP_%' ORDER BY il.establishmentTime DESC")
    List<IntradayLevel> findActiveVWAPLevels(@Param("symbol") String symbol);

    /**
     * Trouve les niveaux de session high/low pour une date
     */
    @Query("SELECT il FROM IntradayLevel il JOIN il.session s WHERE il.symbol = :symbol AND s.sessionDate = :date AND il.levelType IN ('ASIA_HIGH', 'ASIA_LOW', 'LONDON_HIGH', 'LONDON_LOW', 'NY_HIGH', 'NY_LOW') ORDER BY il.levelType")
    List<IntradayLevel> findSessionHighLowLevels(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date
    );

    /**
     * Récupère les niveaux avec importance élevée
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.importanceScore >= :minImportance ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findHighImportanceLevels(
            @Param("symbol") String symbol,
            @Param("minImportance") BigDecimal minImportance
    );

    /**
     * Alias pour compatibilité - Trouve les niveaux cassés récemment
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'BROKEN' AND il.brokenAt >= :since ORDER BY il.brokenAt DESC")
    List<IntradayLevel> findRecentlyBrokenLevels(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les niveaux par type spécifique
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.levelType = :levelType AND il.status = 'ACTIVE' ORDER BY il.establishmentTime DESC")
    List<IntradayLevel> findLevelsByType(
            @Param("symbol") String symbol,
            @Param("levelType") IntradayLevel.LevelType levelType
    );

    /**
     * Trouve le niveau le plus proche au-dessus d'un prix
     */
    @Query(value = "SELECT * FROM intraday_levels il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.price > :price ORDER BY il.price ASC LIMIT 1", nativeQuery = true)
    Optional<IntradayLevel> findNearestResistance(
            @Param("symbol") String symbol,
            @Param("price") BigDecimal price
    );

    /**
     * Trouve le niveau le plus proche en-dessous d'un prix
     */
    @Query(value = "SELECT * FROM intraday_levels il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.price < :price ORDER BY il.price DESC LIMIT 1", nativeQuery = true)
    Optional<IntradayLevel> findNearestSupport(
            @Param("symbol") String symbol,
            @Param("price") BigDecimal price
    );

    /**
     * Vérifie si un niveau existe déjà pour éviter les doublons
     */
    @Query("SELECT COUNT(il) > 0 FROM IntradayLevel il WHERE il.symbol = :symbol AND il.levelType = :levelType AND il.session.id = :sessionId")
    boolean existsBySymbolAndLevelTypeAndSession(
            @Param("symbol") String symbol,
            @Param("levelType") IntradayLevel.LevelType levelType,
            @Param("sessionId") Long sessionId
    );

    /**
     * CORRIGÉE - Récupère les niveaux les plus testés avec paramètre Integer au lieu de int
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.touchCount >= :minTouches ORDER BY il.touchCount DESC, il.importanceScore DESC")
    List<IntradayLevel> findMostTestedLevels(
            @Param("symbol") String symbol,
            @Param("minTouches") Integer minTouches
    );

    /**
     * Trouve les niveaux établis pendant une session spécifique
     */
    @Query("SELECT il FROM IntradayLevel il JOIN il.session s WHERE il.symbol = :symbol AND s.sessionName = :sessionName AND s.sessionDate = :date ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findLevelsEstablishedInSession(
            @Param("symbol") String symbol,
            @Param("sessionName") TradingSession.SessionName sessionName,
            @Param("date") LocalDate date
    );

    /**
     * Calcule la densité de niveaux autour d'un prix
     */
    @Query("SELECT COUNT(il) FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.price BETWEEN :lowerBound AND :upperBound")
    Long countLevelsInRange(
            @Param("symbol") String symbol,
            @Param("lowerBound") BigDecimal lowerBound,
            @Param("upperBound") BigDecimal upperBound
    );

    /**
     * Récupère les statistiques de niveaux par type
     */
    @Query("""
        SELECT new map(
            il.levelType as levelType,
            COUNT(il) as count,
            AVG(il.importanceScore) as avgImportance,
            AVG(il.touchCount) as avgTouches,
            SUM(CASE WHEN il.status = 'BROKEN' THEN 1 ELSE 0 END) as brokenCount
        )
        FROM IntradayLevel il 
        WHERE il.symbol = :symbol AND il.establishmentTime >= :since 
        GROUP BY il.levelType
    """)
    List<java.util.Map<String, Object>> getLevelStatistics(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Trouve les niveaux avec retests récents
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.lastRetestTime >= :since ORDER BY il.lastRetestTime DESC")
    List<IntradayLevel> findLevelsWithRecentRetests(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since
    );

    /**
     * Récupère les niveaux prédits pour être testés prochainement
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.nextTestPrediction IS NOT NULL AND il.nextTestPrediction <= :within ORDER BY il.nextTestPrediction")
    List<IntradayLevel> findLevelsPredictedForTesting(
            @Param("symbol") String symbol,
            @Param("within") LocalDateTime within
    );

    /**
     * Trouve les niveaux ASIA pour breakout LONDON
     */
    @Query("SELECT il FROM IntradayLevel il JOIN il.session s WHERE il.symbol = :symbol AND il.levelType IN ('ASIA_HIGH', 'ASIA_LOW') AND s.sessionDate = :date AND il.status = 'ACTIVE' ORDER BY il.levelType")
    List<IntradayLevel> findAsiaLevelsForLondonBreakout(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date
    );

    /**
     * Trouve les niveaux LONDON pour retest NY
     */
    @Query("SELECT il FROM IntradayLevel il JOIN il.session s WHERE il.symbol = :symbol AND il.levelType IN ('LONDON_HIGH', 'LONDON_LOW') AND s.sessionDate = :date AND il.status = 'ACTIVE' ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findLondonLevelsForNYRetest(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date
    );

    /**
     * Met à jour le statut d'un niveau
     */
    @Modifying
    @Transactional
    @Query("UPDATE IntradayLevel il SET il.status = :status WHERE il.id = :id")
    void updateLevelStatus(
            @Param("id") Long id,
            @Param("status") IntradayLevel.LevelStatus status
    );

    /**
     * Incrémente le compteur de tests d'un niveau
     */
    @Modifying
    @Transactional
    @Query("UPDATE IntradayLevel il SET il.touchCount = il.touchCount + 1, il.lastRetestTime = :testTime WHERE il.id = :id")
    void incrementTouchCount(
            @Param("id") Long id,
            @Param("testTime") LocalDateTime testTime
    );

    /**
     * Trouve les niveaux candidats pour patterns bounce
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.importanceScore >= 0.7 AND il.touchCount >= 2 ORDER BY il.importanceScore DESC")
    List<IntradayLevel> findBounceCandidateLevels(@Param("symbol") String symbol);

    /**
     * Récupère les niveaux d'une fourchette d'importance
     */
    @Query("SELECT il FROM IntradayLevel il WHERE il.symbol = :symbol AND il.status = 'ACTIVE' AND il.importanceScore BETWEEN :minImportance AND :maxImportance ORDER BY il.price")
    List<IntradayLevel> findLevelsByImportanceRange(
            @Param("symbol") String symbol,
            @Param("minImportance") BigDecimal minImportance,
            @Param("maxImportance") BigDecimal maxImportance
    );

    /**
     * Supprime les niveaux anciens et inactifs pour maintenance
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM IntradayLevel il WHERE il.establishmentTime < :cutoffDate AND il.status IN ('BROKEN', 'WEAKENED')")
    void cleanupOldLevels(@Param("cutoffDate") LocalDateTime cutoffDate);
}