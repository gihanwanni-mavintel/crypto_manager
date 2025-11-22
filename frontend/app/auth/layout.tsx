import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "Login | Crypto Manager",
  description: "Login or register to access your crypto trading dashboard",
}

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return children
}
