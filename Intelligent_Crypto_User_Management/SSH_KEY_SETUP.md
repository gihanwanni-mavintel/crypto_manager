# SSH Key Setup Guide for Digital Ocean

## What are SSH Keys?

SSH keys are a pair of cryptographic keys that provide a more secure way to log into servers compared to passwords. They consist of:
- **Private key** - Stays on your computer (NEVER share this)
- **Public key** - Goes on the server (safe to share)

---

## Step 1: Check if You Already Have SSH Keys

### On Windows (PowerShell or Git Bash):
```bash
ls ~/.ssh/id_*.pub
```

### On Mac/Linux (Terminal):
```bash
ls ~/.ssh/id_*.pub
```

**If you see files like `id_rsa.pub` or `id_ed25519.pub`**, you already have SSH keys! Skip to Step 3.

**If you see "No such file or directory"**, continue to Step 2.

---

## Step 2: Generate New SSH Keys

### Option A: Windows (PowerShell)

1. Open **PowerShell** (not Command Prompt)

2. Generate SSH key:
```powershell
ssh-keygen -t ed25519 -C "your_email@example.com"
```

3. When prompted:
   - **"Enter file in which to save the key"** → Press `Enter` (use default location)
   - **"Enter passphrase"** → Press `Enter` (or create a passphrase for extra security)
   - **"Enter same passphrase again"** → Press `Enter` again

4. Your keys are created:
   - Private key: `C:\Users\YourName\.ssh\id_ed25519`
   - Public key: `C:\Users\YourName\.ssh\id_ed25519.pub`

### Option B: Windows (Git Bash)

1. Open **Git Bash** (installed with Git for Windows)

2. Generate SSH key:
```bash
ssh-keygen -t ed25519 -C "your_email@example.com"
```

3. Follow the same prompts as above

### Option C: Mac/Linux (Terminal)

1. Open **Terminal**

2. Generate SSH key:
```bash
ssh-keygen -t ed25519 -C "your_email@example.com"
```

3. Follow the same prompts as above

---

## Step 3: Copy Your Public Key

You need to copy the **public key** (the `.pub` file) to add it to Digital Ocean.

### Windows (PowerShell):

```powershell
# Display public key
Get-Content ~\.ssh\id_ed25519.pub

# Or copy to clipboard
Get-Content ~\.ssh\id_ed25519.pub | Set-Clipboard
```

### Windows (Git Bash):

```bash
# Display public key
cat ~/.ssh/id_ed25519.pub

# Or copy to clipboard (if clip.exe available)
cat ~/.ssh/id_ed25519.pub | clip
```

### Mac:

```bash
# Display public key
cat ~/.ssh/id_ed25519.pub

# Or copy to clipboard
pbcopy < ~/.ssh/id_ed25519.pub
```

### Linux:

```bash
# Display public key
cat ~/.ssh/id_ed25519.pub

# Or copy to clipboard (if xclip installed)
cat ~/.ssh/id_ed25519.pub | xclip -selection clipboard
```

You should see something like:
```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJl3dIeudNqd0FEWW8xkU... your_email@example.com
```

**Copy this entire line!**

---

## Step 4: Add SSH Key to Digital Ocean

### Method 1: During Droplet Creation (Recommended)

1. **Go to Digital Ocean** → Click **Create** → **Droplets**

2. **Scroll down to "Authentication"** section

3. Click **"New SSH Key"** button

4. **Paste your public key** (the line you copied in Step 3)

5. **Give it a name** like:
   - "My Laptop"
   - "Work Computer"
   - "Windows PC"

6. Click **"Add SSH Key"**

7. **Select the checkbox** next to your new SSH key

8. Continue with droplet creation

### Method 2: Add to Account (For Future Use)

1. **Go to Digital Ocean Dashboard**

2. Click your **profile icon** (top right) → **Settings**

3. Click **"Security"** in left sidebar

4. Scroll to **"SSH keys"** section

5. Click **"Add SSH Key"**

6. **Paste your public key**

7. **Give it a name**

8. Click **"Add SSH Key"**

Now when you create droplets, this key will be available to select.

---

## Step 5: Connect to Your Droplet Using SSH Key

Once your droplet is created with your SSH key:

### Windows (PowerShell or Git Bash):

```bash
ssh root@your_droplet_ip
```

### Mac/Linux (Terminal):

```bash
ssh root@your_droplet_ip
```

**No password required!** It will use your SSH key automatically.

---

## Visual Guide

### What You'll See in Digital Ocean:

```
┌─────────────────────────────────────────┐
│  Choose Authentication Method          │
├─────────────────────────────────────────┤
│  ○ Password                             │
│  ● SSH Keys (Recommended)               │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │ ☑ My Laptop (added 2 days ago)    │ │
│  └───────────────────────────────────┘ │
│                                         │
│  [+ New SSH Key]                        │
└─────────────────────────────────────────┘
```

### Adding New SSH Key Dialog:

```
┌──────────────────────────────────────────┐
│  Add SSH Key                             │
├──────────────────────────────────────────┤
│  SSH Key Content *                       │
│  ┌────────────────────────────────────┐ │
│  │ ssh-ed25519 AAAAC3NzaC1lZDI1...   │ │
│  │                                    │ │
│  └────────────────────────────────────┘ │
│                                          │
│  Name *                                  │
│  ┌────────────────────────────────────┐ │
│  │ My Laptop                          │ │
│  └────────────────────────────────────┘ │
│                                          │
│  [Cancel]              [Add SSH Key]    │
└──────────────────────────────────────────┘
```

