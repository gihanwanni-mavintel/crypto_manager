# Frontend Startup Guide

## Quick Start (Choose One)

### Option 1: Using Batch File (Windows)
```bash
double-click RUN_DEV.bat
```

### Option 2: Using PowerShell (Windows)
```powershell
./RUN_DEV.ps1
```

### Option 3: Using Terminal/Command Prompt
```bash
npm run dev
```

### Option 4: Manual Installation + Dev
```bash
npm install --legacy-peer-deps
npm run dev
```

---

## What Happens

When you run the dev server:

1. **Dependencies Check**
   - Installs packages if `node_modules` doesn't exist
   - Downloads ~1000+ npm packages

2. **Next.js Dev Server Starts**
   ```
   ▲ Next.js 15.2.4
   - Local:        http://localhost:3000
   - Environments: .env.local
   ```

3. **Access the Application**
   - Open browser
   - Go to: http://localhost:3000
   - Should redirect to login page

---

## First Time Setup

### Step 1: Navigate to Frontend Directory
```bash
cd frontend
```

### Step 2: Install Dependencies (if not done yet)
```bash
npm install --legacy-peer-deps
```

This installs all required packages. It may take a few minutes.

### Step 3: Start Dev Server
```bash
npm run dev
```

You should see:
```
▲ Next.js 15.2.4
- Local:        http://localhost:3000
- Environments: .env.local

Creating an optimized build ...
○ Compiling /
✓ Compiled /
```

### Step 4: Open Browser
Navigate to: **http://localhost:3000**

---

## Login

### Default Behavior
1. App redirects to login page
2. Enter your backend username/password
3. Click "Sign In"
4. JWT token saved automatically
5. Redirects to dashboard

### Credentials
Use your backend login credentials:
- **Username**: Your backend username
- **Password**: Your backend password

### Example (Change to Your Actual Credentials)
```
Username: admin
Password: password123
```

---

## What You'll See

### Login Page
- Crypto Position Manager header
- Username field
- Password field
- Sign In button
- API endpoint info at bottom

### Dashboard (After Login)
- Header with P&L indicator
- Sidebar with navigation
- Main content area with:
  - Trading signals
  - Positions
  - Manual trading
  - Trade history

---

## Configuration

### Backend URL
Edit `frontend/.env.local`:
```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=wss://telegramsignals-production.up.railway.app
```

**For Production:**
```env
NEXT_PUBLIC_API_URL=https://your-backend.com
NEXT_PUBLIC_WS_URL=wss://your-websocket-server.com
```

---

## Troubleshooting

### npm install fails
```bash
npm install --legacy-peer-deps --force
```

### Port 3000 Already in Use
Use different port:
```bash
npm run dev -- -p 3001
```
Then access: http://localhost:3001

### Cannot Connect to Backend
1. Verify backend is running: `http://localhost:8080/auth/health`
2. Check `.env.local` has correct URL
3. Backend should be on port 8080

### Login Fails
1. Verify credentials are correct
2. Check backend `/auth/login` endpoint works
3. Check browser console for error messages
4. Verify backend database has the user

### WebSocket Connection Error
1. Check `NEXT_PUBLIC_WS_URL` in `.env.local`
2. Verify WebSocket server is running
3. Check browser DevTools Console
4. Verify firewall allows WebSocket

### Blank Page or Errors
1. Check browser DevTools Console (F12)
2. Check Network tab for API calls
3. Verify backend is running
4. Clear browser cache and reload

---

## Development Tips

### Hot Reload
- Changes automatically reload
- Save file and see changes instantly
- No need to restart server

### Debug Console
- Open browser DevTools: F12
- Check Console for JavaScript errors
- Check Network tab for API calls
- Check Application tab for localStorage

### API Testing
Use browser DevTools Network tab:
1. Open DevTools (F12)
2. Go to Network tab
3. Login to see `/auth/login` request
4. Check response contains JWT token
5. Other API calls show in Network tab

---

## File Locations

| File | Purpose |
|------|---------|
| `lib/api.ts` | Backend API client |
| `.env.local` | Configuration |
| `app/page.tsx` | Dashboard |
| `app/login/page.tsx` | Login page |
| `components/` | UI components |

---

## Production Build

When ready to deploy:

```bash
# Build optimized bundle
npm run build

# Start production server
npm start
```

Then access: http://localhost:3000

---

## Helpful Commands

```bash
# Install dependencies
npm install --legacy-peer-deps

# Start development server
npm run dev

# Build for production
npm run build

# Start production server
npm start

# Check TypeScript errors (if enabled)
npm run type-check
```

---

## Getting Help

1. **Quick Issues?** → Check browser console (F12)
2. **API Issues?** → Check Network tab in DevTools
3. **Configuration?** → Edit `.env.local`
4. **Backend Issues?** → Check backend logs
5. **Documentation?** → Read files in project root

---

## Next Steps After Login

1. ✅ Verify dashboard loads
2. ✅ Check WebSocket connection in Console
3. ✅ Review API calls in Network tab
4. ✅ Test trading signals display
5. ✅ Check position management

---

## Summary

**To start:**
1. `cd frontend`
2. `npm install --legacy-peer-deps` (first time only)
3. `npm run dev`
4. Open http://localhost:3000
5. Login with your credentials

**That's it!** Your frontend is ready to use.

---

**Questions?** See documentation files:
- QUICK_START.md
- INTEGRATION_GUIDE.md
- COMPLETION_REPORT.md

**Happy trading! 🚀**
