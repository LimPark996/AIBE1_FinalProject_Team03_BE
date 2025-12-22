# 🎫 Ticketmon - 콘서트 티켓 예매 시스템

> 실시간 좌석 선점과 대기열 관리를 지원하는 콘서트 티켓 예매 백엔드 서비스

**테스트 ID: test01**
**테스트 PW: test011234!@#$**

![콘서트티켓예매시스템](https://github.com/user-attachments/assets/a7be3b7c-896d-48f2-9c05-bec7fa2a3f75)

📺 [발표 영상](https://www.youtube.com/watch?v=FLgYcKAJM0o) | 📑 [Figma 발표자료](https://www.figma.com/slides/4RZvK5E6tnWw2bQsHqx6H9/Untitled?node-id=1-19&t=fKAZP0N9cTwcIzEB-1)

---

## 📌 프로젝트 소개

다수의 사용자가 동시에 접속하는 티켓팅 환경에서 **좌석 선점 충돌 방지**, **결제 상태 관리**, **대기열 처리**를 안정적으로 수행하는 백엔드 시스템입니다.

### 핵심 기능

- **핵심 예매 프로세스**: 로그인 → 대기열 → 좌석 선택 → 결제
- **AI 리뷰 요약**: MD5 체크섬으로 변화 감지 후 자동 요약 생성
- **판매자 권한 관리**: 서류 검토 기반 승인/반려 시스템

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Spring Boot 3, Java 17, Spring Security, JPA |
| Database | MySQL (AWS RDS) |
| Cache | Redis (콘서트 정보 캐싱) |
| AI | Together AI (Llama 3.3 70B) → OpenAI |
| Infra | AWS EC2, Docker, GitHub Actions CI/CD |
| Auth | JWT, OAuth2 (Kakao, Google) |
| Storage | Supabase → AWS S3 |

---

## ✨ 주요 기능

### 🎵 콘서트 관리
- 콘서트 CRUD 및 검색/필터링
- 공연 완료 자동 처리 스케줄러
- Redis 캐시로 조회 성능 최적화

### 🤖 AI 리뷰 자동 요약
- MD5 체크섬으로 리뷰 변화 감지
- Together AI / OpenAI 연동
- 토큰 계산 기반 리뷰 수 제한

### 🏪 판매자 관리 시스템
- 판매자 신청 → 서류 업로드 → 관리자 검토 → 승인/반려
- 권한 부여 및 콘서트 등록 기능

### 🎯 실시간 좌석 예매
- Redis 분산락으로 동시 선점 충돌 방지
- WebSocket으로 실시간 대기 순번 알림
- Toss Payments 결제 연동

---

## 👤 담당 역할 (박유미)

### 콘서트 관리 시스템
- 콘서트 CRUD API 설계 및 구현
- 검색/정렬/날짜 필터링 기능
- Redis 캐시 서비스 구현으로 조회 성능 최적화
- 공연 완료 자동 처리 스케줄러 구현

### AI 리뷰 요약 시스템
- Together AI (Llama 3.3 70B) 연동 및 배치 처리 시스템 구현
- MD5 체크섬 기반 리뷰 변화 감지 로직 개발
- 토큰 계산 및 리뷰 수 제한 기능으로 API 비용 최적화
- OpenAI 모델로 마이그레이션 진행

### 유지보수 (프로젝트 종료 후)
- 예매/좌석 파트 버그 수정 및 리팩토링
- 결제 취소 시 좌석 해제 이슈 해결
- 좌석 배치도 Layout 동적 변경 기능 추가

---

## 🔧 주요 기술적 챌린지

### 1. AI 리뷰 요약 비용 최적화
**문제**: 매 요청마다 AI API 호출 시 비용 급증  
**해결**: MD5 체크섬으로 리뷰 변화 감지, 변경 시에만 요약 재생성

### 2. 토큰 제한 내 최대 리뷰 처리
**문제**: LLM 토큰 제한으로 모든 리뷰를 한 번에 처리 불가  
**해결**: 토큰 계산 로직으로 제한 내 최대 리뷰 수 동적 산정

### 3. 콘서트 조회 성능 개선
**문제**: 매 요청마다 DB 조회로 응답 지연  
**해결**: Redis 캐시 도입, TTL 기반 자동 갱신

---

## 📁 프로젝트 구조

```
src/main/java/com/team03/ticketmon/
├── _global/              # 공통 설정, 예외 처리
├── admin/                # 관리자 기능
├── auth/                 # 인증/인가 (JWT, OAuth2)
├── batch/                # 배치 처리
├── booking/              # 예매 처리
├── concert/              # 콘서트 관리 ⭐
├── notification/         # 알림 서비스
├── payment/              # 결제 연동
├── queue/                # 대기열 시스템
├── seat/                 # 좌석 선점/상태 관리
├── seller_application/   # 판매자 신청 관리 ⭐
├── user/                 # 사용자 관리
├── venue/                # 공연장 관리 ⭐
└── websocket/            # 웹소켓 통신
```

---

## 🚀 실행 방법

```bash
# 레포지토리 클론
git clone https://github.com/LimPark996/Ticketing-Website_BE.git

# Docker 컨테이너 실행 (Redis)
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

**필수 환경 변수**: `.env.example` 참고

---

## 👥 팀 구성

| 이름 | GitHub | 역할 |
|------|--------|------|
| 박유미 | [@LimPark996](https://github.com/LimPark996) | AI 리뷰 요약, 콘서트/판매자 페이지 파트 |
| 서희수 | [@hsu-git](https://github.com/hsu-git) | 인프라(운영/개발 환경), 관리자 페이지 파트 |
| 손주영 | [@Juyoung8563](https://github.com/Juyoung8563) | 좌석 및 예매 파트 |
| 유승남 | [@usn757](https://github.com/usn757) | 좌석, 대기열, 예매 파트 |
| 이원규 | [@bitamin707](https://github.com/bitamin707) | 회원가입/로그인, 티켓 관리 파트 |
| 이의선 | [@uiseon98](https://github.com/uiseon98) | 예매 및 결제 전체 파트 |

---

## 📎 관련 링크

- [Frontend Repository](https://github.com/LimPark996/Ticketing-Website_FE)
