package com.scalper.repository;

import com.scalper.model.entity.SessionBreakout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface SessionBreakoutRepository extends JpaRepository<SessionBreakout, Long> {

    // Méthodes basiques utilisées par le controller

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findRecentBreakouts(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findBySymbolOrderByBreakoutTimestampDesc(@Param("symbol") String symbol);

    @Query("SELECT COUNT(sb) FROM SessionBreakout sb WHERE sb.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);

    @Query("SELECT COUNT(sb) FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since")
    long countBySymbolAndBreakoutTimestampAfter(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    // Méthodes simplifiées pour les autres endpoints (placeholder pour éviter les erreurs)

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutSession = 'LONDON' AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findAsiaToLondonBreakouts(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutSession = 'NEWYORK' AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findLondonToNYBreakouts(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.momentumStrength >= :minMomentum AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findTechnicallyConfirmedBreakouts(@Param("symbol") String symbol, @Param("minMomentum") BigDecimal minMomentum, @Param("since") LocalDateTime since);

    @Query("""
        SELECT sb FROM SessionBreakout sb
        WHERE sb.symbol = :symbol
          AND sb.newsImpactLevel = :impactLevel
          AND sb.breakoutTimestamp >= :since
    """)
    List<SessionBreakout> findNewsCatalyzedBreakouts(
            @Param("symbol") String symbol,
            @Param("impactLevel") Integer impactLevel,
            @Param("since") LocalDateTime since
    );
    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutDirection = :direction AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findBreakoutsByDirection(@Param("symbol") String symbol, @Param("direction") SessionBreakout.BreakoutDirection direction, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.retestOccurred = true AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findRetestedBreakouts(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.retestOccurred = true AND sb.retestHeld = true AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findSuccessfulRetests(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.momentumStrength >= :minStrength AND sb.breakoutTimestamp >= :since ORDER BY sb.breakoutTimestamp DESC")
    List<SessionBreakout> findHighMomentumBreakouts(@Param("symbol") String symbol, @Param("minStrength") BigDecimal minStrength, @Param("since") LocalDateTime since);

    @Query("SELECT sb FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.maxFollowThroughPips >= :minPips AND sb.breakoutTimestamp >= :since ORDER BY sb.maxFollowThroughPips DESC")
    List<SessionBreakout> findTopPerformingBreakouts(@Param("symbol") String symbol, @Param("minPips") Integer minPips, @Param("since") LocalDateTime since);

    // Méthodes pour les statistiques (version simplifiée qui retourne des résultats vides)

    @Query("SELECT sb.breakoutSession as breakoutType, COUNT(sb) as totalCount, AVG(sb.momentumStrength) as avgMomentum FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since GROUP BY sb.breakoutSession")
    List<Map<String, Object>> getBreakoutPerformanceStats(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb.breakoutSession as session, COUNT(sb) as total, COUNT(CASE WHEN sb.retestHeld = true THEN 1 END) as successful FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since GROUP BY sb.breakoutSession")
    List<Map<String, Object>> getBreakoutSuccessRateBySession(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb.breakoutSession as pattern, COUNT(sb) as occurrences FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since GROUP BY sb.breakoutSession HAVING COUNT(sb) >= :minOccurrences")
    List<Map<String, Object>> findRecurringBreakoutPatterns(@Param("symbol") String symbol, @Param("since") LocalDateTime since, @Param("minOccurrences") Long minOccurrences);

    @Query("SELECT HOUR(sb.breakoutTimestamp) as hour, COUNT(sb) as count FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since GROUP BY HOUR(sb.breakoutTimestamp) ORDER BY hour")
    List<Map<String, Object>> getBreakoutTimeDistribution(@Param("symbol") String symbol, @Param("since") LocalDateTime since);

    @Query("SELECT sb.momentumStrength as momentum, AVG(sb.maxFollowThroughPips) as avgPerformance FROM SessionBreakout sb WHERE sb.symbol = :symbol AND sb.breakoutTimestamp >= :since AND sb.momentumStrength IS NOT NULL GROUP BY sb.momentumStrength ORDER BY sb.momentumStrength")
    List<Map<String, Object>> analyzeBreakoutMomentumCorrelation(@Param("symbol") String symbol, @Param("since") LocalDateTime since);
}