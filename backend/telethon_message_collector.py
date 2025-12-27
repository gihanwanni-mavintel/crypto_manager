import os
import asyncio
import json
import logging
import re
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from telethon import TelegramClient, events
from telethon.sessions import StringSession
from dotenv import load_dotenv
import websockets
from websockets.exceptions import ConnectionClosed
import asyncpg
from aiohttp import web
import requests

# ----------------------------
# Logging
# ----------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler("app.log", encoding='utf-8')
    ]
)
logger = logging.getLogger(__name__)

# ----------------------------
# Environment
# ----------------------------
load_dotenv()
API_ID = int(os.getenv("API_ID", 0))
API_HASH = os.getenv("API_HASH", "")
GROUP_ID = int(os.getenv("GROUP_ID", 0))
SESSION_STRING = os.getenv("SESSION_STRING", "")
DATABASE_URL = os.getenv("DATABASE_URL", "")
HTTP_PORT = int(os.getenv("HTTP_PORT", 8082))
WS_PORT = int(os.getenv("WS_PORT", 6789))
JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8081")
SEND_SIGNALS_TO_JAVA = os.getenv("SEND_SIGNALS_TO_JAVA", "true").lower() == "true"

if not API_ID or not API_HASH or not SESSION_STRING or not DATABASE_URL or not GROUP_ID:
    logger.error("[ERROR] Missing critical environment variables. Exiting.")
    exit(1)

# ----------------------------
# Health check
# ----------------------------
async def http_handler(request):
    return web.Response(text="[OK] Telegram Signal Bot is running")

# ----------------------------
# WebSocket (aiohttp version)
# ----------------------------
connected_clients = set()
MAX_CONNECTIONS = 100

async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    if len(connected_clients) >= MAX_CONNECTIONS:
        await ws.close(code=1008, message=b"Max connections reached")
        return ws

    connected_clients.add(ws)
    logger.info(f"WebSocket client connected. Total: {len(connected_clients)}")

    try:
        async for msg in ws:
            if msg.type == web.WSMsgType.TEXT:
                logger.debug(f"WS client said: {msg.data}")
    finally:
        connected_clients.discard(ws)
        logger.info(f"WebSocket client disconnected. Total: {len(connected_clients)}")

    return ws

async def send_to_clients(data):
    if connected_clients:
        message = json.dumps(data, default=str)
        dead_clients = set()
        for client in connected_clients:
            try:
                await client.send_str(message)  # 👈 aiohttp WS uses send_str
            except Exception:
                dead_clients.add(client)
        connected_clients.difference_update(dead_clients)


# ----------------------------
# Timezone Helper
# ----------------------------
def ensure_timezone_aware(dt):
    """Convert datetime to UTC-aware datetime. Always returns offset-aware datetime."""
    if dt is None:
        return None
    if dt.tzinfo is None:
        # Naive datetime - assume it's UTC
        return dt.replace(tzinfo=timezone.utc)
    else:
        # Already aware - convert to UTC
        return dt.astimezone(timezone.utc)


# ----------------------------
# Database
# ----------------------------
db_pool = None
signal_queue = asyncio.Queue()

async def init_db():
    global db_pool
    db_pool = await asyncpg.create_pool(dsn=DATABASE_URL)
    logger.info("[OK] Connected to PostgreSQL")
    logger.info("[OK] Using existing tables (signal_messages, market_messages)")
    # Note: Tables already exist in Neon database with updated schema
    # No table recreation needed

