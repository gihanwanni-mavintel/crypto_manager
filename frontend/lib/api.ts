// API Configuration and Client Functions
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';
const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8081/ws';

// Token management
export const getAuthToken = (): string | null => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('authToken');
};

export const setAuthToken = (token: string): void => {
  if (typeof window === 'undefined') return;
  localStorage.setItem('authToken', token);
};

export const removeAuthToken = (): void => {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('authToken');
};

// API request helper with auth
const apiRequest = async (
  endpoint: string,
  options: RequestInit = {}
): Promise<Response> => {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  // Merge existing headers if provided
  if (options.headers) {
    if (typeof options.headers === 'object' && !Array.isArray(options.headers)) {
      Object.assign(headers, options.headers as Record<string, string>);
    }
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const url = `${API_BASE_URL}${endpoint}`;

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });
    return response;
  } catch (error) {
    console.error('[ERROR] Fetch error:', {
      url,
      error: error instanceof Error ? error.message : String(error),
      endpoint,
    });
    throw new Error(`Failed to connect to API at ${API_BASE_URL}. Make sure the backend is running.`);
  }
};

// Authentication APIs
export const authAPI = {
  register: async (username: string, password: string) => {
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Registration failed');
    }

    const data = await response.json() as { token: string };
    setAuthToken(data.token);
    return data;
  },

  login: async (username: string, password: string) => {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Login failed');
    }

    const data = await response.json() as { token: string };
    setAuthToken(data.token);
    return data;
  },

  logout: () => {
    removeAuthToken();
  },

  health: async () => {
    const response = await apiRequest('/auth/health');
    return response.text();
  },
};

// Binance/Trading APIs
export const tradingAPI = {
  // Get user trades
  getUserTrades: async (symbol: string) => {
    const response = await apiRequest(`/api/binance/userTrades?symbol=${symbol}`);
    if (!response.ok) throw new Error('Failed to fetch user trades');
    return response.json();
  },

  // Get position risk
  getPositionRisk: async (symbol: string) => {
    const response = await apiRequest(`/api/binance/positionRisk?symbol=${symbol}`);
    if (!response.ok) throw new Error('Failed to fetch position risk');
    return response.json();
  },

  // Get account balance
  getAccountBalance: async () => {
    const response = await apiRequest('/api/binance/account');
    if (!response.ok) throw new Error('Failed to fetch account balance');
    return response.json();
  },

  // Get open orders
  getOpenOrders: async (symbol: string) => {
    const response = await apiRequest(`/api/binance/executionInfo?symbol=${symbol}`);
    if (!response.ok) throw new Error('Failed to fetch open orders');
    return response.json();
  },

  // Get account info
  getAccountInfo: async () => {
    const response = await apiRequest('/api/binance/accountInfo');
    if (!response.ok) throw new Error('Failed to fetch account info');
    return response.json();
  },

  // Execute trade (new endpoint)
  executeTrade: async (tradeData: any) => {
    const response = await apiRequest('/api/trades/execute', {
      method: 'POST',
      body: JSON.stringify(tradeData),
    });
    if (!response.ok) throw new Error('Failed to execute trade');
    return response.json();
  },

  // Close position (new endpoint)
  closePosition: async (tradeId: string) => {
    const response = await apiRequest(`/api/trades/close/${tradeId}`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to close position');
    return response.json();
  },

  // Get all trades
  getAllTrades: async () => {
    const response = await apiRequest('/api/trades');
    if (!response.ok) throw new Error('Failed to fetch trades');
    return response.json();
  },

  // Get trade by ID
  getTrade: async (tradeId: string) => {
    const response = await apiRequest(`/api/trades/${tradeId}`);
    if (!response.ok) throw new Error('Failed to fetch trade');
    return response.json();
  },

  // Get open trades
  getOpenTrades: async () => {
    const response = await apiRequest('/api/trades/status/open');
    if (!response.ok) throw new Error('Failed to fetch open trades');
    return response.json();
  },

  // Get trades by pair
  getTradesByPair: async (pair: string) => {
    const response = await apiRequest(`/api/trades/pair/${pair}`);
    if (!response.ok) throw new Error('Failed to fetch trades by pair');
    return response.json();
  },
};

// Trading Signals API
export const signalsAPI = {
  // Get all signals
  getAllSignals: async () => {
    const response = await fetch(`${API_BASE_URL}/api/signals`);
    if (!response.ok) throw new Error('Failed to fetch signals');
    return response.json();
  },

  // Get signals by pair
  getSignalsByPair: async (pair: string) => {
    const response = await fetch(`${API_BASE_URL}/api/signals/pair/${pair}`);
    if (!response.ok) throw new Error('Failed to fetch signals by pair');
    return response.json();
  },

  // Get signals by setup type
  getSignalsBySetupType: async (setupType: string) => {
    const response = await fetch(`${API_BASE_URL}/api/signals/setup/${setupType}`);
    if (!response.ok) throw new Error('Failed to fetch signals by setup type');
    return response.json();
  },

  // Get signals by channel
  getSignalsByChannel: async (channel: string) => {
    const response = await fetch(`${API_BASE_URL}/api/signals/channel/${channel}`);
    if (!response.ok) throw new Error('Failed to fetch signals by channel');
    return response.json();
  },

  // Get signal by ID
  getSignalById: async (id: number) => {
    const response = await fetch(`${API_BASE_URL}/api/signals/${id}`);
    if (!response.ok) throw new Error('Failed to fetch signal');
    return response.json();
  },
};

// WebSocket connection for real-time signals
export const createWebSocketConnection = (
  onMessage: (data: any) => void,
  onError?: (error: Event) => void,
  onClose?: () => void
): WebSocket => {
  const ws = new WebSocket(WS_URL);

  ws.onopen = () => {
    console.log('[OK] WebSocket connected to', WS_URL);
    // Send heartbeat every 30 seconds
    const heartbeatInterval = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'ping' }));
      } else {
        clearInterval(heartbeatInterval);
      }
    }, 30000);
  };

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onMessage(data);
    } catch (error) {
      console.error('[WARN] WebSocket parse error:', error);
    }
  };

  ws.onerror = (error) => {
    const errorMessage = error instanceof Event ? `Code: ${(error as CloseEvent).code || 'unknown'}` : String(error);
    console.error('[ERROR] WebSocket error:', {
      url: WS_URL,
      message: errorMessage,
      readyState: ws.readyState,
    });
    onError?.(error);
  };

  ws.onclose = (event: CloseEvent) => {
    console.log('[ERROR] WebSocket disconnected', {
      code: event.code,
      reason: event.reason,
      wasClean: event.wasClean,
    });
    onClose?.();
  };

  return ws;
};

// Helper to reconnect WebSocket with exponential backoff
export const createReconnectingWebSocket = (
  onMessage: (data: any) => void,
  maxRetries = 5
): WebSocket | null => {
  let retries = 0;
  let ws: WebSocket | null = null;

  const connect = () => {
    try {
      ws = createWebSocketConnection(
        onMessage,
        () => {
          retries = 0; // Reset retries on successful connection
        },
        () => {
          if (retries < maxRetries) {
            retries++;
            const delay = Math.min(1000 * Math.pow(2, retries), 30000); // Max 30s
            console.log(`Reconnecting in ${delay}ms...`);
            setTimeout(connect, delay);
          } else {
            console.error('Max WebSocket retries reached');
          }
        }
      );
    } catch (error) {
      console.error('WebSocket connection failed:', error);
      if (retries < maxRetries) {
        retries++;
        setTimeout(connect, 1000 * Math.pow(2, retries));
      }
    }
  };

  connect();
  return ws;
};