---

## Common Issues & Solutions

### Issue 1: "Permission denied (publickey)"

**Solution:**
```bash
# Ensure correct permissions on Windows/Mac/Linux
chmod 700 ~/.ssh
chmod 600 ~/.ssh/id_ed25519
chmod 644 ~/.ssh/id_ed25519.pub
```

### Issue 2: SSH key not working

**Solutions:**
1. Verify you copied the **PUBLIC key** (.pub file), not the private key
2. Make sure you selected the SSH key when creating the droplet
3. Try connecting with verbose mode to see errors:
   ```bash
   ssh -v root@your_droplet_ip
   ```

### Issue 3: Can't find .ssh folder

**Windows:**
```powershell
# Create .ssh folder if it doesn't exist
mkdir ~\.ssh
```

**Mac/Linux:**
```bash
# Create .ssh folder if it doesn't exist
mkdir -p ~/.ssh
chmod 700 ~/.ssh
```

### Issue 4: "ssh-keygen not found" on Windows

**Solutions:**
1. Use **Git Bash** instead of Command Prompt
2. Or install [OpenSSH for Windows](https://docs.microsoft.com/en-us/windows-server/administration/openssh/openssh_install_firstuse)
3. Or use **PuTTY** and **PuTTYgen** (alternative SSH client for Windows)

---

## Alternative: Using PuTTY on Windows

If you prefer PuTTY:

### 1. Download PuTTY
- Go to: https://www.putty.org/
- Download and install **PuTTY** and **PuTTYgen**

### 2. Generate Key with PuTTYgen

1. Open **PuTTYgen**
2. Click **"Generate"**
3. Move mouse randomly to generate randomness
4. Copy the **public key** from the text box (starts with `ssh-rsa`)
5. Click **"Save private key"** (save as `crypto-droplet.ppk`)
6. Add the public key to Digital Ocean (Step 4 above)

### 3. Connect with PuTTY

1. Open **PuTTY**
2. Enter **Host Name**: `root@your_droplet_ip`
3. Go to **Connection** → **SSH** → **Auth**
4. Browse and select your `.ppk` file
5. Click **"Open"**

---

## Security Best Practices

### ✅ DO:
- Use SSH keys instead of passwords
- Add a passphrase to your private key for extra security
- Keep your private key safe (never share it)
- Use different SSH keys for different purposes
- Store private keys in `~/.ssh/` with proper permissions

### ❌ DON'T:
- Share your private key (the file WITHOUT `.pub`)
- Commit private keys to Git/GitHub
- Use the same key everywhere (consider separate keys for work/personal)
- Store private keys in Dropbox/cloud storage
- Email your private key

---

## Multiple SSH Keys (Advanced)

If you have multiple SSH keys for different servers:

### Create SSH Config File

**Windows:** `C:\Users\YourName\.ssh\config`
**Mac/Linux:** `~/.ssh/config`

```ssh-config
# Digital Ocean Crypto Trading Server
Host crypto-do
    HostName your_droplet_ip
    User root
    IdentityFile ~/.ssh/id_ed25519

# GitHub (if you have separate key)
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_rsa_github
```

Then connect with:
```bash
ssh crypto-do
```

---

## Quick Reference Commands

```bash
# Generate new SSH key
ssh-keygen -t ed25519 -C "your_email@example.com"

# View public key (copy this to Digital Ocean)
cat ~/.ssh/id_ed25519.pub

# Connect to droplet
ssh root@your_droplet_ip

# Connect to droplet as non-root user
ssh crypto-trader@your_droplet_ip

# Copy file to droplet
scp file.txt root@your_droplet_ip:/path/to/destination/

# Copy file from droplet
scp root@your_droplet_ip:/path/to/file.txt ./local/path/
```

---

## Troubleshooting Checklist

When SSH connection fails:

- [ ] Did you copy the **public key** (.pub file)?
- [ ] Did you select the SSH key when creating the droplet?
- [ ] Is the droplet IP address correct?
- [ ] Is your internet connection working?
- [ ] Did you wait for the droplet to finish initializing (1-2 minutes)?
- [ ] Try verbose mode: `ssh -v root@your_droplet_ip`
- [ ] Check firewall isn't blocking SSH (port 22)

---

## Next Steps

Once SSH key is working:

1. ✅ SSH key added to Digital Ocean
2. ✅ Can connect to droplet with `ssh root@your_droplet_ip`
3. → Continue with **DEPLOYMENT_GUIDE.md** - Step 2: Initial Server Setup

---

## Need Help?

**Test your SSH key locally first:**
```bash
# This should show your public key
cat ~/.ssh/id_ed25519.pub

# This should work (connects to local SSH if running)
ssh-keygen -l -f ~/.ssh/id_ed25519.pub
```

**Check Digital Ocean SSH key was added:**
1. Go to Digital Ocean Dashboard
2. Click Settings → Security
3. Scroll to "SSH keys" section
4. Your key should be listed there

**Still having issues?**
- Digital Ocean SSH Tutorial: https://docs.digitalocean.com/products/droplets/how-to/add-ssh-keys/
- Check droplet was created with the SSH key selected
- Verify you can ping the droplet: `ping your_droplet_ip`
