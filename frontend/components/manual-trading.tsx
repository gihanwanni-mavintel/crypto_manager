"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Slider } from "@/components/ui/slider"
import { Checkbox } from "@/components/ui/checkbox"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import type { Trade, TakeProfitLevel } from "@/types/trading"
import { TrendingUp, TrendingDown, Check, ChevronsUpDown } from "lucide-react"
import { cn } from "@/lib/utils"

// API request helper with auth
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081'

const apiRequest = async (
  endpoint: string,
  options: RequestInit = {}
): Promise<Response> => {
  const headers: Record<string, string> = {}

  // Only add Content-Type for requests with a body (POST, PUT, PATCH)
  if (options.method && ['POST', 'PUT', 'PATCH'].includes(options.method.toUpperCase())) {
    headers['Content-Type'] = 'application/json'
  }

  if (options.headers && typeof options.headers === 'object' && !Array.isArray(options.headers)) {
    Object.assign(headers, options.headers as Record<string, string>)
  }

  const token = typeof window !== 'undefined' ? localStorage.getItem('authToken') : null
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const url = `${API_BASE_URL}${endpoint}`

  return fetch(url, {
    ...options,
    headers,
  })
}

interface ManualTradingProps {
  onExecuteTrade: (trade: Trade) => void
}

const TRADING_PAIRS = [
  "BTC/USDT",
  "ETH/USDT",
  "BNB/USDT",
  "SOL/USDT",
  "XRP/USDT",
  "ADA/USDT",
  "DOGE/USDT",
  "AVAX/USDT",
  "DOT/USDT",
  "MATIC/USDT",
  "LINK/USDT",
  "UNI/USDT",
  "ATOM/USDT",
  "LTC/USDT",
  "ETC/USDT",
]

interface TradeConfig {
  maxPositionSize: number
  maxLeverage: number
}

