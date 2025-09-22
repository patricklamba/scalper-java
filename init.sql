-- ===============================
-- SCALPER ASSISTANT - INIT DATABASE
-- ===============================

-- Création des extensions PostgreSQL nécessaires
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- ===============================
-- 1. TABLE DES SESSIONS DE TRADING
-- ===============================
CREATE TABLE trading_sessions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    session_name VARCHAR(10) NOT NULL CHECK (session_name IN ('ASIA', 'LONDON', 'NEWYORK')),
    session_date DATE NOT NULL,
    session_start TIMESTAMP NOT NULL,
    session_end TIMESTAMP NOT NULL,

    -- OHLC de la session
    session_open DECIMAL(10,5),
    session_high DECIMAL(10,5),
    session_low DECIMAL(10,5),
    session_close DECIMAL(10,5),

    -- Métriques de session
    range_size_pips INTEGER DEFAULT 0 CHECK (range_size_pips >= 0),
    volume_total BIGINT DEFAULT 0,
    volatility_score DECIMAL(3,2) DEFAULT 0.00 CHECK (volatility_score >= 0.00 AND volatility_score <= 1.00),
    breakout_occurred BOOLEAN DEFAULT FALSE,
    breakout_direction VARCHAR(5) CHECK (breakout_direction IN ('LONG', 'SHORT')),

    -- Contexte de trading
    major_news_count INTEGER DEFAULT 0,
    news_impact_score DECIMAL(3,2) DEFAULT 0.00 CHECK (news_impact_score >= 0.00 AND news_impact_score <= 1.00),
    session_quality VARCHAR(15) DEFAULT 'NORMAL' CHECK (session_quality IN ('QUIET', 'NORMAL', 'ACTIVE', 'VOLATILE')),

    -- Métriques calculées (VWAP, Pivots)
    vwap_price DECIMAL(10,5),
    pivot_point DECIMAL(10,5),
    support_1 DECIMAL(10,5),
    resistance_1 DECIMAL(10,5),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_trading_sessions UNIQUE(symbol, session_name, session_date)
);

-- Index pour performance
CREATE INDEX idx_trading_sessions_symbol_date ON trading_sessions (symbol, session_date DESC);
CREATE INDEX idx_trading_sessions_active_session ON trading_sessions (symbol, session_name, session_date DESC);
CREATE INDEX idx_trading_sessions_breakout ON trading_sessions (symbol, breakout_occurred, session_date DESC);

-- ===============================
-- 2. TABLE DES NIVEAUX INTRADAY
-- ===============================
CREATE TABLE intraday_levels (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    level_type VARCHAR(30) NOT NULL CHECK (level_type IN (
        'ASIA_HIGH', 'ASIA_LOW', 'LONDON_HIGH', 'LONDON_LOW', 'NY_HIGH', 'NY_LOW',
        'VWAP_ASIA', 'VWAP_LONDON', 'VWAP_NY', 'PIVOT_DAILY', 'ROUND_NUMBER',
        'PREVIOUS_DAY_HIGH', 'PREVIOUS_DAY_LOW', 'WEEKLY_HIGH', 'WEEKLY_LOW'
    )),
    price DECIMAL(10,5) NOT NULL CHECK (price > 0),
    session_id INTEGER REFERENCES trading_sessions(id) ON DELETE CASCADE,
    establishment_time TIMESTAMP NOT NULL,

    -- Force et fiabilité du niveau
    importance_score DECIMAL(3,2) NOT NULL DEFAULT 0.50 CHECK (importance_score >= 0.00 AND importance_score <= 1.00),
    touch_count INTEGER DEFAULT 1 CHECK (touch_count >= 0),
    max_rejection_pips INTEGER DEFAULT 0 CHECK (max_rejection_pips >= 0),
    volume_at_establishment BIGINT DEFAULT 0,

    -- État du niveau
    status VARCHAR(15) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BROKEN', 'RETESTED', 'WEAKENED', 'INACTIVE')),
    broken_at TIMESTAMP,
    broken_by_session VARCHAR(10) CHECK (broken_by_session IN ('ASIA', 'LONDON', 'NEWYORK')),
    broken_price DECIMAL(10,5),
    retest_count INTEGER DEFAULT 0 CHECK (retest_count >= 0),
    last_retest_time TIMESTAMP,

    -- Prédictions IA
    break_probability DECIMAL(3,2) DEFAULT 0.50 CHECK (break_probability >= 0.00 AND break_probability <= 1.00),
    next_test_prediction TIMESTAMP,

    -- Métadonnées pour contexte
    level_context TEXT,
    break_catalyst VARCHAR(100),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_intraday_levels UNIQUE(symbol, level_type, session_id)
);

