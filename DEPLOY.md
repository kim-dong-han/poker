# DEPLOY — AWS Lightsail 배포 가이드

대상: AWS Lightsail 2GB / 1 vCPU 인스턴스. 프론트+백을 **한 jar**로 묶어 8080에서 서빙한다.

> ⚠️ 아래 단계는 **AWS 계정·SSH 키가 필요**하다. 자격증명이 필요한 작업이라 저장소 코드로는
> 준비물(Dockerfile·prod 프로파일·튜닝)만 마련해 두고, 실제 실행은 이 문서를 따라 직접 한다.

---

## 준비물 (이미 저장소에 포함됨)

- `Dockerfile` — 멀티스테이지(프론트→bootJar→JRE), 비루트, 저사양 GC
- `application-prod.properties` — 포트 8080, 이퀴티 반복 600으로 하향, 통계 파일 `/var/lib/homepoker/stats.json`, graceful shutdown
- 튜닝: 라이브 이퀴티 몬테카를로 반복이 `poker.equity.live-iterations`로 외부화됨(1 vCPU 부하 조절)

---

## 방법 A) Docker (권장)

### 1. 인스턴스 준비
- Lightsail에서 Ubuntu 22.04, 2GB 플랜 인스턴스 생성
- 방화벽(네트워킹)에서 TCP **80/443**(리버스 프록시용) 또는 **8080**(직접 노출 시) 개방
- Docker 설치: `curl -fsSL https://get.docker.com | sh`

### 2. 빌드 & 실행
```bash
# 로컬 또는 인스턴스에서
git clone <repo> && cd poker
docker build -t homepoker:latest .

# 통계 영속 볼륨을 붙여 실행
docker run -d --name homepoker \
  -p 8080:8080 \
  -v /var/lib/homepoker:/var/lib/homepoker \
  --restart unless-stopped \
  homepoker:latest
```

### 3. 확인
```bash
curl -s localhost:8080/api/tables      # []  (아직 테이블 없음)
docker logs -f homepoker
```

---

## 방법 B) Docker 없이 (bare jar + systemd)

### 1. 빌드 (로컬)
```bash
cd frontend && npm ci && npm run build && cd ..
./gradlew bootJar          # build/libs/poker-0.0.1-SNAPSHOT.jar
scp build/libs/poker-0.0.1-SNAPSHOT.jar ubuntu@<ip>:/opt/homepoker/app.jar
```

### 2. 인스턴스 세팅
```bash
sudo apt update && sudo apt install -y openjdk-21-jre-headless
sudo useradd -r -s /usr/sbin/nologin homepoker
sudo mkdir -p /var/lib/homepoker && sudo chown homepoker /var/lib/homepoker
```

### 3. systemd 유닛 `/etc/systemd/system/homepoker.service`
```ini
[Unit]
Description=Home Poker Server
After=network.target

[Service]
User=homepoker
WorkingDirectory=/opt/homepoker
Environment=SPRING_PROFILES_ACTIVE=prod
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=50 -XX:+UseSerialGC -jar /opt/homepoker/app.jar
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl daemon-reload && sudo systemctl enable --now homepoker
sudo journalctl -u homepoker -f
```

---

## 리버스 프록시(HTTPS) — 선택

WebSocket을 쓰므로 Nginx에서 Upgrade 헤더를 넘겨야 한다.
```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}
```
Let's Encrypt(certbot)로 인증서를 발급한다.

---

## 튜닝 메모 (저사양)

- 부하가 크면 `poker.equity.live-iterations`를 더 낮춘다(예: 300). 이퀴티 정확도↔CPU 트레이드오프.
- 통계는 파일 스냅샷이라 별도 DB 불필요. 볼륨(`/var/lib/homepoker`)만 유지하면 재시작·재배포에도 리더보드가 보존된다.
- 메모리 압박 시 `-XX:MaxRAMPercentage`를 40으로.
