#!/bin/bash

###############################################################################
# Initial Server Setup Script for Digital Ocean Droplet
# Run this ONCE on a fresh Ubuntu 22.04 droplet
# Usage: sudo bash server-setup.sh
###############################################################################

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Crypto Trading Backend Setup         ║${NC}"
echo -e "${GREEN}║  Digital Ocean Droplet Configuration  ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ Please run as root (use sudo)${NC}"
    exit 1
fi

echo -e "${YELLOW}📦 Step 1: Updating system packages...${NC}"
apt update
apt upgrade -y

echo -e "${YELLOW}☕ Step 2: Installing Java 17...${NC}"
apt install openjdk-17-jdk -y
java -version

echo -e "${YELLOW}📦 Step 3: Installing Maven...${NC}"
apt install maven -y
mvn -version

echo -e "${YELLOW}🌐 Step 4: Installing Nginx...${NC}"
apt install nginx -y
systemctl enable nginx
systemctl start nginx

echo -e "${YELLOW}🔧 Step 5: Installing Git...${NC}"
apt install git -y

echo -e "${YELLOW}🗄️  Step 6: Installing PostgreSQL client...${NC}"
apt install postgresql-client -y

echo -e "${YELLOW}📊 Step 7: Installing monitoring tools...${NC}"
apt install htop net-tools curl wget -y

echo -e "${YELLOW}👤 Step 8: Creating application user...${NC}"
if id "crypto-trader" &>/dev/null; then
    echo -e "${GREEN}✅ User 'crypto-trader' already exists${NC}"
else
    useradd -m -s /bin/bash crypto-trader
    usermod -aG sudo crypto-trader
    echo -e "${GREEN}✅ User 'crypto-trader' created${NC}"
    echo -e "${YELLOW}   Please set password: passwd crypto-trader${NC}"
fi

echo -e "${YELLOW}📁 Step 9: Creating application directory...${NC}"
mkdir -p /opt/crypto-trading
chown -R crypto-trader:crypto-trader /opt/crypto-trading
echo -e "${GREEN}✅ Directory created: /opt/crypto-trading${NC}"

echo -e "${YELLOW}🔐 Step 10: Configuring firewall (UFW)...${NC}"
ufw --force enable
ufw allow OpenSSH
ufw allow 'Nginx HTTP'
ufw allow 'Nginx HTTPS'
ufw status

echo -e "${YELLOW}🔧 Step 11: Configuring system limits...${NC}"
cat >> /etc/security/limits.conf << EOF
crypto-trader soft nofile 65536
crypto-trader hard nofile 65536
crypto-trader soft nproc 4096
crypto-trader hard nproc 4096
EOF

echo -e "${YELLOW}⏰ Step 12: Setting timezone to UTC...${NC}"
timedatectl set-timezone UTC

echo ""
echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ✅ Server Setup Complete!            ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Set password for crypto-trader user:"
echo -e "   ${GREEN}passwd crypto-trader${NC}"
echo ""
echo "2. Copy SSH key for crypto-trader:"
echo -e "   ${GREEN}rsync --archive --chown=crypto-trader:crypto-trader ~/.ssh /home/crypto-trader${NC}"
echo ""
echo "3. Switch to crypto-trader user:"
echo -e "   ${GREEN}su - crypto-trader${NC}"
echo ""
echo "4. Clone your repository:"
echo -e "   ${GREEN}cd /opt/crypto-trading${NC}"
echo -e "   ${GREEN}git clone <your-repo-url> .${NC}"
echo ""
echo "5. Navigate to backend directory:"
echo -e "   ${GREEN}cd Intelligent_Crypto_User_Management${NC}"
echo ""
echo "6. Create .env file with your credentials:"
echo -e "   ${GREEN}nano .env${NC}"
echo ""
echo "7. Build the application:"
echo -e "   ${GREEN}mvn clean package -DskipTests${NC}"
echo ""
echo "8. Copy and configure systemd service:"
echo -e "   ${GREEN}sudo cp crypto-trading.service /etc/systemd/system/${NC}"
echo -e "   ${GREEN}sudo systemctl daemon-reload${NC}"
echo -e "   ${GREEN}sudo systemctl enable crypto-trading${NC}"
echo -e "   ${GREEN}sudo systemctl start crypto-trading${NC}"
echo ""
echo -e "${YELLOW}📋 Check installation:${NC}"
echo "  Java:   $(java -version 2>&1 | head -n 1)"
echo "  Maven:  $(mvn -version 2>&1 | head -n 1)"
echo "  Nginx:  $(nginx -v 2>&1)"
echo ""
echo -e "${GREEN}🎉 All done! Follow the next steps above to deploy your application.${NC}"