export function ManualTrading({ onExecuteTrade }: ManualTradingProps) {
  const [open, setOpen] = useState(false)
  const [pair, setPair] = useState("BTC/USDT")
  const [price, setPrice] = useState("")
  const [usdt, setUsdt] = useState("")
  const [leverage, setLeverage] = useState([10])

  const [enableStopLoss, setEnableStopLoss] = useState(false)
  const [enableTakeProfit, setEnableTakeProfit] = useState(false)

  const [stopLoss, setStopLoss] = useState("")

  const [tp1Price, setTp1Price] = useState("")
  const [tp1Percentage, setTp1Percentage] = useState("")
  const [tp2Price, setTp2Price] = useState("")
  const [tp2Percentage, setTp2Percentage] = useState("")
  const [tp3Price, setTp3Price] = useState("")
  const [tp3Percentage, setTp3Percentage] = useState("")
  const [tp4Price, setTp4Price] = useState("")
  const [tp4Percentage, setTp4Percentage] = useState("")

  const [selectedSide, setSelectedSide] = useState<"LONG" | "SHORT" | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Load trade config from API
  const [tradeConfig, setTradeConfig] = useState<TradeConfig>({
    maxPositionSize: 1000,
    maxLeverage: 10,
  })

  useEffect(() => {
    loadTradeConfig()
  }, [])

  const loadTradeConfig = async () => {
    try {
      const response = await apiRequest('/api/config/trade-settings')
      if (response.ok) {
        const data = await response.json()
        setTradeConfig({
          maxPositionSize: parseFloat(data.maxPositionSize) || 1000,
          maxLeverage: parseFloat(data.maxLeverage) || 10,
        })
      }
    } catch (err) {
      console.log("Could not load trade config from API")
    }
  }

  const handleExecute = (side: "LONG" | "SHORT") => {
    setError(null)

    if (!price || !usdt) {
      setError("Please fill in Price and USDT amount")
      return
    }

    const priceNum = Number.parseFloat(price)
    const usdtNum = Number.parseFloat(usdt)

    if (priceNum <= 0 || usdtNum <= 0) {
      setError("Price and USDT must be greater than 0")
      return
    }

    // ✅ VALIDATE AGAINST TRADE MANAGEMENT CONFIG
    if (usdtNum > tradeConfig.maxPositionSize) {
      setError(`Position size $${usdtNum} exceeds your maximum of $${tradeConfig.maxPositionSize}`)
      return
    }

    if (leverage[0] > tradeConfig.maxLeverage) {
      setError(`Leverage ${leverage[0]}x exceeds your maximum of ${tradeConfig.maxLeverage}x`)
      return
    }

    setSelectedSide(side)
    const quantity = usdtNum / priceNum

    const takeProfitLevels: TakeProfitLevel[] = []
    if (enableTakeProfit) {
      if (tp1Price && tp1Percentage) {
        takeProfitLevels.push({ price: Number.parseFloat(tp1Price), percentage: Number.parseFloat(tp1Percentage) })
      }
      if (tp2Price && tp2Percentage) {
        takeProfitLevels.push({ price: Number.parseFloat(tp2Price), percentage: Number.parseFloat(tp2Percentage) })
      }
      if (tp3Price && tp3Percentage) {
        takeProfitLevels.push({ price: Number.parseFloat(tp3Price), percentage: Number.parseFloat(tp3Percentage) })
      }
      if (tp4Price && tp4Percentage) {
        takeProfitLevels.push({ price: Number.parseFloat(tp4Price), percentage: Number.parseFloat(tp4Percentage) })
      }
    }

    const trade: Trade = {
      pair,
      side,
      price: priceNum,
      quantity,
      leverage: leverage[0],
      stopLoss: enableStopLoss && stopLoss ? Number.parseFloat(stopLoss) : undefined,
      takeProfitLevels: takeProfitLevels.length > 0 ? takeProfitLevels : undefined,
    }

    onExecuteTrade(trade)

    // Reset form
    setPrice("")
    setUsdt("")
    setStopLoss("")
    setTp1Price("")
    setTp1Percentage("")
    setTp2Price("")
    setTp2Percentage("")
    setTp3Price("")
    setTp3Percentage("")
    setTp4Price("")
    setTp4Percentage("")
    setEnableStopLoss(false)
    setEnableTakeProfit(false)
    setSelectedSide(null)
  }

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg sm:text-2xl font-bold text-foreground">Manual Trading</h2>
        <p className="text-xs sm:text-sm text-muted-foreground">Execute trades manually with custom parameters</p>
      </div>

      <Card className="max-w-2xl w-full">
        <CardHeader className="pb-3">
          <CardTitle className="text-base sm:text-lg">Trade Setup</CardTitle>
          <CardDescription className="text-xs sm:text-sm">Configure your trade parameters and execute</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 sm:space-y-6">
          <div className="space-y-3 sm:space-y-4">
            <div className="space-y-2">
              <Label className="text-xs sm:text-sm">Trading Pair</Label>
              <Popover open={open} onOpenChange={setOpen}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    aria-expanded={open}
                    className="w-full justify-between font-mono bg-transparent"
                  >
                    {pair}
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-full p-0" align="start">
                  <Command>
                    <CommandInput placeholder="Search trading pair..." />
                    <CommandList>
                      <CommandEmpty>No trading pair found.</CommandEmpty>
                      <CommandGroup>
                        {TRADING_PAIRS.map((tradingPair) => (
                          <CommandItem
                            key={tradingPair}
                            value={tradingPair}
                            onSelect={(currentValue) => {
                              setPair(currentValue.toUpperCase())
                              setOpen(false)
                            }}
                          >
                            <Check className={cn("mr-2 h-4 w-4", pair === tradingPair ? "opacity-100" : "opacity-0")} />
                            {tradingPair}
                          </CommandItem>
                        ))}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                </PopoverContent>
              </Popover>
            </div>

            <div className="grid grid-cols-2 gap-2 sm:gap-4">
              <div className="space-y-2">
                <Label htmlFor="price" className="text-xs sm:text-sm">Price (USD)</Label>
                <Input
                  id="price"
                  type="number"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  placeholder="45000"
                  className="text-xs sm:text-sm"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="usdt" className="text-xs sm:text-sm">USDT Amount</Label>
                <Input
                  id="usdt"
                  type="number"
                  value={usdt}
                  onChange={(e) => setUsdt(e.target.value)}
                  placeholder="100"
                  className="text-xs sm:text-sm"
                />
              </div>
            </div>

            <div className="space-y-2 sm:space-y-3">
              <div className="flex items-center justify-between">
                <Label className="text-xs sm:text-sm">Leverage</Label>
                <span className="text-xs sm:text-sm font-mono font-semibold text-primary">{leverage[0]}x</span>
              </div>
              <Slider value={leverage} onValueChange={setLeverage} min={1} max={100} step={1} className="w-full" />
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>1x</span>
                <span>25x</span>
                <span>50x</span>
                <span>75x</span>
                <span>100x</span>
              </div>
            </div>

            <div className="space-y-3 pt-2">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="enableStopLoss"
                  checked={enableStopLoss}
                  onCheckedChange={(checked) => setEnableStopLoss(checked as boolean)}
                />
                <Label htmlFor="enableStopLoss" className="cursor-pointer font-medium">
                  Enable Stop Loss
                </Label>
              </div>

              {enableStopLoss && (
                <div className="space-y-2 pl-6">
                  <Label htmlFor="stopLoss">Stop Loss Price</Label>
                  <Input
                    id="stopLoss"
                    type="number"
                    value={stopLoss}
                    onChange={(e) => setStopLoss(e.target.value)}
                    placeholder="43500"
                  />
                </div>
              )}
            </div>

            <div className="space-y-3">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="enableTakeProfit"
                  checked={enableTakeProfit}
                  onCheckedChange={(checked) => setEnableTakeProfit(checked as boolean)}
                />
                <Label htmlFor="enableTakeProfit" className="cursor-pointer font-medium">
                  Enable Take Profit
                </Label>
              </div>

              {enableTakeProfit && (
                <div className="space-y-4 pl-6">
                  <div className="space-y-3">
                    <Label className="text-sm font-semibold">Take Profit Level 1</Label>
                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1.5">
                        <Label htmlFor="tp1Price" className="text-xs text-muted-foreground">
                          Price
                        </Label>
                        <Input
                          id="tp1Price"
                          type="number"
                          value={tp1Price}
                          onChange={(e) => setTp1Price(e.target.value)}
                          placeholder="46000"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="tp1Percentage" className="text-xs text-muted-foreground">
                          % of Position
                        </Label>
                        <Input
                          id="tp1Percentage"
                          type="number"
                          value={tp1Percentage}
                          onChange={(e) => setTp1Percentage(e.target.value)}
                          placeholder="25"
                        />
                      </div>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <Label className="text-sm font-semibold">Take Profit Level 2</Label>
                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1.5">
                        <Label htmlFor="tp2Price" className="text-xs text-muted-foreground">
                          Price
                        </Label>
                        <Input
                          id="tp2Price"
                          type="number"
                          value={tp2Price}
                          onChange={(e) => setTp2Price(e.target.value)}
                          placeholder="47000"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="tp2Percentage" className="text-xs text-muted-foreground">
                          % of Position
                        </Label>
                        <Input
                          id="tp2Percentage"
                          type="number"
                          value={tp2Percentage}
                          onChange={(e) => setTp2Percentage(e.target.value)}
                          placeholder="25"
                        />
                      </div>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <Label className="text-sm font-semibold">Take Profit Level 3</Label>
                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1.5">
                        <Label htmlFor="tp3Price" className="text-xs text-muted-foreground">
                          Price
                        </Label>
                        <Input
                          id="tp3Price"
                          type="number"
                          value={tp3Price}
                          onChange={(e) => setTp3Price(e.target.value)}
                          placeholder="48000"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="tp3Percentage" className="text-xs text-muted-foreground">
                          % of Position
                        </Label>
                        <Input
                          id="tp3Percentage"
                          type="number"
                          value={tp3Percentage}
                          onChange={(e) => setTp3Percentage(e.target.value)}
                          placeholder="25"
                        />
                      </div>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <Label className="text-sm font-semibold">Take Profit Level 4</Label>
                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1.5">
                        <Label htmlFor="tp4Price" className="text-xs text-muted-foreground">
                          Price
                        </Label>
                        <Input
                          id="tp4Price"
                          type="number"
                          value={tp4Price}
                          onChange={(e) => setTp4Price(e.target.value)}
                          placeholder="49000"
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor="tp4Percentage" className="text-xs text-muted-foreground">
                          % of Position
                        </Label>
                        <Input
                          id="tp4Percentage"
                          type="number"
                          value={tp4Percentage}
                          onChange={(e) => setTp4Percentage(e.target.value)}
                          placeholder="25"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {price && usdt && (
            <div className="p-2 sm:p-4 bg-muted rounded-lg space-y-2">
              <h4 className="font-semibold text-xs sm:text-sm">Order Summary</h4>
              <div className="space-y-1 text-xs sm:text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Pair:</span>
                  <span className="font-mono font-semibold truncate">{pair}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Price:</span>
                  <span className="font-mono truncate">${Number.parseFloat(price).toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">USDT Amount:</span>
                  <span className="font-mono truncate">${Number.parseFloat(usdt).toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Quantity:</span>
                  <span className="font-mono truncate">{(Number.parseFloat(usdt) / Number.parseFloat(price)).toFixed(8)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Leverage:</span>
                  <span className="font-mono">{leverage[0]}x</span>
                </div>
                <div className="flex justify-between font-semibold pt-2 border-t border-border">
                  <span className="truncate">Total Notional Value:</span>
                  <span className="font-mono truncate">
                    ${(Number.parseFloat(usdt) * leverage[0]).toLocaleString(undefined, { maximumFractionDigits: 2 })}
                  </span>
                </div>
              </div>
            </div>
          )}

          {error && (
            <div className="p-3 bg-destructive/10 border border-destructive text-destructive rounded-md text-xs sm:text-sm">
              {error}
            </div>
          )}

          <div className="space-y-2 sm:space-y-3 pt-2">
            <Button
              size="lg"
              variant="outline"
              className={cn(
                "w-full text-xs sm:text-sm border-2 font-semibold transition-all duration-200",
                selectedSide === "LONG"
                  ? "border-foreground bg-foreground text-background hover:bg-foreground/90"
                  : "border-foreground text-foreground hover:bg-foreground/10"
              )}
              onClick={() => handleExecute("LONG")}
            >
              <TrendingUp className="h-4 w-4 sm:h-5 sm:w-5 mr-2" />
              Open Long Position
            </Button>

            <Button
              size="lg"
              variant="outline"
              className={cn(
                "w-full text-xs sm:text-sm border-2 font-semibold transition-all duration-200",
                selectedSide === "SHORT"
                  ? "border-destructive bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  : "border-destructive text-destructive hover:bg-destructive/5"
              )}
              onClick={() => handleExecute("SHORT")}
            >
              <TrendingDown className="h-4 w-4 sm:h-5 sm:w-5 mr-2" />
              Open Short Position
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
