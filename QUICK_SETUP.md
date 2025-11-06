# Quick Setup Guide - Automatic Trade Execution

## Prerequisites

- Python 3.11+
- Java 17+
- Maven 3.9+
- Binance Futures account
- PostgreSQL (Neon Cloud DB)
- Node.js 18+ (for frontend)

## Step 1: Get Binance API Keys

1. Go to https://www.binance.com/en/account/api-management
2. Click "Create API" → Choose name (e.g., "Trading Bot")
3. Select restrictions:
   - ✅ Spot & Margin Trading
   - ✅ Futures Trading (Enable Futures trading)
   - Add API Key IP restrictions (RECOMMENDED)
4. Copy API Key and Secret Key
5. **Keep these secret!** Store in .env file, never in code

## Step 2: Configure Python Backend

**File:** `backend/.env`

```env
# Telegram API (already configured)
API_ID=26883812
API_HASH=4e05a57633f9c55768f50bcbd6a9b5f8
GROUP_ID=-4888908773
SESSION_STRING=...

# PostgreSQL (already configured)
DATABASE_URL=postgresql://...

# Ports
HTTP_PORT=8082
WS_PORT=6789

# Java Backend Integration
JAVA_BACKEND_URL=http://localhost:8081
SEND_SIGNALS_TO_JAVA=true
```

**No changes needed!** Already configured.

## Step 3: Configure Java Backend

**File:** `Intelligent_Crypto_User_Management/src/main/resources/application.properties`

Already configured! Just needs environment variables.

**Set Environment Variables:**

### Windows (Command Prompt)
```cmd
setx BINANCE_API_KEY "your_api_key_here"
setx BINANCE_API_SECRET "your_api_secret_here"
setx BINANCE_TESTNET_ENABLED "true"
setx TRADE_AUTO_EXECUTE "true"
```

Then restart Command Prompt.

### Windows (PowerShell)
```powershell
$env:BINANCE_API_KEY="your_api_key_here"
$env:BINANCE_API_SECRET="your_api_secret_here"
$env:BINANCE_TESTNET_ENABLED="true"
$env:TRADE_AUTO_EXECUTE="true"
```

### macOS/Linux
```bash
export BINANCE_API_KEY="your_api_key_here"
export BINANCE_API_SECRET="your_api_secret_here"
export BINANCE_TESTNET_ENABLED="true"
export TRADE_AUTO_EXECUTE="true"
```

## Step 4: Install Dependencies

### Python Backend
```bash
cd backend
pip install -r requirements.txt
```

If `requirements.txt` doesn't exist, install manually:
```bash
pip install telethon python-dotenv asyncpg aiohttp websockets requests
```

### Java Backend
```bash
cd Intelligent_Crypto_User_Management
mvn clean install
```

### Frontend
```bash
cd frontend
npm install
```

## Step 5: Start Services

**Terminal 1 - Python Backend:**
```bash
cd backend
python telethon_message_collector.py
```

Expected output:
```
==================================================
Telegram Signal Bot Starting...
==================================================
2025-11-02 11:09:41,742 - INFO - [OK] Connected to PostgreSQL
2025-11-02 11:09:41,915 - INFO - [OK] Tables ready
👂 Listening for Telegram messages...
```

**Terminal 2 - Java Backend:**
```bash
cd Intelligent_Crypto_User_Management
mvn spring-boot:run
```

Expected output:
```
Spring Boot application started successfully
...
2025-11-02 11:10:00,000 - INFO - [OK] Connected to PostgreSQL
2025-11-02 11:10:00,500 - INFO - ✅ Binance Futures client initialized in TESTNET mode
```

**Terminal 3 - Frontend:**
```bash
cd frontend
npm run dev
```

Expected output:
```
  ▲ Next.js 14.0.0
  - Local:        http://localhost:3000
```

## Step 6: Test the System

### 1. Check Health Endpoints

**Python Backend:**
```bash
curl http://localhost:8082/health
```

**Java Backend:**
```bash
curl http://localhost:8081/api/webhook/health
```

Expected response:
```json
{
  "status": "healthy",
  "auto_execute_enabled": true,
  "timestamp": 1698934200000
}
```

### 2. Send Test Signal (Manual)

**Using curl:**
```bash
curl -X POST http://localhost:8081/api/webhook/signal \
  -H "Content-Type: application/json" \
  -d '{
    "pair": "ARBUSDT.P",
    "setup_type": "LONG",
    "entry": 1.098,
    "leverage": 20,
    "tp1": 1.112,
    "tp2": 1.128,
    "tp3": 1.145,
    "tp4": 1.165,
    "stop_loss": 1.075,
    "timestamp": "2025-11-02T12:30:00Z",
    "full_message": "Test signal"
  }'
```

Expected response:
```json
{
  "status": "success",
  "message": "Signal received and trade executed",
  "signal_id": 1,
  "trade_status": "SUCCESS",
  "trade_id": 1,
  "trade_message": "Order placed successfully on Binance"
}
```

### 3. Check Binance Testnet

1. Go to https://testnet.binancefuture.com
2. Login with Binance account
3. Check "Positions" → Should see open ARBUSDT.P position
4. Check "Orders" → Should see LIMIT order at 1.098 + SL + TPs

## Step 7: Send Real Telegram Signal

