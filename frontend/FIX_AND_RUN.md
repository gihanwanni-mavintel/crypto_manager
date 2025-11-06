# Fix Corrupted Installation & Run Frontend

## Problem

The node_modules directory became corrupted during installation. This can happen on Windows with npm.

## Solution

### Step 1: Run the Clean Install Script

In PowerShell, run:
```powershell
./CLEAN_INSTALL.ps1
```

**What this does:**
- Removes corrupted `node_modules` folder
- Removes `package-lock.json`
- Installs fresh dependencies with `--legacy-peer-deps`

This will take 5-10 minutes.

### Alternative: Manual Clean Install

If the script doesn't work, do this manually:

**In PowerShell:**
```powershell
# Remove node_modules
Remove-Item -Recurse -Force node_modules

# Remove package-lock.json
Remove-Item -Force package-lock.json

# Reinstall
npm install --legacy-peer-deps
```

### Step 2: Start the Dev Server

Once installation completes, run:
```powershell
npm run dev
```

You should see:
```
▲ Next.js 15.2.4
- Local:        http://localhost:3000
```

### Step 3: Open Browser

Go to: **http://localhost:3000**

---

## Troubleshooting

### Still getting errors?

Try with --force flag:
```powershell
npm install --legacy-peer-deps --force
```

### Port 3000 in use?

Use a different port:
```powershell
npm run dev -- -p 3001
```

Then go to: http://localhost:3001

### Still not working?

Check Node.js version:
```powershell
node --version
# Should be v16+
```

---

## ✅ Once It Works

You should see the login page at http://localhost:3000

Enter your backend credentials to login!

---

**Questions?** See `STARTUP_GUIDE.md` or `RUN_BACKEND.md`
