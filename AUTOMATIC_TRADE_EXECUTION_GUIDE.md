# Automatic Trade Execution Implementation Guide

## Overview

This guide explains how the complete automatic trade execution workflow has been implemented across the Python backend, Java backend, and frontend components.

## Architecture Flow

```
Telegram Group
     ↓
Telegram Signal → Python Backend (telethon_message_collector.py)
     ↓
   ├→ Save to PostgreSQL Database
   ├→ Broadcast via WebSocket to Frontend (port 8082)
   └→ Send to Java Backend via HTTP POST (http://localhost:8081/api/webhook/signal)
     ↓
Java Backend (Spring Boot)
     ↓
   ├→ Save Signal to Database
   ├→ Auto-execute Trade (if enabled)
   │  ├→ Calculate Quantity (based on leverage and balance)
   │  ├→ Place LIMIT Order at Entry Price
   │  ├→ Place Stop-Loss Order
   │  └→ Place Take-Profit Orders (TP1-4)
   └→ Return Execution Status to Python Backend
     ↓
Return Status to WebSocket Clients
```

---

## Component Details

### 1. Python Backend (telethon_message_collector.py)

**New Features:**
- Import `requests` library for HTTP communication
- Environment variables for Java backend integration
- `send_signal_to_java()` function to forward signals

**Configuration (.env):**
```env
JAVA_BACKEND_URL=http://localhost:8081
SEND_SIGNALS_TO_JAVA=true
```

**Signal Data Format Sent to Java:**
```json
{
  "pair": "ARBUSDT.P",
  "setup_type": "LONG",
  "entry": 1.098,
  "leverage": 20.0,
  "tp1": 1.112,
  "tp2": 1.128,
  "tp3": 1.145,
  "tp4": 1.165,
  "stop_loss": 1.075,
  "timestamp": "2025-11-02T12:30:00+00:00",
  "full_message": "..."
}
```

**Workflow:**
```python
1. Receive message from Telegram
2. Parse signals (entry, TP1-4, SL, leverage)
3. Create data object
4. Queue to database worker
5. Broadcast to WebSocket clients
6. Send to Java backend (NEW)
   ├─ If successful: log confirmation
   ├─ If connection fails: log warning
   └─ If error: log error but continue operation
7. Continue listening for next message
```

---

### 2. Java Backend (Spring Boot - Port 8081)

#### A. Enhanced TradeService

**Changes to `TradeService.java`:**

1. **LIMIT Orders (Instead of MARKET)**
   - Places LIMIT orders at the exact entry price
   - Good Till Cancel (GTC) timeInForce
   - Waits for price to reach entry before executing
   - More control over execution price

2. **Auto Quantity Calculation**
   - New method: `calculateQuantity()`
   - If quantity provided: use it
   - If not provided: auto-calculate from 50% of balance / leverage
   - Prevents over-leveraging the account

3. **Order Placement Logic**
   ```java
   1. Check minimum balance ($10 USDT)
   2. Set leverage on trading symbol
   3. Place LIMIT order at entry price (GTC)
   4. Place STOP_MARKET order at stop loss
   5. Place TAKE_PROFIT_MARKET orders for TP1-4
   6. Save order IDs and status to database
   7. Return execution response
   ```

#### B. New SignalWebhookController

**Endpoint:** `POST /api/webhook/signal`

**Functionality:**
- Receives trading signals from Python backend
- Validates signal data
- Saves signal to database
- Auto-executes trade if enabled
- Returns execution status

**Request Body:**
```json
{
  "pair": "ARBUSDT.P",
  "setup_type": "LONG",
  "entry": 1.098,
  "leverage": 20.0,
  "tp1": 1.112,
  "tp2": 1.128,
  "tp3": 1.145,
  "tp4": 1.165,
  "stop_loss": 1.075,
  "timestamp": "2025-11-02T12:30:00+00:00",
  "full_message": "...",
  "quantity": null
}
```

**Response (Success):**
```json
{
  "status": "success",
  "message": "Signal received and trade executed",
  "signal_id": 123,
  "trade_status": "SUCCESS",
  "trade_id": 456,
  "trade_message": "Order placed successfully on Binance"
}
```

**Response (Disabled):**
```json
{
  "status": "success",
  "message": "Signal received but auto-execution is disabled",
  "signal_id": 123
}
```

**Health Check:** `GET /api/webhook/health`
```json
{
  "status": "healthy",
  "auto_execute_enabled": true,
  "timestamp": 1698934200000
}
```

#### C. New AutoTradeExecutor Service

**Features:**
- Async trade execution: `executeTradeAsync(signal)`
- Sync trade execution: `executeTradeSync(signal)`
- Execute pending signals: `executePendingSignals()`
- Configurable max concurrent trades
- Signal validation before execution
- Execution statistics tracking

**Configuration:**
```properties
trade.max-concurrent-trades=5
trade.min-signal-confidence=0.8
```

