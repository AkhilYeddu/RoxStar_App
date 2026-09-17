# Cloud Deployment Guide: Google Cloud Platform (Cloud Run + MongoDB Atlas)

This guide documents deploying the RoxStar Real-Time backend to Google Cloud Platform using **Google Cloud Run** with WebSocket session affinity and Google Secret Manager.

---

## 1. Prerequisites
- Google Cloud SDK (`gcloud`) installed and authorized
- Project created: `gcloud config set project <GCP_PROJECT_ID>`

---

## 2. Infrastructure Setup Steps

### Step 2.1: Enable Required GCP APIs
```bash
gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com
```

### Step 2.2: Create Artifact Registry
```bash
gcloud artifacts repositories create roxstar-repo \
    --repository-format=docker \
    --location=us-central1 \
    --description="RoxStar Docker repository"
```

### Step 2.3: Build & Push Image using Cloud Build
```bash
gcloud builds submit backend \
    --tag us-central1-docker.pkg.dev/<GCP_PROJECT_ID>/roxstar-repo/roxstar-backend:latest
```

### Step 2.4: Store MongoDB URI in Google Secret Manager
```bash
echo -n "mongodb+srv://roxstar:<PASSWORD>@cluster0.mongodb.net/roxstar_db" | \
    gcloud secrets create roxstar-mongo-uri --data-file=-
```

### Step 2.5: Deploy to Cloud Run with Session Affinity (WebSockets)
```bash
gcloud run deploy roxstar-backend \
    --image us-central1-docker.pkg.dev/<GCP_PROJECT_ID>/roxstar-repo/roxstar-backend:latest \
    --platform managed \
    --region us-central1 \
    --allow-unauthenticated \
    --port 4000 \
    --min-instances 1 \
    --max-instances 10 \
    --session-affinity \
    --set-secrets MONGO_URI=roxstar-mongo-uri:latest \
    --set-env-vars NODE_ENV=production,CORS_ORIGIN=*,SPIN_INTERVAL_MS=5000
```

---

## 3. Health Verification
```bash
SERVICE_URL=$(gcloud run services describe roxstar-backend --platform managed --region us-central1 --format 'value(status.url)')
curl -s "${SERVICE_URL}/api/health"
```

---

## 4. Rollback Procedure
```bash
# List previous revisions
gcloud run revisions list --service roxstar-backend --region us-central1

# Route 100% of traffic back to the previous stable revision
gcloud run services update-traffic roxstar-backend \
    --region us-central1 \
    --to-revisions <PREVIOUS_REVISION_NAME>=100
```
