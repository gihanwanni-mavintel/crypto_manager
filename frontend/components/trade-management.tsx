'use client'

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { cn } from "@/lib/utils"
import { useToast } from "@/hooks/use-toast"

interface TradeManagementConfig {
  maxPositionSize: string
  maxLeverage: string
  tp1: string
  tp2: string
  tp3: string
  tp4: string
}

interface TradeManagementProps {
  onConfigSave?: (config: TradeManagementConfig) => Promise<void>
}

export function TradeManagement({ onConfigSave }: TradeManagementProps) {
  const { toast } = useToast()
  const [isLoading, setIsLoading] = useState(false)
  const [config, setConfig] = useState<TradeManagementConfig>({
    maxPositionSize: "1000",
    maxLeverage: "10",
    tp1: "25",
    tp2: "25",
    tp3: "25",
    tp4: "25",
  })

  const [validationError, setValidationError] = useState<string>("")

  // Calculate TP4 automatically
  const calculateTP4 = (tp1: string, tp2: string, tp3: string): string => {
    const val1 = Number.parseFloat(tp1) || 0
    const val2 = Number.parseFloat(tp2) || 0
    const val3 = Number.parseFloat(tp3) || 0
    const tp4Value = 100 - (val1 + val2 + val3)
    return tp4Value.toFixed(2)
  }

  const handleTPChange = (field: "tp1" | "tp2" | "tp3", value: string) => {
    // Allow empty string or valid numbers
    if (value !== "" && (isNaN(Number.parseFloat(value)) || Number.parseFloat(value) < 0)) {
      return
    }

    const updatedConfig = { ...config, [field]: value }
    const calculatedTP4 = calculateTP4(updatedConfig.tp1, updatedConfig.tp2, updatedConfig.tp3)
    updatedConfig.tp4 = calculatedTP4

    setConfig(updatedConfig)

    // Validate
    const sum =
      Number.parseFloat(updatedConfig.tp1 || "0") +
      Number.parseFloat(updatedConfig.tp2 || "0") +
      Number.parseFloat(updatedConfig.tp3 || "0") +
      Number.parseFloat(calculatedTP4)

    if (sum > 100) {
      setValidationError("Total exit percentage cannot exceed 100%")
    } else if (Number.parseFloat(calculatedTP4) < 0) {
      setValidationError("TP4 cannot be negative. Reduce TP1, TP2, or TP3.")
    } else {
      setValidationError("")
    }
  }

  const handleConfigChange = (field: keyof TradeManagementConfig, value: string) => {
    if (field === "tp1" || field === "tp2" || field === "tp3") {
      handleTPChange(field, value)
    } else {
      setConfig({ ...config, [field]: value })
      setValidationError("")
    }
  }

  const handleSave = async () => {
    // Validate totals
    const totalTP =
      Number.parseFloat(config.tp1 || "0") +
      Number.parseFloat(config.tp2 || "0") +
      Number.parseFloat(config.tp3 || "0") +
      Number.parseFloat(config.tp4 || "0")

    if (totalTP !== 100) {
      setValidationError("Total TP percentages must equal 100%")
      return
    }

    setIsLoading(true)
    try {
      if (onConfigSave) {
        await onConfigSave(config)
      }
      toast({
        title: "Success",
        description: "Trade management configuration saved successfully",
      })
    } catch (error) {
      toast({
        title: "Error",
        description: error instanceof Error ? error.message : "Failed to save configuration",
        variant: "destructive",
      })
    } finally {
      setIsLoading(false)
    }
  }

  const handleReset = () => {
    setConfig({
      maxPositionSize: "1000",
      maxLeverage: "10",
      tp1: "25",
      tp2: "25",
      tp3: "25",
      tp4: "25",
    })
    setValidationError("")
    toast({
      title: "Reset",
      description: "Configuration reset to defaults",
    })
  }

  const totalTP =
    Number.parseFloat(config.tp1 || "0") +
    Number.parseFloat(config.tp2 || "0") +
    Number.parseFloat(config.tp3 || "0") +
    Number.parseFloat(config.tp4 || "0")

  const isValid = !validationError && totalTP === 100

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      {/* Configuration Form - Left Side (2/3) */}
      <div className="lg:col-span-2">
        <Card className="shadow-sm">
          <CardHeader>
            <CardTitle>Trade Management Settings</CardTitle>
            <CardDescription>
              Set your maximum position size, leverage, and take profit exit percentages
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Position and Leverage Settings */}
            <div className="grid gap-6 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="maxPositionSize">Maximum Position Size ($)</Label>
                <Input
                  id="maxPositionSize"
                  type="number"
                  placeholder="e.g., 1000"
                  value={config.maxPositionSize}
                  onChange={(e) => handleConfigChange("maxPositionSize", e.target.value)}
                  min="0"
                  step="100"
                />
                <p className="text-xs text-muted-foreground">Maximum dollar amount per position</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="maxLeverage">Maximum Leverage (x)</Label>
                <Input
                  id="maxLeverage"
                  type="number"
                  placeholder="e.g., 10"
                  value={config.maxLeverage}
                  onChange={(e) => handleConfigChange("maxLeverage", e.target.value)}
                  min="1"
                  max="125"
                  step="1"
                />
                <p className="text-xs text-muted-foreground">Maximum leverage multiplier</p>
              </div>
            </div>

            <Separator />

            {/* Take Profit Settings */}
            <div className="space-y-4">
              <div>
                <h3 className="text-sm font-semibold text-foreground">Take Profit Exit Percentages</h3>
                <p className="text-xs text-muted-foreground">
                  Define what percentage of your position to exit at each TP level. Must total 100%.
                </p>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="tp1">TP1 Exit Percentage (%)</Label>
                  <Input
                    id="tp1"
                    type="number"
                    placeholder="e.g., 25"
                    value={config.tp1}
                    onChange={(e) => handleTPChange("tp1", e.target.value)}
                    min="0"
                    max="100"
                    step="0.01"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tp2">TP2 Exit Percentage (%)</Label>
                  <Input
                    id="tp2"
                    type="number"
                    placeholder="e.g., 25"
                    value={config.tp2}
                    onChange={(e) => handleTPChange("tp2", e.target.value)}
                    min="0"
                    max="100"
                    step="0.01"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tp3">TP3 Exit Percentage (%)</Label>
                  <Input
                    id="tp3"
                    type="number"
                    placeholder="e.g., 25"
                    value={config.tp3}
                    onChange={(e) => handleTPChange("tp3", e.target.value)}
                    min="0"
                    max="100"
                    step="0.01"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tp4">TP4 Exit Percentage (%)</Label>
                  <Input
                    id="tp4"
                    type="number"
                    placeholder="Auto-calculated"
                    value={config.tp4}
                    disabled
                    className="bg-muted text-muted-foreground"
                  />
                  <p className="text-xs text-muted-foreground">Auto-calculated based on TP1-3</p>
                </div>
              </div>

              {validationError && (
                <div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{validationError}</div>
              )}

              <div className="flex items-center justify-between rounded-lg bg-muted p-3">
                <span className="text-sm font-medium text-foreground">Total:</span>
                <span
                  className={cn(
                    "text-lg font-bold",
                    totalTP === 100 ? "text-success" : "text-destructive",
                  )}
                >
                  {totalTP.toFixed(2)}%
                </span>
              </div>
            </div>

            <div className="flex justify-end gap-3">
              <Button variant="outline" onClick={handleReset} disabled={isLoading}>
                Reset to Defaults
              </Button>
              <Button onClick={handleSave} disabled={!isValid || isLoading}>
                {isLoading ? "Saving..." : "Save Configuration"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Current Configuration Display - Right Side (1/3) */}
      <div className="lg:col-span-1">
        <Card className="shadow-sm">
          <CardHeader>
            <CardTitle>Current Configuration</CardTitle>
            <CardDescription>Active trading parameters</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Position Settings */}
            <div>
              <h4 className="mb-3 text-sm font-semibold text-foreground">Position Limits</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between rounded-lg bg-muted/50 p-3">
                  <span className="text-sm text-muted-foreground">Max Position</span>
                  <span className="font-semibold text-foreground">${config.maxPositionSize || "0"}</span>
                </div>
                <div className="flex items-center justify-between rounded-lg bg-muted/50 p-3">
                  <span className="text-sm text-muted-foreground">Max Leverage</span>
                  <span className="font-semibold text-foreground">{config.maxLeverage || "0"}x</span>
                </div>
              </div>
            </div>

            <Separator />

            {/* TP Configuration */}
            <div>
              <h4 className="mb-3 text-sm font-semibold text-foreground">Take Profit Strategy</h4>
              <div className="space-y-2">
                {[
                  { label: "TP1", value: config.tp1 },
                  { label: "TP2", value: config.tp2 },
                  { label: "TP3", value: config.tp3 },
                  { label: "TP4", value: config.tp4 },
                ].map((tp) => (
                  <div key={tp.label} className="flex items-center justify-between rounded-lg bg-muted/50 p-3">
                    <span className="text-sm text-muted-foreground">{tp.label} Exit</span>
                    <div className="flex items-center gap-2">
                      <div className="h-2 w-16 overflow-hidden rounded-full bg-secondary">
                        <div
                          className="h-full bg-primary transition-all"
                          style={{ width: `${Number.parseFloat(tp.value || "0")}%` }}
                        />
                      </div>
                      <span className="min-w-[3.5rem] text-right font-semibold text-foreground">
                        {Number.parseFloat(tp.value || "0").toFixed(1)}%
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <Separator />

            {/* Validation Status */}
            <div className="rounded-lg bg-muted/50 p-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Status</span>
                <span className={cn("text-sm font-semibold", isValid ? "text-success" : "text-destructive")}>
                  {isValid ? "Valid" : "Invalid"}
                </span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