-- Index optimisés pour trading en temps réel
CREATE INDEX idx_intraday_levels_active_lookup ON intraday_levels (symbol, status, importance_score DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_intraday_levels_level_type ON intraday_levels (level_type, symbol, establishment_time DESC);
CREATE INDEX idx_intraday_levels_price_range ON intraday_levels (symbol, price, status);
CREATE INDEX idx_intraday_levels_establishment_time ON intraday_levels (establishment_time DESC);

-- ===============================
-- 3. TABLE DES BREAKOUTS INTER-SESSIONS
-- ===============================
CREATE TABLE session_breakouts (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    broken_level_id INTEGER REFERENCES intraday_levels(id) ON DELETE CASCADE,
    breakout_session VARCHAR(10) NOT NULL CHECK (breakout_session IN ('ASIA', 'LONDON', 'NEWYORK')),
    breakout_time TIMESTAMP NOT NULL,
    breakout_price DECIMAL(10,5) NOT NULL,
    breakout_direction VARCHAR(5) NOT NULL CHECK (breakout_direction IN ('LONG', 'SHORT')),

    -- Contexte du breakout
    volume_confirmation BOOLEAN DEFAULT FALSE,
    news_catalyst VARCHAR(100),
    pre_breakout_range_size DECIMAL(6,4) DEFAULT 0.00,
    momentum_strength DECIMAL(3,2) DEFAULT 0.50 CHECK (momentum_strength >= 0.00 AND momentum_strength <= 1.00),

    -- Suivi post-breakout
    retest_occurred BOOLEAN DEFAULT FALSE,
    retest_time TIMESTAMP,
    retest_held BOOLEAN,
    max_follow_through DECIMAL(6,4) DEFAULT 0.00,
    target_reached BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_session_breakouts UNIQUE(broken_level_id, breakout_session, breakout_timestamp)
);

-- Index pour analyse des breakouts
CREATE INDEX idx_session_breakouts_symbol_time ON session_breakouts (symbol, breakout_timestamp DESC);
CREATE INDEX idx_session_breakouts_session ON session_breakouts (breakout_session, breakout_timestamp DESC);

-- ===============================
-- 4. TABLE DES SIGNAUX DE TRADING ENRICHIS
-- ===============================
CREATE TABLE trading_signals_enriched (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    signal_type VARCHAR(10) NOT NULL CHECK (signal_type IN ('BUY', 'SELL')),
    setup_type VARCHAR(30) NOT NULL CHECK (setup_type IN (
        'ASIA_BREAKOUT', 'LONDON_BREAKOUT', 'NY_BREAKOUT',
        'LEVEL_RETEST', 'LEVEL_BOUNCE', 'VWAP_REJECTION',
        'NEWS_BREAKOUT', 'MOMENTUM_CONTINUATION'
    )),

    -- Prix de trading
    entry_price DECIMAL(10,5) NOT NULL,
    stop_loss DECIMAL(10,5) NOT NULL,
    take_profit_1 DECIMAL(10,5) NOT NULL,
    take_profit_2 DECIMAL(10,5),
    risk_reward_ratio DECIMAL(4,2) NOT NULL CHECK (risk_reward_ratio > 0),

    -- Références aux niveaux et breakouts
    related_level_id INTEGER REFERENCES intraday_levels(id),
    related_breakout_id INTEGER REFERENCES session_breakouts(id),

    -- Contexte de marché
    session_context VARCHAR(15) NOT NULL,
    news_context VARCHAR(50) DEFAULT 'CLEAR',
    market_sentiment VARCHAR(15) DEFAULT 'NEUTRAL',

    -- Qualité du signal
    confidence_score DECIMAL(3,2) NOT NULL CHECK (confidence_score >= 0.00 AND confidence_score <= 1.00),
    pattern_strength DECIMAL(3,2) DEFAULT 0.50,
    volume_confirmation BOOLEAN DEFAULT FALSE,

    -- Métadonnées IA
    ai_analysis TEXT,
    alternative_scenarios TEXT,

    -- Statut
    status VARCHAR(15) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CLOSED', 'CANCELLED')),
    triggered_at TIMESTAMP,
    closed_at TIMESTAMP,
    pnl_pips INTEGER,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Screenshots et annexes
    screenshot_path VARCHAR(255),
    chart_annotation TEXT
);

-- Index pour signaux actifs
CREATE INDEX idx_trading_signals_active ON trading_signals_enriched (symbol, status, created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_trading_signals_setup_type ON trading_signals_enriched (setup_type, symbol, created_at DESC);

-- ===============================
-- 5. TABLE DES ALERTES NEWS
-- ===============================
CREATE TABLE news_alerts (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(50) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    currency VARCHAR(10) NOT NULL,
    impact_level VARCHAR(10) NOT NULL CHECK (impact_level IN ('LOW', 'MEDIUM', 'HIGH')),
    event_title TEXT NOT NULL,
    actual_value VARCHAR(50),
    forecast_value VARCHAR(50),
    previous_value VARCHAR(50),
    deviation_impact DECIMAL(3,2) DEFAULT 0.00,
    
    -- Métadonnées de traitement
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    affects_symbol VARCHAR(10),
    market_impact_assessment TEXT,
    
    CONSTRAINT uk_news_alerts UNIQUE(source, event_time, event_title)
);

-- Index pour recherche rapide de news
CREATE INDEX idx_news_alerts_time_impact ON news_alerts (event_time DESC, impact_level);
CREATE INDEX idx_news_alerts_currency ON news_alerts (currency, event_time DESC);

-- ===============================
-- 6. DONNÉES DE TEST INITIALES
-- ===============================

-- Sessions exemple pour EURUSD (aujourd'hui)
INSERT INTO trading_sessions (symbol, session_name, session_date, session_start, session_end, session_open, session_high, session_low, session_close, range_size_pips, volatility_score) VALUES
('EURUSD', 'ASIA', CURRENT_DATE, CURRENT_DATE + TIME '00:00:00', CURRENT_DATE + TIME '06:00:00', 1.0850, 1.0875, 1.0840, 1.0870, 35, 0.30),
('EURUSD', 'LONDON', CURRENT_DATE, CURRENT_DATE + TIME '07:00:00', CURRENT_DATE + TIME '11:00:00', 1.0872, 1.0920, 1.0845, 1.0890, 75, 0.75),
('EURUSD', 'NEWYORK', CURRENT_DATE, CURRENT_DATE + TIME '12:00:00', CURRENT_DATE + TIME '16:00:00', 1.0890, 1.0905, 1.0865, 1.0880, 40, 0.55);

-- Niveaux intraday exemple
INSERT INTO intraday_levels (symbol, level_type, price, session_id, establishment_time, importance_score, touch_count, status) VALUES
('EURUSD', 'ASIA_HIGH', 1.0875, 1, CURRENT_DATE + TIME '05:30:00', 0.85, 3, 'ACTIVE'),
('EURUSD', 'ASIA_LOW', 1.0840, 1, CURRENT_DATE + TIME '02:15:00', 0.80, 2, 'ACTIVE'),
('EURUSD', 'LONDON_HIGH', 1.0920, 2, CURRENT_DATE + TIME '09:45:00', 0.90, 1, 'ACTIVE'),
('EURUSD', 'LONDON_LOW', 1.0845, 2, CURRENT_DATE + TIME '07:20:00', 0.75, 2, 'BROKEN');

-- News exemple
INSERT INTO news_alerts (source, event_time, currency, impact_level, event_title, forecast_value, previous_value, processed) VALUES
('ForexFactory', CURRENT_TIMESTAMP + INTERVAL '2 hours', 'EUR', 'HIGH', 'ECB Interest Rate Decision', '3.75%', '3.50%', false),
('ForexFactory', CURRENT_TIMESTAMP + INTERVAL '6 hours', 'USD', 'HIGH', 'NFP Employment Change', '180K', '150K', false);

-- ===============================
-- 7. FONCTIONS UTILITAIRES
-- ===============================

-- Fonction pour calculer le prochain niveau de support/résistance
CREATE OR REPLACE FUNCTION get_next_key_level(p_symbol VARCHAR, p_price DECIMAL, p_direction VARCHAR)
RETURNS DECIMAL AS $$
DECLARE
    next_level DECIMAL;
BEGIN
    IF p_direction = 'UP' THEN
        SELECT MIN(price) INTO next_level 
        FROM intraday_levels 
        WHERE symbol = p_symbol AND price > p_price AND status = 'ACTIVE';
    ELSE
        SELECT MAX(price) INTO next_level 
        FROM intraday_levels 
        WHERE symbol = p_symbol AND price < p_price AND status = 'ACTIVE';
    END IF;
    
    RETURN COALESCE(next_level, p_price);
END;
$$ LANGUAGE plpgsql;

-- Trigger pour update automatique du timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Application du trigger sur les tables principales
CREATE TRIGGER update_trading_sessions_updated_at BEFORE UPDATE ON trading_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_intraday_levels_updated_at BEFORE UPDATE ON intraday_levels FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===============================
-- 8. COMMENTAIRES ET DOCUMENTATION
-- ===============================

COMMENT ON TABLE trading_sessions IS 'Sessions de marché Asia/London/NY avec métriques OHLC et contexte';
COMMENT ON TABLE intraday_levels IS 'Niveaux clés intraday avec tracking des cassures et tests';
COMMENT ON TABLE session_breakouts IS 'Breakouts des niveaux entre sessions avec suivi post-breakout';
COMMENT ON TABLE trading_signals_enriched IS 'Signaux de trading avec contexte multi-sessions et IA';
COMMENT ON TABLE news_alerts IS 'Alertes économiques reçues de n8n avec impact sur trading';

-- Statistiques pour optimisation
ANALYZE trading_sessions;
ANALYZE intraday_levels;
ANALYZE session_breakouts;
ANALYZE trading_signals_enriched;
ANALYZE news_alerts;