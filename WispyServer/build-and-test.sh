#!/bin/bash

echo "🚀 WispyServer 빌드 및 테스트 스크립트"

# 1. 현재 컨테이너 정리
echo "📦 기존 컨테이너 정리 중..."
docker-compose down -v

# 2. 이미지 빌드
echo "🔨 Docker 이미지 빌드 중..."
docker-compose build --no-cache

# 3. 컨테이너 시작
echo "🏃 컨테이너 시작 중..."
docker-compose up -d

# 4. 애플리케이션 시작 대기
echo "⏳ 애플리케이션 시작 대기 중..."
sleep 30

# 6. 컨테이너 상태 확인
echo "📊 컨테이너 상태:"
docker-compose ps

echo "✅ 애플리케이션이 시작되었습니다!"
echo "🌐 API 엔드포인트: http://localhost:8080"