# Cloud Deployment Guide: Microsoft Azure (Azure Container Apps + MongoDB Atlas / Cosmos DB)

This guide documents deploying the RoxStar Real-Time backend to Microsoft Azure using **Azure Container Apps** with WebSockets enabled and Azure Key Vault integration.

---

## 1. Prerequisites
- Azure CLI (`az`) installed and authenticated (`az login`)
- Azure Resource Group created: `az group create --name roxstar-rg --location eastus`

---

## 2. Infrastructure Setup Steps

### Step 2.1: Create Azure Container Registry (ACR)
```bash
az acr create \
    --resource-group roxstar-rg \
    --name roxstarregistry \
    --sku Basic \
    --admin-enabled true
```

### Step 2.2: Build & Push Image
```bash
az acr build \
    --registry roxstarregistry \
    --image roxstar-backend:latest ./backend
```

### Step 2.3: Create Container Apps Environment
```bash
az containerapp env create \
    --name roxstar-env \
    --resource-group roxstar-rg \
    --location eastus
```

### Step 2.4: Deploy Container App with WebSockets & Session Affinity
```bash
az containerapp create \
    --name roxstar-backend \
    --resource-group roxstar-rg \
    --environment roxstar-env \
    --image roxstarregistry.azurecr.io/roxstar-backend:latest \
    --target-port 4000 \
    --ingress 'external' \
    --min-replicas 1 \
    --max-replicas 5 \
    --enable-session-affinity true \
    --secrets mongo-uri="mongodb+srv://roxstar:<PASSWORD>@cluster0.mongodb.net/roxstar_db" \
    --env-vars PORT=4000 NODE_ENV=production CORS_ORIGIN="*" MONGO_URI=secretref:mongo-uri
```

---

## 3. Health Verification
```bash
FQDN=$(az containerapp show --name roxstar-backend --resource-group roxstar-rg --query properties.configuration.ingress.fqdn -o tsv)
curl -s "https://${FQDN}/api/health"
```

---

## 4. Rollback Procedure
```bash
# List active revisions
az containerapp revision list --name roxstar-backend --resource-group roxstar-rg -o table

# Switch 100% traffic back to previous stable revision
az containerapp ingress traffic set \
    --name roxstar-backend \
    --resource-group roxstar-rg \
    --revision-weight <PREVIOUS_REVISION_NAME>=100
```