#### D. Updated Application Configuration

**File:** `application.properties`

**New Properties:**
```properties
# Auto-execute trades when signals are received
trade.auto-execute=true

# Maximum concurrent trades to execute
trade.max-concurrent-trades=5

# Minimum signal confidence (0.0-1.0)
trade.min-signal-confidence=0.8

# Default position sizing (% of account)
trade.position-size-percent=50

# Minimum USDT balance required
trade.min-balance=10

# Webhook endpoints
webhook.signal-endpoint=/api/webhook/signal
webhook.health-endpoint=/api/webhook/health
```

**Environment Variables Required:**
```bash
BINANCE_API_KEY=your_api_key
BINANCE_API_SECRET=your_api_secret
BINANCE_TESTNET_ENABLED=true  # Set to false for live trading
TRADE_AUTO_EXECUTE=true       # Enable/disable auto execution
```

---

## Complete Workflow Example

### Step 1: Signal Received from Telegram
```
Telegram: "#ARBUSDT.P LONG SETUP 🟩
Entry: 1.098
TP1: 1.112
TP2: 1.128
TP3: 1.145
TP4: 1.165
Leverage: 20x
SL: 1.075"
```

### Step 2: Python Backend Processes
```python
✓ Connects to Telegram group
✓ Receives message
✓ Parses: pair, setup_type, entry, TPs, SL, leverage
✓ Creates signal data dict
✓ Saves to PostgreSQL
✓ Broadcasts to WebSocket clients
✓ Sends to Java backend: POST http://localhost:8081/api/webhook/signal
```

### Step 3: Java Backend Executes
```java
✓ Receives signal via webhook
✓ Validates: pair, entry, leverage required
✓ Maps to ExecuteTradeRequest
✓ Fetches USDT balance (checks >= $10)
✓ Sets leverage on symbol
✓ Places LIMIT order at $1.098
✓ Places STOP_MARKET at $1.075 (SL)
✓ Places TAKE_PROFIT_MARKET at:
  - $1.112 (TP1)
  - $1.128 (TP2)
  - $1.145 (TP3)
  - $1.165 (TP4)
✓ Saves trade to database with status=OPEN
✓ Returns success response
```

### Step 4: Python Backend Receives Confirmation
```python
✓ Receives 200 OK from Java backend
✓ Logs: "✅ Signal sent to Java backend: ARBUSDT.P"
```

### Step 5: User Sees Real-Time Updates
```
Frontend (WebSocket listener)
✓ Receives signal broadcast from Python backend
✓ Displays signal in dashboard
✓ Shows entry: $1.098, TP1-4, SL
✓ Shows status: PENDING → OPEN
✓ Updates as TP levels are hit
```

---

## Port Configuration

| Service | Port | Purpose |
|---------|------|---------|
| Python Backend (HTTP) | 8082 | Health checks, WebSocket server |
| Python Backend (WebSocket) | 6789 | Real-time data to frontend |
| Java Backend | 8081 | REST API, webhook receiver |
| Frontend | 3000 | User interface |

---

## Environment Setup

### 1. Python Backend (.env)
```env
# Telegram Configuration
API_ID=your_api_id
API_HASH=your_api_hash
GROUP_ID=your_group_id
SESSION_STRING=your_session_string

# Database
DATABASE_URL=postgresql://...

# Ports
HTTP_PORT=8082
WS_PORT=6789

# Java Backend Integration
JAVA_BACKEND_URL=http://localhost:8081
SEND_SIGNALS_TO_JAVA=true
```

### 2. Java Backend (Environment Variables)
```bash
# Binance API
export BINANCE_API_KEY=your_key
export BINANCE_API_SECRET=your_secret
export BINANCE_TESTNET_ENABLED=true

# Trading Configuration
export TRADE_AUTO_EXECUTE=true
export TRADE_MAX_CONCURRENT=5
export TRADE_MIN_CONFIDENCE=0.8

# Database (from application.properties)
# Or override with environment variables
```

### 3. Run Services

**Terminal 1 - Python Backend:**
```bash
cd backend
python telethon_message_collector.py
```

**Terminal 2 - Java Backend:**
```bash
cd Intelligent_Crypto_User_Management
mvn spring-boot:run
```

**Terminal 3 - Frontend:**
```bash
cd frontend
npm run dev
```

---

## Debugging & Monitoring

### Python Backend Logs
```
[OK] Connected to PostgreSQL
[OK] Tables ready (recreated with correct schema)
[OK] Signal queued: ARBUSDT.P LONG
✅ Signal sent to Java backend: ARBUSDT.P
[INFO] Market message saved: ...
```