async def signal_db_worker(batch_size=50):
    while True:
        batch = []
        try:
            logger.debug("[DB WORKER] Waiting for signals in queue...")
            msg = await signal_queue.get()
            logger.info(f"\n[DB WORKER] Received 1 signal from queue. Pair: {msg.get('pair')}")
            batch.append(msg)

            while len(batch) < batch_size:
                try:
                    msg = signal_queue.get_nowait()
                    logger.debug(f"[DB WORKER] Added signal to batch. Pair: {msg.get('pair')}. Batch size: {len(batch)}")
                    batch.append(msg)
                except asyncio.QueueEmpty:
                    logger.debug(f"[DB WORKER] Queue is empty. Batch ready with {len(batch)} signal(s)")
                    break

            logger.info(f"[DB WORKER] Processing batch of {len(batch)} signal(s)...")
            async with db_pool.acquire() as conn:
                values = []
                for idx, m in enumerate(batch, 1):
                    ts = ensure_timezone_aware(m["timestamp"])
                    logger.debug(f"[DB WORKER] Converting signal {idx}: {m['pair']} {m['setup_type']}")
                    values.append((
                        m["pair"],
                        m["setup_type"],
                        float(m["entry"]) if m["entry"] is not None else None,
                        float(m["take_profit"]) if m["take_profit"] is not None else None,
                        float(m["stop_loss"]) if m["stop_loss"] is not None else None,
                        ts,
                        m["full_message"],
                        "TELEGRAM"  # channel - default value for Telegram signals
                    ))

                if values:
                    logger.info(f"[DB INSERT] Inserting {len(values)} signal(s) into signal_messages table...")
                    await conn.executemany("""
                    INSERT INTO signal_messages (
                        pair, setup_type, entry, take_profit, stop_loss,
                        timestamp, full_message, channel
                    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
                    ON CONFLICT (full_message) DO NOTHING
                    """, values)
                    logger.info(f"[DB INSERT] [OK] Successfully inserted {len(values)} signal(s)")
                    logger.info(f"{'='*60}\n")
        except asyncio.CancelledError:
            logger.info("[DB WORKER] Worker cancelled, shutting down...")
            break
        except Exception as e:
            logger.error(f"[DB WORKER ERROR] {e}", exc_info=True)

async def save_market(data):
    ts = ensure_timezone_aware(data["timestamp"])
    async with db_pool.acquire() as conn:
        await conn.execute("""
            INSERT INTO market_messages (sender, text, timestamp)
            VALUES ($1,$2,$3)
        """, data["sender"], data["text"], ts)

def send_signal_to_java(signal_data):
    """Send signal to Java backend for automatic trade execution"""
    if not SEND_SIGNALS_TO_JAVA:
        logger.info("[SKIP] Signal forwarding to Java backend is DISABLED (check .env SEND_SIGNALS_TO_JAVA)")
        return

    try:
        url = f"{JAVA_BACKEND_URL}/api/webhook/signal"
        logger.info(f"\n[JAVA SEND] Target URL: {url}")
        logger.info(f"[JAVA SEND] Payload: Pair={signal_data.get('pair')}, Setup={signal_data.get('setup_type')}, Entry={signal_data.get('entry')}")

        # Convert Decimal values to float for JSON serialization
        json_data = json.dumps(signal_data, default=str)
        logger.debug(f"[JAVA SEND] JSON payload size: {len(json_data)} bytes")

        logger.info(f"[JAVA SEND] Sending HTTP POST request to Java backend...")
        response = requests.post(
            url,
            data=json_data,
            headers={"Content-Type": "application/json"},
            timeout=10
        )

        logger.info(f"[JAVA RESPONSE] Status Code: {response.status_code}")
        if response.status_code == 200:
            logger.info(f"[JAVA RESPONSE] [SUCCESS] Signal sent to Java backend: {signal_data.get('pair')}")
            logger.info(f"[JAVA RESPONSE] Response body: {response.text[:200]}")
        elif response.status_code >= 400:
            logger.error(f"[JAVA RESPONSE] [ERROR] {response.status_code}: {response.text}")
        else:
            logger.warning(f"[JAVA RESPONSE] [WARNING] Unexpected status {response.status_code}: {response.text}")

    except requests.exceptions.ConnectionError as e:
        logger.error(f"[JAVA ERROR] [FAILED] Cannot connect to Java backend at {JAVA_BACKEND_URL}")
        logger.error(f"[JAVA ERROR] Connection Error: {e}")
        logger.error(f"[JAVA ERROR] Please ensure Java backend is running on port 8081")
    except requests.exceptions.Timeout:
        logger.error(f"[JAVA ERROR] [TIMEOUT] Request timeout - Java backend did not respond within 10 seconds")
    except Exception as e:
        logger.error(f"[JAVA ERROR] [FAILED] Error sending signal to Java backend: {e}", exc_info=True)

