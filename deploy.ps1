# 1. Local Package
Write-Host "📦 Packaging JAR file..." -ForegroundColor Cyan
mvn clean package -DskipTests

# 2. Build and Push to Docker Hub (ARM64 for OCI)
# Added --no-cache to ensure your JwtUtils.java '!' fix is actually compiled
Write-Host "🐳 Building and Pushing Docker Image..." -ForegroundColor Green
docker buildx build --no-cache --platform linux/arm64 -t sheerwishful/recon-app:latest --push .

# 3. Execute remote commands on OCI via SSH
Write-Host "🚀 Deploying to OCI..." -ForegroundColor Yellow

# Use a standard string with semicolons to avoid Windows \r\n line ending issues
$commands = "sudo docker stop recon-app || true; " +
            "sudo docker rm recon-app || true; " +
            "sudo docker rmi sheerwishful/recon-app:latest || true; " +
            "sudo docker pull sheerwishful/recon-app:latest; " +
            "sudo docker run -d --name recon-app -p 8081:8081 --restart unless-stopped sheerwishful/recon-app:latest; " +
            "echo '✅ Deployment complete. Streaming logs...'; " +
            "sleep 3; " +
            "sudo docker logs -f recon-app --tail 20"

# Execute SSH with the key and the single-line command string
ssh -i "C:\Users\user\Downloads\ssh-key-2026-03-08 (2).key" ubuntu@146.235.233.191 $commands