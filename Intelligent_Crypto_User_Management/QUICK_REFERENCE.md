# Quick Reference - Production Commands

## Essential Commands Cheat Sheet

### 🔄 Application Management

```bash
# Start application
sudo systemctl start crypto-trading

# Stop application
sudo systemctl stop crypto-trading

# Restart application
sudo systemctl restart crypto-trading

# Check status
sudo systemctl status crypto-trading

# Enable auto-start on boot
sudo systemctl enable crypto-trading

# Disable auto-start on boot
sudo systemctl disable crypto-trading
```

### 📋 View Logs

```bash
# Real-time logs (follow)
sudo journalctl -u crypto-trading -f

# Last 50 lines
sudo journalctl -u crypto-trading -n 50

# Last 100 lines
sudo journalctl -u crypto-trading -n 100

# Logs from today
sudo journalctl -u crypto-trading --since today

# Logs from last hour
sudo journalctl -u crypto-trading --since "1 hour ago"

# Search logs for error
sudo journalctl -u crypto-trading | grep ERROR

# Export logs to file
sudo journalctl -u crypto-trading > logs.txt
```

### 🚀 Deployment

```bash
# Quick deployment (pull + build + restart)
cd /opt/crypto-trading/Intelligent_Crypto_User_Management
./deploy.sh

# Manual deployment
git pull origin Dev-V2
mvn clean package -DskipTests
sudo systemctl restart crypto-trading
```

### 🌐 Nginx Management

```bash
# Start Nginx
sudo systemctl start nginx

# Stop Nginx
sudo systemctl stop nginx

# Restart Nginx
sudo systemctl restart nginx

# Reload configuration (no downtime)
sudo systemctl reload nginx

# Test configuration
sudo nginx -t

# View access logs
sudo tail -f /var/log/nginx/access.log

# View error logs
sudo tail -f /var/log/nginx/error.log
```

### 🏥 Health Checks

```bash
# Test local application
curl http://localhost:8081/auth/health

# Test via Nginx (HTTP)
curl http://your_droplet_ip/auth/health

# Test via Nginx (HTTPS with domain)
curl https://api.yourdomain.com/auth/health

# Test signals endpoint
curl http://localhost:8081/api/signals

# Test webhook endpoint
curl -X POST http://localhost:8081/api/webhook/health
```

### 📊 Monitoring

```bash
# Check system resources
htop

# Check disk usage
df -h

# Check memory usage
free -h

# Check CPU usage
top

# Check application process
ps aux | grep java

# Check port usage
sudo netstat -tulpn | grep 8081

# Check all listening ports
sudo netstat -tulpn

# Check network connections
sudo ss -tunap
```

### 🔥 Firewall (UFW)

```bash
# Check firewall status
sudo ufw status

# Enable firewall
sudo ufw enable

# Disable firewall
sudo ufw disable

# Allow port
sudo ufw allow 8081

# Delete rule
sudo ufw delete allow 8081

# List numbered rules
sudo ufw status numbered

# Delete rule by number
sudo ufw delete [number]
```

### 🔐 SSL/TLS (Let's Encrypt)

```bash
# Obtain certificate
sudo certbot --nginx -d api.yourdomain.com

# Renew certificate (manual)
sudo certbot renew

# Test renewal
sudo certbot renew --dry-run

# List certificates
sudo certbot certificates

# View certificate expiry
sudo certbot certificates | grep Expiry
```

### 📦 Database Operations

```bash
# Connect to database
psql "postgresql://username:password@host:5432/neondb?sslmode=require"

# Export database
pg_dump "postgresql://username:password@host:5432/neondb?sslmode=require" > backup.sql

# Import database
psql "postgresql://username:password@host:5432/neondb?sslmode=require" < backup.sql

# Check table sizes
psql -c "SELECT tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size FROM pg_tables WHERE schemaname = 'public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;"
```

### 🔧 Git Operations

```bash
# Check current branch
git branch

# Pull latest changes
git pull origin Dev-V2

# View commit history
git log --oneline -10

# View remote URL
git remote -v

# Check for changes
git status

# Discard local changes
git reset --hard origin/Dev-V2

# Switch branch
git checkout main
git checkout Dev-V2
```

### 🛠️ Build Operations

```bash
# Build with tests
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Clean only
mvn clean

# Run tests only
mvn test

# View dependency tree
mvn dependency:tree

# Update dependencies
mvn versions:display-dependency-updates
```

### 📁 File Operations

```bash
# View .env file
cat /opt/crypto-trading/Intelligent_Crypto_User_Management/.env

# Edit .env file
nano /opt/crypto-trading/Intelligent_Crypto_User_Management/.env

# Check file permissions
ls -la /opt/crypto-trading/Intelligent_Crypto_User_Management/

# Change ownership
sudo chown -R crypto-trader:crypto-trader /opt/crypto-trading/

# Make script executable
chmod +x deploy.sh
```

### 🔄 System Maintenance

