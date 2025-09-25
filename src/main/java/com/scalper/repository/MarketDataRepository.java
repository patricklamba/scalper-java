package com.scalper.repository;

import com.scalper.model.entity.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour données de marché avec requêtes optimisées pour scalping multi-sessions
 */
@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    // ========== Requêtes de Base ==========

    /**
     * Récupérer les N dernières bougies pour un symbole et timeframe
     */
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = :timeframe " +
            "ORDER BY md.timestamp DESC")
    List<MarketData> findLatestCandles(@Param("symbol") String symbol,
                                       @Param("timeframe") String timeframe);

    /**
     * Récupérer données dans une plage de temps spécifique
     */
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = :timeframe " +
            "AND md.timestamp BETWEEN :startTime AND :endTime ORDER BY md.timestamp ASC")
    List<MarketData> findBySymbolAndTimeframeAndTimestampBetween(@Param("symbol") String symbol,
                                                                 @Param("timeframe") String timeframe,
                                                                 @Param("startTime") LocalDateTime startTime,
                                                                 @Param("endTime") LocalDateTime endTime);

    /**
     * Récupérer la dernière bougie disponible
     */
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = :timeframe " +
            "ORDER BY md.timestamp DESC")
    Optional<MarketData> findLatestCandle(@Param("symbol") String symbol,
                                          @Param("timeframe") String timeframe);
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = :timeframe " +
            "AND md.sessionName = :sessionName AND md.timestamp >= :startOfDay " +
            "ORDER BY md.timestamp ASC")
    List<MarketData> findSessionDataBySymbolAndSessionAndDate(
            @Param("symbol") String symbol,
            @Param("timeframe") String timeframe,
            @Param("sessionName") String sessionName,
            @Param("startOfDay") LocalDateTime startOfDay);


    /**
     * Calculer High/Low pour une session donnée (jour courant)
     */
    @Query("SELECT MAX(md.highPrice), MIN(md.lowPrice) FROM MarketData md " +
            "WHERE md.symbol = :symbol AND md.sessionName = :sessionName " +
            "AND md.timestamp >= :startOfDay")
    List<Object[]> findSessionHighLow(@Param("symbol") String symbol,
                                      @Param("sessionName") String sessionName,
                                      @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Trouver les bougies de breakout (range > seuil)
     */
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = :timeframe " +
            "AND md.timestamp >= :since " +
            "AND (md.highPrice - md.lowPrice) > :minRangeThreshold " +
            "ORDER BY md.timestamp DESC")
    List<MarketData> findBreakoutCandles(@Param("symbol") String symbol,
                                         @Param("timeframe") String timeframe,
                                         @Param("since") LocalDateTime since,
                                         @Param("minRangeThreshold") BigDecimal minRangeThreshold);

    // ========== Requêtes pour VWAP et Niveaux CORRIGÉES ==========

    /**
     * Calculer VWAP pour une session (jour courant)
     */
    @Query("SELECT SUM((md.highPrice + md.lowPrice + md.closePrice) * md.volume / 3) / SUM(md.volume) " +
            "FROM MarketData md WHERE md.symbol = :symbol AND md.sessionName = :sessionName " +
            "AND md.timestamp >= :startOfDay AND md.volume > 0")
    Optional<BigDecimal> calculateSessionVWAP(@Param("symbol") String symbol,
                                              @Param("sessionName") String sessionName,
                                              @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Trouver niveaux de prix significatifs (pivots) - Requête simplifiée
     */
    @Query("SELECT md FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = 'M5' " +
            "AND md.timestamp >= :since " +
            "ORDER BY md.timestamp DESC")
    List<MarketData> findPotentialPivotLevels(@Param("symbol") String symbol,
                                              @Param("since") LocalDateTime since);

    // ========== Requêtes de Performance et Monitoring ==========

    /**
     * Vérifier si les données sont à jour (moins de X minutes)
     */
    @Query("SELECT COUNT(md) > 0 FROM MarketData md WHERE md.symbol = :symbol " +
            "AND md.timeframe = :timeframe AND md.timestamp >= :cutoffTime")
    boolean hasRecentData(@Param("symbol") String symbol,
                          @Param("timeframe") String timeframe,
                          @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Compter le nombre de bougies pour une période
     */
    @Query("SELECT COUNT(md) FROM MarketData md WHERE md.symbol = :symbol " +
            "AND md.timeframe = :timeframe AND md.timestamp >= :since")
    long countCandlesSince(@Param("symbol") String symbol,
                           @Param("timeframe") String timeframe,
                           @Param("since") LocalDateTime since);

    /**
     * Nettoyer les données anciennes (housekeeping)
     */
    @Query("DELETE FROM MarketData md WHERE md.timestamp < :cutoffDate")
    void deleteOldData(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== Requêtes pour Statistiques de Trading CORRIGÉES ==========

    /**
     * Calculer volatilité moyenne par session
     */
    @Query("SELECT md.sessionName, AVG(md.highPrice - md.lowPrice) as avgRange " +
            "FROM MarketData md WHERE md.symbol = :symbol AND md.timeframe = :timeframe " +
            "AND md.timestamp >= :since GROUP BY md.sessionName")
    List<Object[]> calculateAverageRangeBySession(@Param("symbol") String symbol,
                                                  @Param("timeframe") String timeframe,
                                                  @Param("since") LocalDateTime since);

    // ========== Requêtes Native pour Performance ==========

    /**
     * Requête native optimisée pour trouver gaps de prix (weekends, news)
     */
    @Query(value = "SELECT * FROM market_data_sessions md1 " +
            "WHERE md1.symbol = :symbol AND md1.timeframe = 'M1' " +
            "AND ABS(md1.open_price - (SELECT md2.close_price FROM market_data_sessions md2 " +
            "WHERE md2.symbol = md1.symbol AND md2.timeframe = md1.timeframe " +
            "AND md2.timestamp < md1.timestamp ORDER BY md2.timestamp DESC LIMIT 1)) > :gapThreshold " +
            "ORDER BY md1.timestamp DESC LIMIT 20",
            nativeQuery = true)
    List<MarketData> findPriceGaps(@Param("symbol") String symbol,
                                   @Param("gapThreshold") double gapThreshold);

    /**
     * Vérifier existence d'une bougie spécifique (éviter doublons)
     */
    boolean existsBySymbolAndTimeframeAndTimestamp(String symbol,
                                                   String timeframe,
                                                   LocalDateTime timestamp);

    // ========== Méthodes de requête dérivées (NOUVELLES) ==========

    /**
     * Méthodes générées automatiquement par Spring Data JPA
     * Pour éviter les requêtes HQL complexes
     */
    List<MarketData> findBySymbolAndTimeframeOrderByTimestampDesc(String symbol, String timeframe);

    List<MarketData> findBySymbolAndSessionNameAndTimestampGreaterThanEqualOrderByTimestampAsc(
            String symbol, String sessionName, LocalDateTime timestamp);

    Optional<MarketData> findFirstBySymbolAndTimeframeOrderByTimestampDesc(String symbol, String timeframe);
}