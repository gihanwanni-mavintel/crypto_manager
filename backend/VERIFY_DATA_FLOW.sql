-- ==========================================
-- DATA FLOW VERIFICATION QUERIES
-- ==========================================
-- Run these queries to verify signals are being saved to database
-- Usage: psql <connection_string> -f VERIFY_DATA_FLOW.sql
-- Or copy-paste each section into your database client

-- ==========================================
-- 1. CHECK IF ANY SIGNALS HAVE BEEN RECEIVED
-- ==========================================
SELECT COUNT(*) as total_signals FROM signal_messages;
-- Expected: > 0 if signals are being received

-- ==========================================
-- 2. GET LATEST 10 SIGNALS RECEIVED
-- ==========================================
SELECT
    id,
    pair,
    setup_type,
    entry,
    leverage,
    tp1, tp2, tp3, tp4,
    stop_loss,
    timestamp,
    LENGTH(full_message) as message_length
FROM signal_messages
ORDER BY timestamp DESC
LIMIT 10;

-- ==========================================
-- 3. CHECK SIGNALS RECEIVED IN LAST HOUR
-- ==========================================
SELECT
    id,
    pair,
    setup_type,
    entry,
    timestamp
FROM signal_messages
WHERE timestamp > NOW() - INTERVAL '1 hour'
ORDER BY timestamp DESC;
-- If no results, no signals received in the last hour

-- ==========================================
-- 4. CHECK FOR DUPLICATE SIGNALS
-- ==========================================
SELECT
    full_message,
    COUNT(*) as count
FROM signal_messages
GROUP BY full_message
HAVING COUNT(*) > 1;
-- If no results, no duplicates (good!)

-- ==========================================
-- 5. CHECK SIGNAL PARSING QUALITY
-- ==========================================
SELECT
    pair,
    setup_type,
    entry,
    leverage,
    CASE
        WHEN entry IS NULL THEN '❌ MISSING'
        ELSE '✅ OK'
    END as entry_status,
    CASE
        WHEN leverage IS NULL THEN '❌ MISSING'
        ELSE '✅ OK'
    END as leverage_status,
    CASE
        WHEN (tp1 IS NOT NULL OR tp2 IS NOT NULL OR tp3 IS NOT NULL OR tp4 IS NOT NULL) THEN '✅ HAS TPs'
        ELSE '❌ NO TPs'
    END as tp_status,
    CASE
        WHEN stop_loss IS NULL THEN '❌ MISSING'
        ELSE '✅ OK'
    END as sl_status
FROM signal_messages
ORDER BY timestamp DESC
LIMIT 20;

-- ==========================================
-- 6. CHECK SIGNALS BY PAIR
-- ==========================================
SELECT
    pair,
    setup_type,
    COUNT(*) as total_signals,
    MAX(timestamp) as last_received
FROM signal_messages
GROUP BY pair, setup_type
ORDER BY last_received DESC;

-- ==========================================
-- 7. CHECK SIGNALS BY SETUP TYPE (LONG/SHORT)
-- ==========================================
SELECT
    setup_type,
    COUNT(*) as total_signals,
    AVG(CAST(leverage AS FLOAT)) as avg_leverage,
    MIN(timestamp) as first_signal,
    MAX(timestamp) as last_signal
FROM signal_messages
WHERE setup_type IN ('LONG', 'SHORT')
GROUP BY setup_type;

-- ==========================================
-- 8. DETAILED VIEW OF LATEST SIGNAL
-- ==========================================
SELECT
    id,
    pair,
    setup_type,
    entry,
    leverage,
    'TP1: ' || tp1 || ', TP2: ' || tp2 || ', TP3: ' || tp3 || ', TP4: ' || tp4 as take_profits,
    stop_loss,
    timestamp,
    substring(full_message, 1, 200) as message_preview
FROM signal_messages
ORDER BY timestamp DESC
LIMIT 1;

-- ==========================================
-- 9. CHECK FOR NULL VALUES IN CRITICAL FIELDS
-- ==========================================
SELECT
    COUNT(*) as signals_with_null_entry,
    SUM(CASE WHEN entry IS NULL THEN 1 ELSE 0 END) as null_entries,
    SUM(CASE WHEN leverage IS NULL THEN 1 ELSE 0 END) as null_leverage,
    SUM(CASE WHEN stop_loss IS NULL THEN 1 ELSE 0 END) as null_stop_loss
FROM signal_messages;

-- ==========================================
-- 10. CHECK MARKET MESSAGES (NON-SIGNALS)
-- ==========================================
SELECT
    COUNT(*) as total_market_messages,
    COUNT(DISTINCT sender) as unique_senders
FROM market_messages;

-- ==========================================
-- 11. LATEST MARKET MESSAGES
-- ==========================================
SELECT
    id,
    sender,
    substring(text, 1, 100) as message_preview,
    timestamp
FROM market_messages
ORDER BY timestamp DESC
LIMIT 10;

-- ==========================================
-- 12. DATA FLOW SUMMARY
-- ==========================================
SELECT
    'Signal Messages' as message_type,
    COUNT(*) as count,
    MAX(timestamp) as last_received
FROM signal_messages
UNION ALL
SELECT
    'Market Messages' as message_type,
    COUNT(*) as count,
    MAX(timestamp) as last_received
FROM market_messages
ORDER BY last_received DESC;

-- ==========================================
-- TROUBLESHOOTING QUERIES
-- ==========================================

-- If signals are NOT appearing:
-- 1. Check table exists
SELECT table_name FROM information_schema.tables
WHERE table_schema='public' AND table_name='signal_messages';

-- 2. Check table structure
\d signal_messages

-- 3. Check for any errors in the most recent records
SELECT * FROM signal_messages ORDER BY timestamp DESC LIMIT 1;

-- 4. Count signals by hour to see when they arrived
SELECT
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as signals
FROM signal_messages
GROUP BY DATE_TRUNC('hour', timestamp)
ORDER BY hour DESC;

-- 5. Check for very old signals (might indicate timezone issue)
SELECT * FROM signal_messages WHERE timestamp < NOW() - INTERVAL '24 hours' LIMIT 5;

-- ==========================================
-- CLEANUP (USE WITH CAUTION)
-- ==========================================
-- Delete all signals:
-- DELETE FROM signal_messages;

-- Delete all market messages:
-- DELETE FROM market_messages;

-- ==========================================
-- NOTES
-- ==========================================
-- The signal_messages table should grow as new signals arrive
-- Each row = 1 signal received from Telegram
-- Entry/Leverage/TP/SL should NOT be NULL for valid signals
-- Check app.log for parsing errors if signals appear with NULL values