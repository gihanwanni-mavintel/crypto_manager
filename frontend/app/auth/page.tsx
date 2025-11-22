"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { authAPI } from "@/lib/api"
import { LogIn, UserPlus } from "lucide-react"

export default function AuthPage() {
  const router = useRouter()
  const [isLogin, setIsLogin] = useState(true)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      if (!username.trim() || !password.trim()) {
        setError("Username and password are required")
        setIsLoading(false)
        return
      }

      if (isLogin) {
        const response = await authAPI.login(username, password)
        console.log('✅ [Auth] Login successful, token saved:', response.token ? '✅ Yes' : '❌ No')
      } else {
        // Password validation for registration
        if (password.length < 6) {
          setError("Password must be at least 6 characters")
          setIsLoading(false)
          return
        }
        const response = await authAPI.register(username, password)
        console.log('✅ [Auth] Registration successful, token saved:', response.token ? '✅ Yes' : '❌ No')
      }

      // Verify token was saved
      const savedToken = typeof window !== 'undefined' ? localStorage.getItem('authToken') : null
      console.log('🔍 [Auth] Checking localStorage after login/register:', savedToken ? '✅ Token found' : '❌ Token NOT found')

      // Redirect to dashboard on success
      router.push("/")
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "An error occurred"
      setError(errorMessage)
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center h-12 w-12 rounded-lg bg-primary mb-4">
            <LogIn className="h-6 w-6 text-primary-foreground" />
          </div>
          <h1 className="text-3xl font-bold text-white mb-2">Crypto Manager</h1>
          <p className="text-slate-400">Manage your cryptocurrency trades with confidence</p>
        </div>

        {/* Card */}
        <div className="bg-card border border-border rounded-lg shadow-lg p-8">
          {/* Tabs */}
          <div className="flex gap-2 mb-6">
            <button
              onClick={() => {
                setIsLogin(true)
                setError(null)
                setUsername("")
                setPassword("")
              }}
              className={`flex-1 py-2 px-4 rounded-lg font-medium transition-colors ${
                isLogin
                  ? "bg-primary text-primary-foreground"
                  : "bg-accent text-accent-foreground hover:bg-accent/80"
              }`}
            >
              <span className="flex items-center justify-center gap-2">
                <LogIn className="h-4 w-4" />
                Login
              </span>
            </button>
            <button
              onClick={() => {
                setIsLogin(false)
                setError(null)
                setUsername("")
                setPassword("")
              }}
              className={`flex-1 py-2 px-4 rounded-lg font-medium transition-colors ${
                !isLogin
                  ? "bg-primary text-primary-foreground"
                  : "bg-accent text-accent-foreground hover:bg-accent/80"
              }`}
            >
              <span className="flex items-center justify-center gap-2">
                <UserPlus className="h-4 w-4" />
                Register
              </span>
            </button>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Username */}
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-foreground mb-2">
                Username
              </label>
              <Input
                id="username"
                type="text"
                placeholder="Enter your username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={isLoading}
                className="w-full"
              />
            </div>

            {/* Password */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-foreground mb-2">
                Password
              </label>
              <Input
                id="password"
                type="password"
                placeholder={isLogin ? "Enter your password" : "Create a password (min. 6 characters)"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
                className="w-full"
              />
            </div>

            {/* Error Message */}
            {error && (
              <div className="bg-destructive/10 border border-destructive/50 rounded-lg p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            {/* Success Message */}
            {!error && username && password && !isLoading && (
              <div className="bg-green-500/10 border border-green-500/50 rounded-lg p-3 text-sm text-green-600 dark:text-green-400">
                {isLogin ? "Ready to login" : "Ready to create account"}
              </div>
            )}

            {/* Submit Button */}
            <Button
              type="submit"
              disabled={isLoading || !username || !password}
              className="w-full"
            >
              {isLoading ? (
                <>
                  <span className="inline-block animate-spin mr-2">⏳</span>
                  {isLogin ? "Logging in..." : "Creating account..."}
                </>
              ) : (
                <>
                  {isLogin ? (
                    <>
                      <LogIn className="mr-2 h-4 w-4" />
                      Login
                    </>
                  ) : (
                    <>
                      <UserPlus className="mr-2 h-4 w-4" />
                      Register
                    </>
                  )}
                </>
              )}
            </Button>
          </form>

          {/* Footer Info */}
          <div className="mt-6 pt-6 border-t border-border">
            <p className="text-center text-sm text-slate-400">
              {isLogin ? (
                <>
                  Don't have an account?{" "}
                  <button
                    type="button"
                    onClick={() => {
                      setIsLogin(false)
                      setError(null)
                      setUsername("")
                      setPassword("")
                    }}
                    className="text-primary hover:text-primary/80 font-medium transition-colors"
                  >
                    Create one
                  </button>
                </>
              ) : (
                <>
                  Already have an account?{" "}
                  <button
                    type="button"
                    onClick={() => {
                      setIsLogin(true)
                      setError(null)
                      setUsername("")
                      setPassword("")
                    }}
                    className="text-primary hover:text-primary/80 font-medium transition-colors"
                  >
                    Login here
                  </button>
                </>
              )}
            </p>
          </div>
        </div>

        {/* Demo Info */}
        <div className="mt-6 p-4 bg-slate-800/50 border border-slate-700 rounded-lg">
          <p className="text-xs text-slate-400">
            <span className="font-semibold text-slate-300">Demo Info:</span> Create a new account or login with existing credentials
          </p>
        </div>
      </div>
    </div>
  )
}
