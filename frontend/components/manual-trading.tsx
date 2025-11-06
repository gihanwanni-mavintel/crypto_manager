"use client"

import { useState } from "react"
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

export function ManualTrading({ onExecuteTrade }: ManualTradingProps) {
  const [open, setOpen] = useState(false)
  const [pair, setPair] = useState("BTC/USDT")
  const [price, setPrice] = useState("")
  const [quantity, setQuantity] = useState("")
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

  const handleExecute = (side: "LONG" | "SHORT") => {
    if (!price || !quantity) {
      alert("Please fill in price and quantity")
      return
    }

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
      price: Number.parseFloat(price),
      quantity: Number.parseFloat(quantity),
      leverage: leverage[0],
      stopLoss: enableStopLoss && stopLoss ? Number.parseFloat(stopLoss) : undefined,
      takeProfitLevels: takeProfitLevels.length > 0 ? takeProfitLevels : undefined,
    }

    onExecuteTrade(trade)

    // Reset form
    setPrice("")
    setQuantity("")
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
  }

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-foreground">Manual Trading</h2>
        <p className="text-sm text-muted-foreground">Execute trades manually with custom parameters</p>
      </div>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>Trade Setup</CardTitle>
          <CardDescription>Configure your trade parameters and execute</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Trading Pair</Label>
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

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="price">Price</Label>
                <Input
                  id="price"
                  type="number"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  placeholder="45000"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="quantity">Quantity</Label>
                <Input
                  id="quantity"
                  type="number"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  placeholder="0.5"
                />
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>Leverage</Label>
                <span className="text-sm font-mono font-semibold text-primary">{leverage[0]}x</span>
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

          {price && quantity && (
            <div className="p-4 bg-muted rounded-lg space-y-2">
              <h4 className="font-semibold text-sm">Order Summary</h4>
              <div className="space-y-1 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Pair:</span>
                  <span className="font-mono font-semibold">{pair}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Price:</span>
                  <span className="font-mono">${Number.parseFloat(price).toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Quantity:</span>
                  <span className="font-mono">{quantity}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Leverage:</span>
                  <span className="font-mono">{leverage[0]}x</span>
                </div>
                <div className="flex justify-between font-semibold pt-2 border-t border-border">
                  <span>Total Value:</span>
                  <span className="font-mono">
                    ${(Number.parseFloat(price) * Number.parseFloat(quantity) * leverage[0]).toLocaleString()}
                  </span>
                </div>
              </div>
            </div>
          )}

          <div className="space-y-3 pt-2">
            <Button
              size="lg"
              className="w-full bg-success hover:bg-success/90 text-success-foreground"
              onClick={() => handleExecute("LONG")}
            >
              <TrendingUp className="h-5 w-5 mr-2" />
              Open Long Position
            </Button>

            <Button size="lg" variant="destructive" className="w-full" onClick={() => handleExecute("SHORT")}>
              <TrendingDown className="h-5 w-5 mr-2" />
              Open Short Position
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
