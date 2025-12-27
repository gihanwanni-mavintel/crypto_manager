#!/bin/bash

###############################################################################
# Crypto Trading Backend - Deployment Script
# Usage: ./deploy.sh
###############################################################################

set -e  # Exit on error

echo "🚀 Starting deployment..."

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
APP_DIR="/opt/crypto-trading/Intelligent_Crypto_User_Management"
SERVICE_NAME="crypto-trading"

# Check if running as correct user
if [ "$USER" != "crypto-trader" ] && [ "$USER" != "root" ]; then
    echo -e "${RED}❌ Please run as crypto-trader user${NC}"
    exit 1
fi

# Navigate to application directory
cd "$APP_DIR" || exit 1

echo -e "${YELLOW}📥 Pulling latest code...${NC}"
git fetch origin
git pull origin Dev-V2

echo -e "${YELLOW}🔨 Building application...${NC}"
mvn clean package -DskipTests

echo -e "${YELLOW}🔄 Restarting service...${NC}"
sudo systemctl restart "$SERVICE_NAME"

echo -e "${YELLOW}⏳ Waiting for application to start...${NC}"
sleep 5

# Check if service is running
if sudo systemctl is-active --quiet "$SERVICE_NAME"; then
    echo -e "${GREEN}✅ Service is running${NC}"
else
    echo -e "${RED}❌ Service failed to start${NC}"
    echo -e "${YELLOW}📋 Last 20 log lines:${NC}"
    sudo journalctl -u "$SERVICE_NAME" -n 20 --no-pager
    exit 1
fi

# Test health endpoint
echo -e "${YELLOW}🏥 Testing health endpoint...${NC}"
if curl -f -s http://localhost:8081/auth/health > /dev/null; then
    echo -e "${GREEN}✅ Health check passed${NC}"
else
    echo -e "${RED}❌ Health check failed${NC}"
    exit 1
fi

echo -e "${GREEN}🎉 Deployment completed successfully!${NC}"

# Show recent logs
echo -e "${YELLOW}📋 Recent logs:${NC}"
sudo journalctl -u "$SERVICE_NAME" -n 10 --no-pager

echo ""
echo -e "${GREEN}Deployment Summary:${NC}"
echo "  - Branch: Dev-V2"
echo "  - Build: SUCCESS"
echo "  - Service: RUNNING"
echo "  - Health: OK"
echo ""
echo -e "${YELLOW}View logs: ${NC}sudo journalctl -u crypto-trading -f"