```bash
# Update system packages
sudo apt update
sudo apt upgrade -y

# Clean package cache
sudo apt clean
sudo apt autoclean

# Remove unnecessary packages
sudo apt autoremove -y

# Check system uptime
uptime

# Reboot system
sudo reboot

# Shutdown system
sudo shutdown -h now
```

### 📈 Performance Tuning

```bash
# Check Java process memory
ps aux | grep java | grep -v grep

# Get Java heap dump
sudo -u crypto-trader jmap -dump:live,format=b,file=heap.bin $(pgrep -f Intelligent_Crypto_User_Management)

# View Java threads
sudo -u crypto-trader jstack $(pgrep -f Intelligent_Crypto_User_Management)

# Monitor garbage collection
sudo -u crypto-trader jstat -gc $(pgrep -f Intelligent_Crypto_User_Management) 1000
```

### 🐛 Debugging

```bash
# Check if application is listening
sudo netstat -tulpn | grep :8081

# Test database connection
psql "$DB_URL"

# Check environment variables
sudo systemctl show crypto-trading | grep Environment

# Verify JAR file
ls -lh /opt/crypto-trading/Intelligent_Crypto_User_Management/target/*.jar

# Check for errors in logs
sudo journalctl -u crypto-trading | grep -i error | tail -20

# Check for warnings in logs
sudo journalctl -u crypto-trading | grep -i warn | tail -20
```

---

## Common Issues & Solutions

### 1. Application won't start

```bash
# Check logs
sudo journalctl -u crypto-trading -n 50

# Check if port is in use
sudo netstat -tulpn | grep 8081

# Verify JAR exists
ls -lh /opt/crypto-trading/Intelligent_Crypto_User_Management/target/*.jar

# Test database connection
psql "$DB_URL"
```

### 2. 502 Bad Gateway

```bash
# Check application status
sudo systemctl status crypto-trading

# Check if listening on port
curl http://localhost:8081/auth/health

# Restart application
sudo systemctl restart crypto-trading
```

### 3. High memory usage

```bash
# Check memory
free -h

# Adjust JVM memory in systemd service
sudo nano /etc/systemd/system/crypto-trading.service
# Modify: -Xms512m -Xmx1024m

# Reload and restart
sudo systemctl daemon-reload
sudo systemctl restart crypto-trading
```

### 4. Database connection issues

```bash
# Test connection
psql "$DB_URL"

# Check logs for database errors
sudo journalctl -u crypto-trading | grep -i database

# Verify environment variables
sudo systemctl show crypto-trading | grep DB_URL
```

---

## Emergency Procedures

### Stop everything

```bash
sudo systemctl stop crypto-trading
sudo systemctl stop nginx
```

### Restart everything

```bash
sudo systemctl restart crypto-trading
sudo systemctl restart nginx
```

### Rollback deployment

```bash
cd /opt/crypto-trading/Intelligent_Crypto_User_Management
git log --oneline -5  # Find previous commit
git reset --hard <commit-hash>
mvn clean package -DskipTests
sudo systemctl restart crypto-trading
```

### Full system recovery

```bash
# Stop services
sudo systemctl stop crypto-trading

# Backup current JAR
cp target/Intelligent_Crypto_User_Management-0.0.1-SNAPSHOT.jar target/backup.jar

# Pull latest code
git fetch origin
git reset --hard origin/Dev-V2

# Rebuild
mvn clean package -DskipTests

# Restart
sudo systemctl start crypto-trading
```

---

## Production URLs

**Without Domain (IP only):**
- Health: `http://YOUR_IP/auth/health`
- Signals: `http://YOUR_IP/api/signals`
- Webhook: `http://YOUR_IP/api/webhook/signal`
- Trade Config: `http://YOUR_IP/api/trade-management-config`

**With Domain:**
- Health: `https://api.yourdomain.com/auth/health`
- Signals: `https://api.yourdomain.com/api/signals`
- Webhook: `https://api.yourdomain.com/api/webhook/signal`
- Trade Config: `https://api.yourdomain.com/api/trade-management-config`

---

## Daily Maintenance Checklist

- [ ] Check application status: `sudo systemctl status crypto-trading`
- [ ] Check logs for errors: `sudo journalctl -u crypto-trading --since today | grep ERROR`
- [ ] Check disk space: `df -h`
- [ ] Check memory usage: `free -h`
- [ ] Test health endpoint: `curl http://localhost:8081/auth/health`
- [ ] Check for system updates: `sudo apt update`

---

## Weekly Maintenance Checklist

- [ ] Review full application logs
- [ ] Check for security updates: `sudo apt upgrade`
- [ ] Backup database
- [ ] Review disk usage
- [ ] Check SSL certificate expiry
- [ ] Review trade execution logs

---

## Contact & Support

If you need help:
1. Check logs first
2. Review troubleshooting section in DEPLOYMENT_GUIDE.md
3. Search error messages in application logs

**Key log files:**
- Application: `sudo journalctl -u crypto-trading`
- Nginx: `/var/log/nginx/error.log`
- System: `/var/log/syslog`