1. Go to your Telegram trading group
2. Send a properly formatted signal:
```
#ARBUSDT.P LONG SETUP 🟩

Entry: 1.098
Leverage: 20x
TP1: 1.112
TP2: 1.128
TP3: 1.145
TP4: 1.165
SL: 1.075
```

**Watch the logs:**

**Python Backend:**
```
2025-11-02 12:30:00,000 - INFO - [OK] Signal queued: ARBUSDT.P LONG
✅ Signal sent to Java backend: ARBUSDT.P
```

**Java Backend:**
```
📡 Received signal from Python backend: ARBUSDT.P
✅ Signal saved to database with ID: 2
🚀 Executing trade: BUY ARBUSDT.P @ $1.098
✅ LIMIT Order placed: BUY ARBUSDT.P Qty=125 Status=OPEN
🛑 Stop-Loss placed: ...
📈 TP1 placed: ...
✅ All orders placed for ARBUSDT.P
```

## Step 8: Monitor in Frontend

1. Open http://localhost:3000
2. Check "Telegram Signals" dashboard
3. See the signal in real-time
4. See trade status: PENDING → OPEN
5. Watch as price approaches entry/TP levels

## Troubleshooting

### "Cannot connect to Java backend"
```bash
# Check if Java backend is running
curl http://localhost:8081/api/webhook/health

# If fails, check Java backend logs
# Ensure BINANCE_API_KEY is set
echo %BINANCE_API_KEY%  # Windows
echo $BINANCE_API_KEY   # macOS/Linux
```

### "Insufficient balance"
- Add funds to Binance Futures testnet
- Minimum: $10 USDT required
- Or reduce leverage in signal

### "Symbol not found"
- Ensure symbol exists on Binance Futures (e.g., ARBUSDT.P)
- Check Binance Futures market
- Symbol must support Perpetual futures

### "Missing API key"
```bash
# Verify API key is set
echo %BINANCE_API_KEY%  # Windows
echo $BINANCE_API_KEY   # macOS/Linux

# If empty, set it again
setx BINANCE_API_KEY "your_key"  # Windows
export BINANCE_API_KEY="your_key"  # macOS/Linux
```

## Testnet vs Live

### Currently Using: TESTNET ✅

- No real money is at risk
- Practice trading safely
- Test signals and system

### To Switch to Live Trading:

**⚠️ WARNING: Real money at risk!**

1. Ensure system works perfectly on testnet first
2. Add funds to Binance Futures account
3. Change environment variable:
```bash
setx BINANCE_TESTNET_ENABLED "false"  # Windows
# OR
export BINANCE_TESTNET_ENABLED="false"  # macOS/Linux
```

4. **VERY CAREFULLY** send first signal with small position
5. Monitor closely
6. Gradually increase as confidence grows

## Configuration Options

### Auto-Execute Trades
```bash
# Enable (default)
export TRADE_AUTO_EXECUTE="true"

# Disable (manually execute)
export TRADE_AUTO_EXECUTE="false"
```

### Max Concurrent Trades
```bash
# Default: 5 trades simultaneously
export TRADE_MAX_CONCURRENT="5"

# Change to 3 (more conservative)
export TRADE_MAX_CONCURRENT="3"
```

### Position Size
```bash
# Default: 50% of balance
export TRADE_POSITION_SIZE="50"

# Conservative: 25%
export TRADE_POSITION_SIZE="25"

# Aggressive: 75%
export TRADE_POSITION_SIZE="75"
```

## What Happens When You Send a Signal

1. **Telegram** → Signal posted in group
2. **Python Backend** → Receives, parses, saves to DB
3. **Python Backend** → Sends to Java Backend (HTTP POST)
4. **Java Backend** → Receives, saves signal
5. **Java Backend** → Auto-executes:
   - Checks balance (must have $10+)
   - Calculates position size
   - Places LIMIT order at entry price
   - Places STOP_MARKET at SL
   - Places TAKE_PROFIT_MARKET at TP1-4
   - Saves trade with status=OPEN
   - Returns success
6. **Python Backend** → Receives confirmation
7. **WebSocket** → Broadcasts signal to frontend
8. **Frontend** → Shows signal and trade status
9. **Binance** → Monitors market for order fills

## Success Checklist

- [x] Binance API keys obtained
- [x] Environment variables set
- [x] Python backend running
- [x] Java backend running
- [x] Health checks passing
- [x] Test signal created successfully
- [x] Orders visible in Binance Testnet
- [x] Frontend showing signals
- [x] Ready for live trading (if testnet works perfectly)

## Support

Check logs for any errors:

**Python Backend Logs:**
```bash
# Last 20 lines
tail -f backend/app.log

# Or in terminal output
```

**Java Backend Logs:**
```bash
# In Spring Boot terminal output
# Or check log files in Intelligent_Crypto_User_Management/logs/
```

## Next: Go Live

Once testnet is working perfectly:

1. Switch BINANCE_TESTNET_ENABLED to false
2. Add real funds to Binance Futures
3. Start with 1-2 small test trades
4. Monitor closely
5. Gradually increase as confidence grows
6. Never risk more than you can afford to lose

---

**You're all set! The system is ready to automatically execute trades from Telegram signals.** 🚀

For detailed documentation, see: `AUTOMATIC_TRADE_EXECUTION_GUIDE.md`
