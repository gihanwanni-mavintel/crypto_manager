import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import type { Signal } from "@/types/trading"
import { TrendingUp, TrendingDown, Clock } from "lucide-react"

interface TelegramSignalsProps {
  signals: Signal[]
}

export function TelegramSignals({ signals }: TelegramSignalsProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground">Telegram Signals</h2>
          <p className="text-sm text-muted-foreground">Latest trading signals from your channels</p>
        </div>
        <Badge variant="outline" className="text-xs">
          {signals.filter((s) => s.status === "active").length} Active
        </Badge>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {signals.map((signal) => (
          <Card key={signal.id} className="relative overflow-hidden">
            <div
              className={`absolute top-0 left-0 w-1 h-full ${
                signal.action === "BUY" ? "bg-success" : "bg-destructive"
              }`}
            />

            <CardHeader className="pb-3">
              <div className="flex items-start justify-between">
                <div>
                  <CardTitle className="text-lg font-bold">{signal.pair}</CardTitle>
                  <CardDescription className="text-xs">{signal.source}</CardDescription>
                </div>
                <Badge
                  variant={signal.action === "BUY" ? "default" : "destructive"}
                  className="flex items-center gap-1"
                >
                  {signal.action === "BUY" ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                  {signal.action}
                </Badge>
              </div>
            </CardHeader>

            <CardContent className="space-y-3">
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div>
                  <p className="text-muted-foreground text-xs">Entry</p>
                  <p className="font-mono font-semibold">${signal.entry.toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-muted-foreground text-xs">Stop Loss</p>
                  <p className="font-mono font-semibold text-destructive">${signal.stopLoss.toLocaleString()}</p>
                </div>
              </div>

              <div>
                <p className="text-muted-foreground text-xs mb-1">Take Profit Targets</p>
                <div className="flex gap-1">
                  {signal.takeProfit.map((tp, idx) => (
                    <Badge key={idx} variant="outline" className="font-mono text-xs">
                      ${tp.toLocaleString()}
                    </Badge>
                  ))}
                </div>
              </div>

              <div className="flex items-center gap-1 text-xs text-muted-foreground pt-2 border-t border-border">
                <Clock className="h-3 w-3" />
                {signal.timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
