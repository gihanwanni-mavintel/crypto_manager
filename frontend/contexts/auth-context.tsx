"use client"

import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { useRouter } from 'next/navigation'
import { authAPI, getAuthToken, removeAuthToken } from '@/lib/api'

interface User {
  username: string
  token: string
}

interface AuthContextType {
  user: User | null
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
  isLoading: boolean
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const router = useRouter()

  useEffect(() => {
    // Check for existing token on mount
    const token = getAuthToken()
    if (token) {
      // Decode username from JWT token (simple base64 decode)
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        setUser({ username: payload.sub, token })
      } catch (error) {
        console.error('Failed to decode token:', error)
        removeAuthToken()
      }
    }
    setIsLoading(false)
  }, [])

  const login = async (username: string, password: string) => {
    try {
      const response = await authAPI.login(username, password)
      const payload = JSON.parse(atob(response.token.split('.')[1]))
      setUser({ username: payload.sub, token: response.token })
      router.push('/')
    } catch (error) {
      throw error
    }
  }

  const register = async (username: string, password: string) => {
    try {
      const response = await authAPI.register(username, password)
      const payload = JSON.parse(atob(response.token.split('.')[1]))
      setUser({ username: payload.sub, token: response.token })
      router.push('/')
    } catch (error) {
      throw error
    }
  }

  const logout = () => {
    authAPI.logout()
    setUser(null)
    router.push('/auth/login')
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        login,
        register,
        logout,
        isLoading,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
