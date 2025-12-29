"use client"

import { useState, useEffect } from "react"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Settings2 } from "lucide-react"

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081'

interface TradeConfig {
  maxPositionSize: number
  maxLeverage: number
  marginMode: "ISOLATED" | "CROSS"
}

export function TradeManagement() {
  const [config, setConfig] = useState<TradeConfig>({
    maxPositionSize: 1000,
    maxLeverage: 20,
    marginMode: "CROSS",
  })
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [saveStatus, setSaveStatus] = useState<"idle" | "success" | "error">("idle")

  // Load config from backend
  useEffect(() => {
    const loadConfig = async () => {
      setIsLoading(true)
      try {
        const response = await fetch(`${API_BASE_URL}/api/trade-management-config`)
        if (response.ok) {
          const data = await response.json()
          // Convert percent to USD (assuming 10% = $1000 for now)
          const maxPositionSize = (data.maxPositionSizePercent / 10) * 1000
          setConfig({
            maxPositionSize: maxPositionSize || 1000,
            maxLeverage: Number(data.maxLeverage) || 20,
            marginMode: data.marginMode || "ISOLATED",
          })
        }
      } catch (err) {
        console.error("Failed to load config:", err)
      } finally {
        setIsLoading(false)
      }
    }

    loadConfig()
  }, [])

  const handleSave = async () => {
    setIsSaving(true)
    setSaveStatus("idle")

    try {
      // Convert USD to percent (assuming $1000 = 10% for now)
      const maxPositionSizePercent = (config.maxPositionSize / 1000) * 10

      const response = await fetch(`${API_BASE_URL}/api/trade-management-config`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          userId: 1,
          maxLeverage: config.maxLeverage,
          maxPositionSizePercent: maxPositionSizePercent,
          marginMode: config.marginMode,
        }),
      })

      if (response.ok) {
        setSaveStatus("success")
        setTimeout(() => setSaveStatus("idle"), 3000)
      } else {
        setSaveStatus("error")
      }
    } catch (err) {
      console.error("Failed to save config:", err)
      setSaveStatus("error")
    } finally {
      setIsSaving(false)
    }
  }

  const handleReset = () => {
    setConfig({
      maxPositionSize: 1000,
      maxLeverage: 20,
      marginMode: "CROSS",
    })
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Settings2 className="h-8 w-8 text-primary" />
        <div>
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Trade Management</h2>
          <p className="text-sm text-muted-foreground mt-1">
            Configure your trading parameters and risk management
          </p>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        {/* Settings Card */}
        <Card className="md:col-span-2 p-6">
          <div className="space-y-6">
            <div>
              <h3 className="text-lg font-semibold mb-1">Trade Management Settings</h3>
              <p className="text-sm text-muted-foreground">
                Set your maximum position size, leverage, and take profit exit percentages
              </p>
            </div>

            <div className="grid gap-6 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="maxPositionSize">Maximum Position Size ($)</Label>
                <Input
                  id="maxPositionSize"
                  type="number"
                  value={config.maxPositionSize}
                  onChange={(e) =>
                    setConfig({ ...config, maxPositionSize: Number(e.target.value) })
                  }
                  placeholder="1000"
                />
                <p className="text-xs text-muted-foreground">Maximum dollar amount per position</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="maxLeverage">Maximum Leverage (x)</Label>
                <Input
                  id="maxLeverage"
                  type="number"
                  value={config.maxLeverage}
                  onChange={(e) => setConfig({ ...config, maxLeverage: Number(e.target.value) })}
                  placeholder="10"
                  min="1"
                  max="125"
                />
                <p className="text-xs text-muted-foreground">Maximum leverage multiplier</p>
              </div>
            </div>

            <div className="flex items-center justify-between p-4 border rounded-lg">
              <div className="space-y-0.5">
                <Label htmlFor="marginMode" className="text-base">
                  Margin Mode
                </Label>
                <p className="text-sm text-muted-foreground">
                  {config.marginMode === "ISOLATED"
                    ? "Isolated margin limits risk to position size"
                    : "Cross margin uses entire account balance"}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-muted-foreground">Isolated</span>
                <Switch
                  id="marginMode"
                  checked={config.marginMode === "CROSS"}
                  onCheckedChange={(checked) =>
                    setConfig({ ...config, marginMode: checked ? "CROSS" : "ISOLATED" })
                  }
                />
                <span className="text-sm font-medium">Cross</span>
              </div>
            </div>

            <div className="flex gap-3 pt-4">
              <Button onClick={handleSave} disabled={isSaving} className="flex-1">
                {isSaving ? "Saving..." : "Save Configuration"}
              </Button>
              <Button onClick={handleReset} variant="outline">
                Reset to Defaults
              </Button>
            </div>

            {saveStatus === "success" && (
              <div className="p-3 bg-green-500/10 border border-green-500/20 rounded-lg">
                <p className="text-sm text-green-600 dark:text-green-400">
                  ✓ Configuration saved successfully
                </p>
              </div>
            )}

            {saveStatus === "error" && (
              <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
                <p className="text-sm text-red-600 dark:text-red-400">
                  ✗ Failed to save configuration. Please try again.
                </p>
              </div>
            )}
          </div>
        </Card>

        {/* Current Configuration Card */}
        <Card className="p-6">
          <div className="space-y-4">
            <h3 className="text-lg font-semibold">Current Configuration</h3>
            <p className="text-sm text-muted-foreground">Active trading parameters</p>

            <div className="space-y-4 pt-2">
              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">Max Position</p>
                <p className="text-2xl font-bold">${config.maxPositionSize}</p>
              </div>

              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">Max Leverage</p>
                <p className="text-2xl font-bold">{config.maxLeverage}x</p>
              </div>

              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">Margin Mode</p>
                <div className="flex items-center gap-2">
                  <div
                    className={`h-2 w-2 rounded-full ${config.marginMode === "ISOLATED" ? "bg-blue-500" : "bg-purple-500"}`}
                  />
                  <p className="text-lg font-semibold">{config.marginMode}</p>
                </div>
              </div>

              <div className="pt-2">
                <p className="text-sm text-muted-foreground">Status</p>
                <div className="flex items-center gap-2 mt-1">
                  <div className="h-2 w-2 rounded-full bg-green-500" />
                  <p className="text-sm font-medium text-green-600 dark:text-green-400">Valid</p>
                </div>
              </div>
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}