# ----------------------------
# Helpers
# ----------------------------
def parse_decimal(value: str):
    if value is None:
        return None
    try:
        return Decimal(value.replace(",", "").strip())
    except (InvalidOperation, AttributeError):
        return None

def calculate_tp_sl(entry: Decimal, direction: str):
    """
    Calculate TP and SL based on entry price and direction

    LONG:
      TP = entry * 1.025  (entry + 2.5%)
      SL = entry * 0.95   (entry - 5%)

    SHORT:
      TP = entry * 0.975  (entry - 2.5%)
      SL = entry * 1.05   (entry + 5%)
    """
    if entry is None:
        return None, None

    try:
        entry_decimal = Decimal(str(entry))

        if direction == "LONG":
            tp = entry_decimal * Decimal("1.025")  # +2.5%
            sl = entry_decimal * Decimal("0.95")   # -5%
        elif direction == "SHORT":
            tp = entry_decimal * Decimal("0.975")  # -2.5%
            sl = entry_decimal * Decimal("1.05")   # +5%
        else:
            logger.warning(f"Unknown direction: {direction}, cannot calculate TP/SL")
            return None, None

        logger.info(f"[CALC] Direction={direction}, Entry={entry}, TP={tp}, SL={sl}")
        return tp, sl
    except Exception as e:
        logger.error(f"Error calculating TP/SL: {e}")
        return None, None

def extract_value(label, lines):
    pattern = re.compile(rf"{label}\s*:\s*(.+)", re.IGNORECASE)
    for line in lines:
        match = pattern.search(line)
        if match:
            # Extract value and remove emojis, parentheses content, and extra text
            value = match.group(1).strip().replace("•", "").split("☠️")[0].strip()
            # Remove anything in parentheses (like "(CMP)")
            value = re.sub(r'\([^)]*\)', '', value).strip()
            return value
    return None

