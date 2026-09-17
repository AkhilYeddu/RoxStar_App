# Cloud Deployment Guide: AWS (ECS Fargate + DocumentDB / MongoDB Atlas)

This guide documents deploying the RoxStar Real-Time backend to Amazon Web Services using AWS ECS Fargate and an Application Load Balancer with sticky sessions for WebSocket support.

---

## 1. Prerequisites
- AWS CLI configured (`aws configure`)
- Docker installed locally
- MongoDB Atlas cluster or AWS DocumentDB cluster running

---

## 2. Infrastructure Setup Steps

### Step 2.1: Create Amazon ECR Repository
```bash
aws ecr create-repository \
    --repository-name roxstar-backend \
    --region us-east-1
```

### Step 2.2: Authenticate & Push Docker Image
```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Build & tag image
docker build -t roxstar-backend:latest ./backend
docker tag roxstar-backend:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/roxstar-backend:latest

# Push image
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/roxstar-backend:latest
```

### Step 2.3: Store Secrets in AWS Secrets Manager
```bash
aws secretsmanager create-secret \
    --name "roxstar/mongo_uri" \
    --secret-string "mongodb+srv://roxstar_admin:<PASSWORD>@cluster0.mongodb.net/roxstar_db?retryWrites=true&w=majority"
```

### Step 2.4: Register Task Definition & Create ECS Service
```bash
# Register task definition
aws ecs register-task-definition --cli-input-json file://infrastructure/aws/ecs-task-definition.json

# Create ECS Service on Fargate
aws ecs create-service \
    --cluster roxstar-cluster \
    --service-name roxstar-backend-service \
    --task-definition roxstar-backend-task \
    --desired-count 2 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[subnet-abc,subnet-xyz],securityGroups=[sg-123],assignPublicIp=ENABLED}" \
    --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:<ACCOUNT_ID>:targetgroup/roxstar-tg/...,containerName=roxstar-backend,containerPort=4000"
```

---

## 3. Application Load Balancer Configuration for WebSocket Support
Socket.IO requires sticky sessions enabled on the Target Group:
```bash
aws elbv2 modify-target-group-attributes \
    --target-group-arn <TARGET_GROUP_ARN> \
    --attributes Key=stickiness.enabled,Value=true Key=stickiness.type,Value=lb_cookie Key=stickiness.lb_cookie.duration_seconds,Value=86400
```

---

## 4. Health Verification
Verify running container status via the `/api/health` endpoint:
```bash
curl -f https://api.roxstar.example.com/api/health
```
Expected output:
```json
{
  "status": "UP",
  "uptime": 120,
  "database": { "status": "CONNECTED", "readyState": 1 }
}
```

---

## 5. Rollback Procedure
If a new release causes regression:
```bash
# Rollback to the previous task definition revision (e.g. revision 1)
aws ecs update-service \
    --cluster roxstar-cluster \
    --service roxstar-backend-service \
    --task-definition roxstar-backend-task:1 \
    --force-new-deployment
```
AWS ECS executes a rolling deployment, launching the previous healthy containers before draining the regressed ones.
