# Frontend Integration Guide

## Overview
This is the new Crypto Position Manager frontend that has been integrated with your existing backend services. The frontend includes:

- Real-time trading signals display via WebSocket
- Position management interface
- Manual trading execution
- Trade history tracking
- Account summary and analytics
- JWT-based authentication

## Backend Services

The frontend is configured to connect to:

1. **Authentication Service** (Spring Boot)
   - Endpoint: `http://localhost:8080/auth`
   - Methods: POST `/auth/login`, GET `/auth/health`

2. **Trading Service (Binance)**
   - Endpoint: `http://localhost:8080/api/binance`
   - Methods:
     - GET `/userTrades?symbol={symbol}`
     - GET `/positionRisk?symbol={symbol}`
     - GET `/account`
     - GET `/executionInfo?symbol={symbol}`
     - GET `/accountInfo`
     - POST `/executeTrade` (custom endpoint)
     - POST `/closePosition/{id}` (custom endpoint)

3. **WebSocket Connection**
   - URL: `wss://telegramsignals-production.up.railway.app`
   - Purpose: Real-time signal streaming

## Environment Configuration

The following environment variables are configured in `.env.local`:

```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=wss://telegramsignals-production.up.railway.app
```

### For Production Deployment

Update `.env.local` or set environment variables:

```
NEXT_PUBLIC_API_URL=https://your-backend-domain.com
NEXT_PUBLIC_WS_URL=wss://your-websocket-domain.com
```

## Installation & Setup

### 1. Install Dependencies

```bash
cd frontend
npm install
# or
pnpm install
```

### 2. Configure Backend Endpoints

Edit `.env.local` to match your backend configuration:

```env
NEXT_PUBLIC_API_URL=http://your-backend-url:8080
NEXT_PUBLIC_WS_URL=wss://your-websocket-url
```

### 3. Start Development Server

```bash
npm run dev
# or
pnpm dev
```

The frontend will be available at `http://localhost:3000`

## API Integration Points

### Authentication Flow

1. User enters credentials on login page
2. Frontend sends POST request to `POST /auth/login`
3. Backend returns JWT token
4. Token is stored in `localStorage` as `authToken`
5. All subsequent API requests include `Authorization: Bearer {token}` header

### Data Loading Flow

On application start:
1. Check if user has valid auth token
2. If not, redirect to `/login`
3. If yes, fetch account info from `GET /api/binance/accountInfo`
4. Establish WebSocket connection for real-time signals
5. Load positions and trade history

### Real-time Updates

WebSocket connection handles:
- **Signal notifications** - New trading signals from Telegram
- **Position updates** - Real-time P&L, price changes
- **Order execution** - Trade execution confirmations

Expected WebSocket message format:

```json
{
  "type": "signal",
  "id": "1",
  "pair": "BTC/USDT",
  "action": "BUY",
  "entry": 45000,
  "stopLoss": 43500,
  "takeProfit": [47000, 49000, 51000],
  "source": "Premium Signals",
  "timestamp": "2025-01-04T10:30:00Z"
}
```

## File Structure

```
frontend/
├── app/
│   ├── layout.tsx          # Root layout
│   ├── page.tsx            # Main dashboard
│   ├── login/
│   │   └── page.tsx        # Login page
│   └── globals.css         # Global styles
├── components/
│   ├── ui/                 # UI components (buttons, inputs, etc.)
│   ├── sidebar.tsx         # Navigation sidebar
│   ├── telegram-signals.tsx # Signals display
│   ├── position-management.tsx # Position management
│   ├── manual-trading.tsx   # Manual trading form
│   ├── trade-history.tsx    # Trade history table
│   ├── account-summary.tsx  # Account summary
│   └── theme-provider.tsx   # Theme configuration
├── lib/
│   ├── api.ts              # API client and backend communication
│   └── utils.ts            # Utility functions
├── types/
│   └── trading.ts          # TypeScript interfaces
├── hooks/
│   ├── use-toast.ts        # Toast notifications
│   └── use-mobile.ts       # Mobile detection
├── .env.local              # Environment variables
└── package.json            # Dependencies
```

## Key Files for Backend Integration

### `/lib/api.ts`
Main API client file containing:
- `authAPI.login()` - User authentication
- `tradingAPI.*()` - All trading operations
- `createReconnectingWebSocket()` - WebSocket with auto-reconnect
- Token management helpers

### `/app/page.tsx`
Main dashboard with:
- Authentication check
- Data loading from backend
- WebSocket connection setup
- Event handlers for trades and positions

### `/app/login/page.tsx`
Login page with:
- Form validation
- JWT token storage
- Error handling
- Redirect to dashboard on success

## Backend Endpoint Implementation Notes

### Custom Endpoints Required

The following endpoints need to be implemented in your backend if not already present:

#### POST /api/binance/executeTrade
Executes a manual trade.

**Request:**
```json
{
  "pair": "BTC/USDT",
  "side": "LONG",
  "price": 45000,
  "quantity": 0.5,
  "leverage": 10,
  "stopLoss": 43500,
  "takeProfit": 47000
}
```

**Response:**
```json
{
  "id": "position-123",
  "status": "executed",
  "pair": "BTC/USDT",
  "entryPrice": 45000
}
```

#### POST /api/binance/closePosition/{id}
Closes an open position.

**Response:**
```json
{
  "id": "position-123",
  "status": "closed",
  "exitPrice": 46000,
  "pnl": 500
}
```

## Testing the Integration

### 1. Test Authentication
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'
```

### 2. Test Health Check
```bash
curl http://localhost:8080/auth/health
```

### 3. Test Trading API
```bash
curl http://localhost:8080/api/binance/accountInfo \
  -H "Authorization: Bearer {your-jwt-token}"
```

## Troubleshooting

### WebSocket Connection Issues
- Check that `NEXT_PUBLIC_WS_URL` is correct
- Verify firewall/network allows WebSocket connections
- Check browser console for connection errors

### API 401 Unauthorized
- Token may be expired or invalid
- Clear localStorage and login again
- Check that token is being sent in Authorization header

### CORS Issues
- Ensure backend has CORS enabled
- Check CORS configuration in Spring Boot `WebConfig.java`

### Database Connection Issues
- Verify PostgreSQL connection string in backend
- Check database credentials
- Ensure database migrations have run

## Development Tips

1. **Mock Data**: Remove `.env.local` to use mock data for development
2. **Debug Requests**: Check browser DevTools Network tab for API calls
3. **Console Logs**: Frontend includes console.log statements for debugging
4. **Hot Reload**: Changes automatically reload during development

## Deployment

### Build Production Bundle
```bash
npm run build
```

### Start Production Server
```bash
npm start
```

### Environment Variables (Production)
Set these in your deployment environment:
- `NEXT_PUBLIC_API_URL` - Your backend API domain
- `NEXT_PUBLIC_WS_URL` - Your WebSocket server domain

## Support

For integration issues:
1. Check the browser console for error messages
2. Review API response in DevTools Network tab
3. Verify backend endpoints are running and accessible
4. Check environment variables are set correctly

---

**Last Updated**: 2025-10-24
**Frontend Version**: 15.2.4 (Next.js)
**Backend Required Version**: Spring Boot 3.x with PostgreSQL