# ----------------------------
# Telegram
# ----------------------------
async def run_telegram_client():
    client = TelegramClient(StringSession(SESSION_STRING), API_ID, API_HASH)
    await client.start()
    me = await client.get_me()
    logger.info(f"[USER] Connected as: {me.first_name} (ID: {me.id})")

    @client.on(events.NewMessage(chats=GROUP_ID))
    async def handler(event):
        try:
            text = event.message.message
            date = ensure_timezone_aware(event.message.date)
            lines = [line.strip() for line in text.splitlines() if line.strip()]

            logger.info(f"\n{'='*60}")
            logger.info(f"[RECEIVED MESSAGE] Timestamp: {date}")
            logger.info(f"Message content (first 100 chars): {text[:100]}...")
            logger.info(f"Total lines in message: {len(lines)}")

            # Detect signal: Must have # symbol, entry, and direction (LONG/SHORT)
            # TP and SL are now calculated, so we don't require them in the message
            is_signal = (
                any(line.startswith('#') for line in lines) and
                any("entry" in line.lower() for line in lines) and
                (any("long" in line.lower() for line in lines) or any("short" in line.lower() for line in lines))
            )

            logger.info(f"[SIGNAL DETECTION] Is Signal: {is_signal}")
            if is_signal:
                logger.info(f"[DETAILS] Detected as SIGNAL [OK]")

            if is_signal:
                first_line = lines[0] if lines else ""
                pair = first_line.split()[0].strip("#") if first_line else "UNKNOWN"
                setup_type = "LONG" if "LONG" in first_line.upper() else "SHORT" if "SHORT" in first_line.upper() else "UNKNOWN"

                logger.info(f"\n[PARSING] Starting to parse signal...")
                logger.info(f"[PARSING] First line: '{first_line}'")
                logger.info(f"[PARSING] Extracted PAIR: {pair}")
                logger.info(f"[PARSING] Extracted SETUP_TYPE: {setup_type}")

                # Extract only ENTRY from telegram message
                entry = parse_decimal(extract_value("Entry", lines))
                logger.info(f"[PARSING] Extracted ENTRY: {entry}")

                # Calculate TP and SL based on entry and direction
                take_profit, stop_loss = calculate_tp_sl(entry, setup_type)

                # Create simplified data object
                data = {
                    "pair": pair,
                    "setup_type": setup_type,
                    "entry": entry,
                    "take_profit": take_profit,
                    "stop_loss": stop_loss,
                    "timestamp": date,
                    "full_message": text
                }

                logger.info(f"\n[DATA OBJECT] Created signal data object:")
                logger.info(f"  Pair: {data['pair']}")
                logger.info(f"  Setup: {data['setup_type']}")
                logger.info(f"  Entry: {data['entry']}")
                logger.info(f"  Take Profit (Calculated): {data['take_profit']}")
                logger.info(f"  Stop Loss (Calculated): {data['stop_loss']}")

                logger.info(f"\n[QUEUE] Adding signal to database queue...")
                await signal_queue.put(data)
                logger.info(f"[QUEUE] [OK] Signal queued successfully. Queue size: {signal_queue.qsize()}")

                logger.info(f"[WEBSOCKET] Broadcasting to {len(connected_clients)} connected clients...")
                await send_to_clients(data)
                logger.info(f"[WEBSOCKET] [OK] Broadcast complete")

                # Send signal to Java backend for automatic trade execution
                logger.info(f"[JAVA BACKEND] Attempting to send signal to Java backend...")
                send_signal_to_java(data)
                logger.info(f"[JAVA BACKEND] Signal sent (see previous logs for response)")
                logger.info(f"[OK] Signal fully processed: {pair} {setup_type} [SUCCESS]")
                logger.info(f"{'='*60}\n")
            else:
                logger.info(f"\n[NON-SIGNAL] Detected as MARKET MESSAGE (not a signal)")
                sender = event.message.sender.first_name if event.message.sender else "Unknown"
                logger.info(f"[NON-SIGNAL] Sender: {sender}")
                logger.info(f"[NON-SIGNAL] Message content: {text[:100]}...")
                data = {"sender": sender, "text": text, "timestamp": date}

                logger.info(f"[NON-SIGNAL] Saving to market_messages table...")
                await save_market(data)
                logger.info(f"[NON-SIGNAL] [OK] Saved to database")

                logger.info(f"[NON-SIGNAL] Broadcasting to {len(connected_clients)} connected clients...")
                await send_to_clients(data)
                logger.info(f"[NON-SIGNAL] [OK] Broadcast complete")
                logger.info(f"{'='*60}\n")
        except Exception as e:
            logger.error(f"[ERROR] Error processing message: {e}")

    logger.info("Listening for Telegram messages...")
    await client.run_until_disconnected()

# ----------------------------
# Main
# ----------------------------
async def main():
    await init_db()
    db_worker_task = asyncio.create_task(signal_db_worker())

    app = web.Application()
    app.router.add_get('/', http_handler)
    app.router.add_get('/health', http_handler)
    app.router.add_get("/ws", websocket_handler)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", HTTP_PORT)
    await site.start()
    logger.info(f"HTTP health server started on port {HTTP_PORT}")


    logger.info(f"WebSocket server started on port {WS_PORT}")

    try:
        await run_telegram_client()
    except Exception as e:
        logger.error(f"Fatal error: {e}")
    finally:
        await runner.cleanup()
        db_worker_task.cancel()
        await asyncio.sleep(0.1)
        logger.info("[STOP] Shutdown complete")

if __name__ == "__main__":
    print("="*50)
    print("Telegram Signal Bot Starting...")
    print("="*50)
    asyncio.run(main())