### Java Backend Logs
```
📡 Received signal from Python backend: ARBUSDT.P
✅ Signal saved to database with ID: 123
🚀 Executing trade: BUY ARBUSDT.P @ $1.098
✅ Trade record created with ID: 456
💰 USDT Balance: 5000
⚙️ Leverage set to 20x for ARBUSDT.P
📍 Placing LIMIT order: BUY ARBUSDT.P @ $1.098 Qty=125
✅ LIMIT Order placed: BUY ARBUSDT.P Qty=125 Status=OPEN
🛑 Stop-Loss placed: ...
📈 TP1 placed: ...
✅ All orders placed for ARBUSDT.P
```

### Check Health
```bash
# Python Backend
curl http://localhost:8082/health

# Java Backend
curl http://localhost:8081/api/webhook/health

# Response:
{
  "status": "healthy",
  "auto_execute_enabled": true,
  "timestamp": 1698934200000
}
```

---

## Disable Auto-Execution (For Testing)

If you want to receive signals but NOT auto-execute trades:

**Python Backend:**
```env
SEND_SIGNALS_TO_JAVA=false
```

**Java Backend:**
```properties
trade.auto-execute=false
```

Then manually execute trades via:
```bash
curl -X POST http://localhost:8081/api/trades/execute \
  -H "Content-Type: application/json" \
  -d '{
    "pair": "ARBUSDT.P",
    "side": "BUY",
    "entry": 1.098,
    "quantity": 125,
    "leverage": 20,
    "stopLoss": 1.075,
    "tp1": 1.112,
    "tp2": 1.128,
    "tp3": 1.145,
    "tp4": 1.165
  }'
```

---

## Testnet vs Live Trading

**IMPORTANT:** Default is TESTNET mode!

### To use Testnet (Default - SAFE):
```bash
export BINANCE_TESTNET_ENABLED=true
```

### To use Live Trading (REAL MONEY):
```bash
export BINANCE_TESTNET_ENABLED=false
```

**WARNING:** Live trading will execute real trades with actual funds. Test thoroughly on testnet first!

---

## Security Considerations

1. **API Keys:** Store in environment variables, never in code
2. **JWT Tokens:** Use for API authentication
3. **HTTPS:** Use in production
4. **Signal Validation:** Only process valid signals with required fields
5. **Rate Limiting:** Consider adding rate limits to webhook
6. **Position Sizing:** Uses conservative 50% of balance default
7. **Minimum Balance:** Requires $10 USDT before trading

---

## Troubleshooting

### Issue: "Cannot connect to Java backend"
**Solution:**
- Ensure Java backend is running on port 8081
- Check: `curl http://localhost:8081/api/webhook/health`
- Verify JAVA_BACKEND_URL in .env

### Issue: "Insufficient balance"
**Solution:**
- Add funds to Binance account
- Minimum $10 USDT required per trade
- Use testnet for testing

### Issue: "Missing required fields"
**Solution:**
- Ensure Telegram signal includes all required data
- Check signal parsing logic in Python backend
- Enable debug logging for details

### Issue: "Order not placed on Binance"
**Solution:**
- Verify Binance API keys are correct
- Check if trading pair is enabled on Binance Futures
- Verify leverage settings match
- Check Binance account restrictions

---

## Next Steps

1. **Setup Binance API Keys:**
   - Go to https://www.binance.com/en/account/api-management
   - Create API key with Futures trading enabled
   - Save API key and secret

2. **Test on Testnet First:**
   - Keep BINANCE_TESTNET_ENABLED=true
   - Send test signals from Telegram
   - Verify orders appear in testnet account

3. **Monitor Trading:**
   - Watch logs for errors
   - Check WebSocket updates in real-time
   - Monitor Binance account

4. **Go Live (When Ready):**
   - Fund Binance Futures account
   - Set BINANCE_TESTNET_ENABLED=false
   - Start receiving real signals
   - Monitor carefully first few trades

---

## API Reference

### Python Backend Endpoints

**Health Check:**
```
GET http://localhost:8082/health
```

**WebSocket:**
```
WS ws://localhost:6789/ws
```

### Java Backend Endpoints

**Webhook Signal:**
```
POST /api/webhook/signal
```

**Webhook Health:**
```
GET /api/webhook/health
```

**Execute Trade (Manual):**
```
POST /api/trades/execute
Authorization: Bearer {jwt_token}
```

**Get Open Trades:**
```
GET /api/trades/status/open
Authorization: Bearer {jwt_token}
```

**Close Position:**
```
POST /api/trades/close/{tradeId}
Authorization: Bearer {jwt_token}
```

---

## Summary

The automatic trade execution system is now fully implemented:

✅ **Python Backend** - Listens to Telegram, parses signals, sends to Java
✅ **Java Backend** - Receives signals, executes LIMIT orders, manages trades
✅ **Binance Integration** - Places orders, manages leverage, tracks positions
✅ **Database** - Stores signals and trades for history
✅ **Frontend** - Real-time WebSocket updates
✅ **Configuration** - Fully configurable via environment variables

**Ready for testing and deployment!**
