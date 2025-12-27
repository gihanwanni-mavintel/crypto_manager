# Digital Ocean Droplet Deployment Guide
## Java Spring Boot Backend - Complete Setup

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Step 1: Create Digital Ocean Droplet](#step-1-create-digital-ocean-droplet)
3. [Step 2: Initial Server Setup](#step-2-initial-server-setup)
4. [Step 3: Install Required Software](#step-3-install-required-software)
5. [Step 4: Deploy Application](#step-4-deploy-application)
6. [Step 5: Configure Systemd Service](#step-5-configure-systemd-service)
7. [Step 6: Setup Nginx Reverse Proxy](#step-6-setup-nginx-reverse-proxy)
8. [Step 7: Configure SSL with Let's Encrypt](#step-7-configure-ssl-with-lets-encrypt)
9. [Step 8: Configure Firewall](#step-8-configure-firewall)
10. [Troubleshooting](#troubleshooting)
11. [Maintenance Commands](#maintenance-commands)

---

## Prerequisites

- Digital Ocean account
- Domain name (optional but recommended for SSL)
- SSH key pair
- Your `.env` file with production credentials

---

## Step 1: Create Digital Ocean Droplet

### 1.1 Create Droplet via Digital Ocean Dashboard

1. Log in to Digital Ocean
2. Click **Create** → **Droplets**
3. Choose configuration:
   - **Image**: Ubuntu 22.04 LTS (recommended)
   - **Plan**: Basic
   - **CPU**: Regular (2 GB RAM / 1 CPU) - Minimum recommended
   - **Datacenter**: Choose closest to your users
   - **Authentication**: SSH Key (recommended) or Password
   - **Hostname**: `crypto-trading-api` (or your preference)

4. Click **Create Droplet**
5. Note the IP address once created

### 1.2 Point Your Domain (Optional)

If using a domain:
1. Go to your domain registrar
2. Add an **A Record**:
   - Host: `api` (or `@` for root domain)
   - Value: Your droplet IP address
   - TTL: 3600

Example: `api.yourdomain.com` → `157.230.xxx.xxx`

---

## Step 2: Initial Server Setup

### 2.1 Connect to Your Droplet

```bash
ssh root@your_droplet_ip
```

### 2.2 Create a Non-Root User

```bash
# Create user
adduser crypto-trader

# Add to sudo group
usermod -aG sudo crypto-trader

# Setup SSH for new user
rsync --archive --chown=crypto-trader:crypto-trader ~/.ssh /home/crypto-trader
```

### 2.3 Test New User

```bash
# Exit and reconnect as new user
exit
ssh crypto-trader@your_droplet_ip
```

---

## Step 3: Install Required Software

### 3.1 Update System

```bash
sudo apt update
sudo apt upgrade -y
```

### 3.2 Install Java 17

```bash
# Install OpenJDK 17
sudo apt install openjdk-17-jdk -y

# Verify installation
java -version
# Should output: openjdk version "17.x.x"
```

### 3.3 Install Maven

```bash
# Install Maven
sudo apt install maven -y

# Verify installation
mvn -version
```

### 3.4 Install Nginx

```bash
sudo apt install nginx -y
sudo systemctl enable nginx
```

### 3.5 Install Git

```bash
sudo apt install git -y
```

---

## Step 4: Deploy Application

### 4.1 Create Application Directory

```bash
# Create directory
sudo mkdir -p /opt/crypto-trading
sudo chown crypto-trader:crypto-trader /opt/crypto-trading
cd /opt/crypto-trading
```

### 4.2 Clone Repository

```bash
# Clone your repository
git clone https://github.com/yourusername/yourrepo.git .

# Or if using SSH
git clone git@github.com:yourusername/yourrepo.git .

# Navigate to Java backend
cd Intelligent_Crypto_User_Management
```

### 4.3 Create Production Environment File

```bash
# Create .env file
nano .env
```

**Paste your production environment variables** (update with your actual values):

```env
PORT=8081
DB_URL=jdbc:postgresql://ep-lingering-pond-a1jm0v0d-pooler.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=npg_Xz4BASnmv8yl
BINANCE_API_KEY=your_binance_api_key
BINANCE_API_SECRET=your_binance_secret
BINANCE_TESTNET_ENABLED=false
TRADE_AUTO_EXECUTE=true
TRADE_MAX_CONCURRENT=5
TRADE_POSITION_SIZE=50
TRADE_MIN_BALANCE=0.50
```

Save: `Ctrl+O`, `Enter`, `Ctrl+X`

### 4.4 Build the Application

```bash
# Build with Maven (skip tests for faster build)
mvn clean package -DskipTests

# Or build with tests
mvn clean package

# The JAR will be in: target/Intelligent_Crypto_User_Management-0.0.1-SNAPSHOT.jar
```

### 4.5 Test the Application

```bash
# Load environment variables
export $(cat .env | xargs)

# Run the application
java -jar target/Intelligent_Crypto_User_Management-0.0.1-SNAPSHOT.jar

# Test in another terminal
curl http://localhost:8081/auth/health

# If it works, stop with Ctrl+C
```

---

## Step 5: Configure Systemd Service

### 5.1 Create Service File

```bash
sudo nano /etc/systemd/system/crypto-trading.service
```

**Paste this configuration**:

```ini
[Unit]
Description=Crypto Trading Java Backend
After=network.target

[Service]
Type=simple
User=crypto-trader
WorkingDirectory=/opt/crypto-trading/Intelligent_Crypto_User_Management
EnvironmentFile=/opt/crypto-trading/Intelligent_Crypto_User_Management/.env
ExecStart=/usr/bin/java -jar /opt/crypto-trading/Intelligent_Crypto_User_Management/target/Intelligent_Crypto_User_Management-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=crypto-trading

# Resource limits
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

Save: `Ctrl+O`, `Enter`, `Ctrl+X`

### 5.2 Enable and Start Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service to start on boot
sudo systemctl enable crypto-trading

# Start the service
sudo systemctl start crypto-trading

# Check status
sudo systemctl status crypto-trading

# View logs
sudo journalctl -u crypto-trading -f
```

### 5.3 Verify Application is Running

```bash
curl http://localhost:8081/auth/health
```

---

## Step 6: Setup Nginx Reverse Proxy

### 6.1 Create Nginx Configuration

```bash
sudo nano /etc/nginx/sites-available/crypto-trading
```

**For IP-based access** (no domain):

```nginx
server {
    listen 80;
    server_name your_droplet_ip;

    client_max_body_size 10M;

    location / {
        proxy_pass http://localhost:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
```

**For domain-based access** (if you have a domain):

```nginx
server {
    listen 80;
    server_name api.yourdomain.com;

    client_max_body_size 10M;

    location / {
        proxy_pass http://localhost:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
```

Save: `Ctrl+O`, `Enter`, `Ctrl+X`

### 6.2 Enable the Configuration

```bash
# Create symbolic link
sudo ln -s /etc/nginx/sites-available/crypto-trading /etc/nginx/sites-enabled/

# Remove default configuration
sudo rm /etc/nginx/sites-enabled/default

# Test configuration
sudo nginx -t

# Reload Nginx
sudo systemctl reload nginx
```

### 6.3 Test Access

```bash
# Test via IP
curl http://your_droplet_ip/auth/health

# Or via domain
curl http://api.yourdomain.com/auth/health
```

---

## Step 7: Configure SSL with Let's Encrypt

**Only if you have a domain name**

### 7.1 Install Certbot

```bash
sudo apt install certbot python3-certbot-nginx -y
```

### 7.2 Obtain SSL Certificate

```bash
# Replace with your domain
sudo certbot --nginx -d api.yourdomain.com

# Follow prompts:
# - Enter email
# - Agree to terms
# - Choose to redirect HTTP to HTTPS (recommended: option 2)
```

### 7.3 Test Auto-Renewal

```bash
sudo certbot renew --dry-run
```

### 7.4 Verify HTTPS Access

```bash
curl https://api.yourdomain.com/auth/health
```

---

## Step 8: Configure Firewall

### 8.1 Setup UFW Firewall

```bash
# Allow SSH
sudo ufw allow OpenSSH

# Allow HTTP
sudo ufw allow 'Nginx HTTP'

# Allow HTTPS (if using SSL)
sudo ufw allow 'Nginx HTTPS'

# Enable firewall
sudo ufw enable

# Check status
sudo ufw status
```

---

## Troubleshooting

### Check Application Logs

```bash
# Real-time logs
sudo journalctl -u crypto-trading -f

# Last 100 lines
sudo journalctl -u crypto-trading -n 100

# Logs from today
sudo journalctl -u crypto-trading --since today
```

### Check Nginx Logs

```bash
# Access logs
sudo tail -f /var/log/nginx/access.log

# Error logs
sudo tail -f /var/log/nginx/error.log
```

### Common Issues

#### 1. Application Won't Start

```bash
# Check service status
sudo systemctl status crypto-trading

# Check if port is in use
sudo netstat -tulpn | grep 8081

# Check environment variables are loaded
sudo systemctl show crypto-trading | grep Environment
```

#### 2. 502 Bad Gateway

```bash
# Application might not be running
sudo systemctl status crypto-trading

# Check if application is listening
curl http://localhost:8081/auth/health
```

#### 3. Connection Refused

```bash
# Check firewall
sudo ufw status

# Check Nginx is running
sudo systemctl status nginx
```

#### 4. Database Connection Issues

```bash
# Test database connection from droplet
psql "postgresql://neondb_owner:npg_Xz4BASnmv8yl@ep-lingering-pond-a1jm0v0d-pooler.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require"

# If psql not installed
sudo apt install postgresql-client -y
```

---

## Maintenance Commands

### Restart Application

```bash
sudo systemctl restart crypto-trading
```

### Update Application

```bash
# Navigate to directory
cd /opt/crypto-trading/Intelligent_Crypto_User_Management

# Pull latest code
git pull origin Dev-V2

# Rebuild
mvn clean package -DskipTests

# Restart service
sudo systemctl restart crypto-trading

# Check status
sudo systemctl status crypto-trading
```

### Monitor Resource Usage

```bash
# CPU and memory
htop

# Disk usage
df -h

# Application process
ps aux | grep java
```

### Backup Database

```bash
# Export database
pg_dump "postgresql://neondb_owner:password@host/neondb?sslmode=require" > backup_$(date +%Y%m%d).sql
```

---

## Deployment Checklist

- [ ] Droplet created with Ubuntu 22.04
- [ ] Non-root user created
- [ ] Java 17 installed
- [ ] Maven installed
- [ ] Application cloned and built
- [ ] `.env` file configured with production credentials
- [ ] Systemd service created and running
- [ ] Nginx installed and configured
- [ ] SSL certificate installed (if using domain)
- [ ] Firewall configured
- [ ] Application accessible via HTTP/HTTPS
- [ ] Logs are clean and showing no errors
- [ ] Auto-restart on failure configured
- [ ] Auto-start on boot enabled

---

## Production URLs

**Without SSL:**
- Health check: `http://your_droplet_ip/auth/health`
- Signals API: `http://your_droplet_ip/api/signals`
- Webhook: `http://your_droplet_ip/api/webhook/signal`
- Trade Management: `http://your_droplet_ip/api/trade-management-config`

**With SSL (domain):**
- Health check: `https://api.yourdomain.com/auth/health`
- Signals API: `https://api.yourdomain.com/api/signals`
- Webhook: `https://api.yourdomain.com/api/webhook/signal`
- Trade Management: `https://api.yourdomain.com/api/trade-management-config`

---

## Next Steps

1. Update your frontend to point to the new backend URL
2. Update Python backend webhook URL to point to Digital Ocean droplet
3. Set up monitoring (optional): Grafana, Prometheus, or Digital Ocean Monitoring
4. Set up automated backups
5. Consider setting up a staging environment

---

## Security Recommendations

1. **Keep system updated**: `sudo apt update && sudo apt upgrade`
2. **Use strong passwords** for database and API keys
3. **Enable 2FA** on Digital Ocean account
4. **Regular backups** of database
5. **Monitor logs** for suspicious activity
6. **Use environment variables** - never commit secrets to git
7. **Limit SSH access** - use key-based authentication only
8. **Configure fail2ban** to prevent brute force attacks

---

## Support

If you encounter issues:
1. Check application logs: `sudo journalctl -u crypto-trading -f`
2. Check Nginx logs: `sudo tail -f /var/log/nginx/error.log`
3. Verify service status: `sudo systemctl status crypto-trading`
4. Test local access: `curl http://localhost:8081/auth/health`
