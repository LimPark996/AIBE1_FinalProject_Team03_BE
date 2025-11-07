# AIBE1-FinalProject-Team03

### (2025.11.07) README.md 최신화

부트캠프에서 발표한 PPT 자료 공유드립니다.

https://www.figma.com/slides/4RZvK5E6tnWw2bQsHqx6H9/Untitled?node-id=1-19&t=fKAZP0N9cTwcIzEB-1




---

### (2025.06.24) README.md 최신화

이 레포지토리는 Ticketmon 프로젝트의 **백엔드 애플리케이션 코드**를 관리합니다. 기존 통합 레포지토리에서 프론트엔드 코드와 분리되어, 백엔드 개발 팀이 독립적으로 개발 및 배포를 진행할 수 있도록 구성되었습니다.

---

## 🚀 1. 개발 환경 설정 가이드

### 1.1. 필수 설치 도구

- **Java 17**: 백엔드 애플리케이션 실행을 위한 Java Development Kit (JDK)
- **Gradle**: `gradlew` 스크립트가 포함되어 있어 별도 설치 불필요 (첫 빌드 시 자동 다운로드)
- **IntelliJ IDEA Ultimate**: 주요 개발 IDE
- **Git**: 최신 버전
- **Docker Desktop**: Redis, LocalStack, 그리고 로컬 Nginx 컨테이너 실행을 위해 필요합니다.

### 1.2. 프로젝트 시작하기

1. **레포지토리 클론:**

    ```bash
    git clone [<https://github.com/AIBE-3Team/AIBE1-FinalProject-Team03.git>](<https://github.com/AIBE-3Team/AIBE1-FinalProject-Team03.git>)
    cd AIBE1-FinalProject-Team03
    
    ```

2. **의존성 빌드:**

    ```bash
    ./gradlew build
    
    ```

   (프로젝트 의존성을 다운로드하고 빌드합니다.)

3. **환경 변수 설정:**
- 프로젝트 루트 디렉토리 (`build.gradle` 파일이 있는 곳)에 `.env` 파일을 생성합니다.
- `.env` 파일은 `.gitignore`에 의해 Git 추적에서 제외되므로 **절대 커밋하지 않습니다.**
- `application-dev.yml`에서 참조하는 다음 환경 변수들의 실제 값을 팀 내부 채널(예: Discord)에서 공유받아 `.env` 파일에 작성합니다.

  ```
  # .env 파일 내용 (예시 - 실제 값으로 대체 필요)
  # SPRING_PROFILES_ACTIVE 기본값은 'dev'이므로, 로컬 개발 시에는 이 값을 변경하지 않습니다.
  SPRING_PROFILES_ACTIVE=dev
  
  # 데이터베이스 (Aiven MySQL)
  DB_URL=jdbc:mysql://[your-db-host]:[your-db-port]/defaultdb
  DB_USERNAME=[your-db-username]
  DB_PASSWORD=[your-db-password]
  
  # Redis
  SPRING_DATA_REDIS_HOST=localhost
  SPRING_DATA_REDIS_PORT=6379
  SPRING_DATA_REDIS_USERNAME=default
  SPRING_DATA_REDIS_PASSWORD=
  
  # Supabase (현재 로컬 개발 환경에서 사용 중)
  SUPABASE_URL=https://[your-supabase-url].supabase.co
  SUPABASE_KEY=[your-supabase-service-role-key] # ⚠️ 절대 클라이언트에 노출 금지!
  SUPABASE_PROFILE_BUCKET=ticketmon-dev-profile-imgs
  SUPABASE_POSTER_BUCKET=ticketmon-dev-poster-imgs
  SUPABASE_DOCS_BUCKET=ticketmon-dev-seller-docs
  
  # JWT
  JWT_SECRET_KEY=your-very-long-and-secure-jwt-secret-key-from-openssl-rand-base64-32
  JWT_ACCESS_EXPIRATION_MS=600000  # 10분
  JWT_REFRESH_EXPIRATION_MS=86400000 # 24시간
  
  # AWS (LocalStack 모킹용 - 실제 AWS 아님)
  AWS_ACCESS_KEY=test-key
  AWS_SECRET_KEY=test-secret
  SQS_ENDPOINT=http://localstack:4566 # LocalStack SQS 엔드포인트
  
  # 앱 기본 URL (토스페이먼츠 콜백 URL 등)
  BASE_URL=http://localhost:8080 # 로컬 백엔드 앱의 주소
  
  # 토스페이먼츠 (개발용 키)
  TOSS_CLIENT_KEY=test_ck_your-toss-client-key
  TOSS_SECRET_KEY=test_sk_your-toss-secret-key
  
  ```

4. **Docker 컨테이너 실행 (Redis, LocalStack, Nginx):**
- 프로젝트 루트 디렉토리에서 다음 명령어를 실행합니다.

  ```bash
  docker-compose up --build
  
  ```

- `redis-cache`, `localstack`, `nginx-frontend-server` 컨테이너가 실행됩니다.
- **`nginx-frontend-server` 역할:** `http://localhost`를 통해 **프론트엔드 레포지토리의 `dist` 폴더** 내용을 서빙하며, `/api` 및 `/ws` 요청을 백엔드 앱(`http://host.docker.internal:8080/`)으로 프록시합니다.
- **참고:** 이 단계에서는 프론트엔드 레포지토리도 함께 `npm run dev`로 실행되어야 완전한 로컬 환경 연동 테스트가 가능합니다.
5. **백엔드 Spring Boot 앱 실행 (IntelliJ IDEA에서):**
- IntelliJ IDEA에서 `TicketmonGoApplication.java`를 실행합니다. (`http://localhost:8080`에서 시작)
- 콘솔 로그에 오류 없이 시작되는지 확인합니다.

### 1.3. 로컬 환경 테스트 방법 ✅

모든 앱(백엔드, 프론트엔드 개발 서버)과 Docker 컨테이너가 실행 중인 상태에서:

1. **백엔드 API 직접 테스트 (Swagger UI):**
- 웹 브라우저로 `http://localhost:8080/swagger-ui/index.html` 에 접속하여 백엔드 API 문서가 정상 로드되는지 확인합니다.
- `/api/auth/login` (POST) API 등을 통해 백엔드 API가 정상 작동하는지 테스트합니다.
- **💡 참고:** 현재 `SecurityConfig.java`에 `anyRequest().permitAll()`이 임시 활성화되어 있으므로, 인증 없이 모든 API 테스트가 가능합니다. 로그인 관련 기능의 JWT 쿠키 발행 문제는 백엔드 로그인 담당자가 추후 해결할 예정입니다.
2. **Docker 컨테이너 상태 확인 명령어:**
- **Redis 상태 확인:**

    ```bash
    docker exec -it redis-cache redis-cli ping
    # → PONG 반환되면 정상 작동
    
    ```

- **LocalStack SQS 상태 확인:**

    ```bash
    docker exec -it localstack awslocal sqs list-queues
    # → 큐 없어도 오류 없이 반환되면 정상 (예시: "<http://localhost:4566>" 응답 확인)
    
    ```


---

## 🛠️ 2. 기술 스택 및 주요 설정

### 2.1. 핵심 기술 스택

- **백엔드 프레임워크:** Spring Boot (`v3.x`)
- **언어:** Java 17
- **ORM:** Spring Data JPA (Hibernate)
- **데이터베이스:** MySQL (Aiven 사용)
- **캐시/분산락/PubSub:** Redis (Redisson 사용)
- **클라우드 모킹:** LocalStack (AWS SQS, S3 개발용 모킹)
- **파일 스토리지:** Supabase (현재) -> AWS S3 (추후 마이그레이션 예정)
- **결제:** Toss Payments 연동
- **API 문서:** Swagger UI
- **인증/보안:** Spring Security (JWT, OAuth2)
- **로깅:** SLF4J + Logback

### 2.2. Spring Security 설정 요약 (최신화)

| 구분 | 설명 |
| --- | --- |
| **현재 인증 상태** | **전체 인증 없이 API 테스트 가능** 상태입니다. (`.anyRequest().permitAll()` 활성화) |
| **CORS 설정** | `SecurityConfig.java`의 `corsConfigurationSource`에서 모든 CORS 정책을 통합 관리하며, 프론트엔드 개발 서버 도메인(`http://localhost:5173`, `http://localhost:5174`, `http://localhost:8080`) 및 `ngrok` 주소를 허용합니다. `credentials: true`를 허용합니다. (기존 `WebConfig.java` 삭제) |
| **인가 설정** | `SecurityConfig.java`에서 URL별 접근 권한이 설정되어 있습니다. <br> - `/api/auth/login` (POST) 및 `/api/queue/enter` (POST)는 `permitAll()`로 최상단에 명시적으로 허용됩니다. <br> - `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/test/upload/**`, `/profile/image/**`도 `permitAll()` 입니다. <br> - `/api/users/me/seller-status`, `/api/users/me/seller-requests`, `/api/users/me/role`은 `authenticated()` (로그인된 사용자). <br> - `/api/seller/concerts/**`, `/api/seller/count`는 `hasRole("SELLER")` (판매자 권한). <br> - `/admin/**`은 `hasRole("ADMIN")` (관리자 권한, 주석 처리됨). <br> - 나머지 모든 요청은 현재 `anyRequest().permitAll()`로 임시 허용 중입니다. |
| **JWT 필터** | `LoginFilter`, `JwtAuthenticationFilter`, `CustomLogoutFilter`가 `SecurityFilterChain`에 추가되어 있습니다. `LoginFilter`는 `/api/auth/login` (POST) 요청을 처리하며, `JwtAuthenticationFilter`는 해당 로그인 요청을 건너뛰도록 처리되어 있습니다. |
| **WebSocket** | `WebSocketConfig.java`에서 `/ws/waitqueue` 엔드포인트를 허용하며, `setAllowedOrigins`에 프론트엔드 개발 서버 도메인을 포함합니다. `WebSocketAuthInterceptor`를 통해 WebSocket 핸드셰이크 시 JWT 토큰을 검증합니다. |
| **예외 응답 처리** | 인증 실패(401), 권한 없음(403)에 대해 커스텀 에러 메시지를 반환하도록 설정됨. |
| **주의 사항** | **현재 `anyRequest().permitAll()` 활성화로 인해 로그인 및 JWT 쿠키 발행 관련 문제가 우회된 상태입니다.** 이 문제는 로그인 담당 팀원과 상의하여 해결할 예정입니다. 이 PR은 프론트엔드 개발 블로킹 해소를 위한 인프라 설정에 초점을 맞춥니다. |

### 2.3. 파일 업로드 시스템 구조 (Supabase -> S3 마이그레이션 대비)

- `StorageUploader` 인터페이스를 통해 Supabase와 S3 업로더를 추상화했습니다.
- **현재는 `SupabaseUploader`가 `application.yml`에 `supabase` 프로필이 포함되어 활성화되어 있습니다.**
- `S3Uploader`는 `s3` 프로필이 활성화될 때 사용되도록 준비되어 있습니다.

### 2.4. 공통 예외 처리 정책 (`Global Exception Handling`)

- `GlobalExceptionHandler`를 통해 애플리케이션 전역에서 발생하는 예외를 일관된 `ErrorResponse` 형식으로 처리합니다.
- `ErrorCode` enum을 통해 모든 비즈니스 및 시스템 예외 코드를 통합 관리합니다.
- **참고:** 대기열 진입 API (`/api/queue/enter`)는 현재 클라이언트(`queue/index.html`)의 요청 방식과 컨트롤러 파라미터 방식 불일치로 인해 `400 Bad Request` 오류가 발생하고 있습니다. 이 부분은 대기열 담당 팀원이 `WaitingQueueController.java`에서 `@RequestParam` 대신 `@RequestBody` DTO를 사용하도록 수정해야 합니다.

---

## 📚 3. 주요 파일 설명 (패키지 기준)

| 파일명 | 설명 |
| --- | --- |
| `.env` | 실제 민감 설정 키 저장 (Git 제외) |
| `docker-compose.yml` | 로컬 개발용 컨테이너 (Redis, LocalStack, Nginx) 구성 |
| `nginx.conf` | 로컬 Nginx 컨테이너 설정 (프론트 서빙, 백엔드 프록시) |
| `SecurityConfig.java` | Spring Security 핵심 설정 (인증, 인가, CORS) |
| `WebSocketConfig.java` | WebSocket 설정 (핸들러, 인터셉터, CORS) |
| `LoginFilter.java` | 자체 로그인 처리 필터 |
| `JwtAuthenticationFilter.java` | JWT 토큰 검증 및 재발급 필터 |
| `CookieUtil.java` | JWT 쿠키 생성/삭제 유틸 |
| `GlobalExceptionHandler.java` | 전역 예외 처리 |
| `ErrorCode.java` | 에러 코드 및 메시지 정의 |
| `SuccessResponse.java` | 공통 성공 응답 형식 |
| `application.yml` | Spring 공통 설정 및 프로필 활성화 |
| `application-dev.yml` | 개발 환경 (dev 프로필) 전용 설정 |
| `application-prod.yml` | 운영 환경 (prod 프로필) 전용 설정 (AWS 마이그레이션용) |
| `TicketmonGoApplication.java` | Spring Boot 애플리케이션 진입점 |
| `SellerConcertController.java` | 판매자 콘서트 CRUD API 컨트롤러 |
| `WaitingQueueController.java` | 대기열 진입 API 컨트롤러 |
| `ConcertController.java` | 콘서트 목록/상세 조회 등 API 컨트롤러 |
| `ReviewController.java` | 후기 작성/수정/삭제 API 컨트롤러 |
| `ExpectationReviewController.java` | 기대평 작성/수정/삭제 API 컨트롤러 |
| `PaymentApiController.java` | 결제 API 컨트롤러 (Toss Payments 연동) |
| `WebhookController.java` | 토스페이먼츠 웹훅 처리 컨트롤러 |
| `TestUploadController.java` | 파일 업로드 테스트 API |
| `RedisTestController.java` | Redis 테스트 API |
| `RedisHealthController.java` | Redis 헬스체크 API |
| `HomeController.java` | 기본 `/` 경로 컨트롤러 |
| `ExampleProfileController.java` | 프로필 이미지 업로드 예시 컨트롤러 |
| `PaymentTestPageController.java` | 결제 테스트 페이지 (`/payment/checkout`) 컨트롤러 |
| `UserController.java` | 사용자 (회원가입) 컨트롤러 |
| `SeatReservationController.java` | 좌석 선점/해제 API 컨트롤러 |
| `SeatQueryController.java` | 좌석 상태 조회 API 컨트롤러 |
| `SeatAdminController.java` | 좌석 관리자 API 컨트롤러 |
| `SeatStatusService.java` | 좌석 상태 관리 서비스 (Redis) |
| `SeatCacheInitService.java` | 좌석 캐시 초기화 서비스 |
| `WaitingQueueService.java` | 대기열 서비스 (Redis Sorted Set) |
| `NotificationService.java` | 알림 서비스 (Redis Pub/Sub) |
| `WaitingQueueScheduler.java` | 대기열 스케줄러 (Redis 분산 락) |
| `CleanupScheduler.java` | 만료된 세션 정리 스케줄러 |
| `RegisterService.java` | 회원가입 서비스 인터페이스 |
| `RegisterServiceImpl.java` | 회원가입 서비스 구현체 |
| `UserEntityService.java` | 사용자 엔티티 서비스 |
| `UserEntityServiceImpl.java` | 사용자 엔티티 서비스 구현체 |
| `SocialUserService.java` | 소셜 사용자 서비스 인터페이스 |
| `SocialUserServiceImpl.java` | 소셜 사용자 서비스 구현체 |
| `RefreshTokenService.java` | 리프레시 토큰 서비스 인터페이스 |
| `RefreshTokenServiceImpl.java` | 리프레시 토큰 서비스 구현체 |
| `TestUploadService.java` | 파일 업로드 테스트 서비스 |
| `ExampleProfileImageService.java` | 프로필 이미지 업로드 예시 서비스 |
| `ConcertRepository.java` | 콘서트 레포지토리 |
| `SellerConcertRepository.java` | 판매자 콘서트 레포지토리 |
| `ReviewRepository.java` | 후기 레포지토리 |
| `ExpectationReviewRepository.java` | 기대평 레포지토리 |
| `PaymentRepository.java` | 결제 레포지토리 |
| `PaymentCancelHistoryRepository.java` | 결제 취소 이력 레포지토리 |
| `UserRepository.java` | 사용자 레포지토리 |
| `SocialUserRepository.java` | 소셜 사용자 레포지토리 |
| `ReviewDTO.java` | 후기 DTO |
| `ExpectationReviewDTO.java` | 기대평 DTO |
| `LoginDTO.java` | 로그인 요청 DTO |
| `TicketmonGoApplicationTests.java` | 메인 애플리케이션 테스트 |
| `ConcertServiceTest.java` | 콘서트 서비스 테스트 |
| `SellerConcertServiceTest.java` | 판매자 콘서트 서비스 테스트 |
| `ReviewServiceTest.java` | 후기 서비스 테스트 |
| `ExpectationReviewServiceTest.java` | 기대평 서비스 테스트 |
| `PaymentServiceTest.java` | 결제 서비스 테스트 |
| `RegisterServiceImplTest.java` | 회원가입 서비스 테스트 |
| `RedisTestController.java` | Redis 테스트 컨트롤러 |
| `RedisHealthController.java` | Redis 헬스체크 컨트롤러 |
| `WaitingQueueControllerTest.java` | 대기열 컨트롤러 테스트 |
| `AdmissionFlowIntegrationTest.java` | 대기열 흐름 통합 테스트 |
| `WaitingQueueSchedulerIntegrationTest.java` | 대기열 스케줄러 통합 테스트 |
| `CleanupSchedulerTest.java` | 정리 스케줄러 테스트 |

<br>
<br>
<br>
<br>

---

## (2025.06.15) 초기 환경 세팅 관련 공지

---
## 1. 초기 환경 설정 요약 (Security + Swagger)

**✅ 적용 대상**

- `SecurityConfig.java`
- `SwaggerConfig.java`

---

### 🔐 Spring Security 설정 요약

| 구분 | 설명                                                                    |
| --- |-----------------------------------------------------------------------|
| **현재 인증 상태** | **전체 인증 없이 테스트 가능** 상태입니다.→ `anyRequest().permitAll()`                |
| **JWT 필터** | 자리만 확보되어 있으며, **실제 필터는 구현되지 않았습니다.**<br>→ 로그인/회원가입/JWT 담당자가 추후 구현 예정  |
| **권한 설정** | `/admin/**` 등 권한별 URL은 **아직 작동하지 않으며**, 주석으로 표시되어 있습니다.               |
| **인증 예외 응답 처리** | 인증 실패(401), 권한 없음(403)에 대해 커스텀 에러 메시지를 반환하도록 설정됨.                     |
| **로그아웃 설정** | `/logout` 호출 시 세션 무효화 + JWT 쿠키 삭제 설정만 되어있습니다.<br> → JWT 무효화는 실제 구현 필요 |
| **CORS 설정** | 프론트 요청 허용(`localhost:3000`, `localhost:8080`) 및 쿠키/헤더 허용 구성 완료        |

🔧 *📌 현재 인증 설정은 모두 테스트 편의를 위한 임시 상태입니다. 실제 로그인 기능 구현 시 반드시 수정이 필요합니다.*

<br>

---

### 📘 Swagger 설정 요약

| 구분 | 설명 |
| --- | --- |
| **접근 경로** | `/swagger-ui/index.html` 에서 UI 확인 가능 |
| **보안 설정** | JWT 사용을 전제로 `Authorize` 버튼이 설정되어 있음 (`bearer` 방식) |
| **실제 인증 작동 여부** | ❌ 현재는 JWT 미연동 상태이므로 `Authorize` 버튼은 **기능하지 않습니다.** |
| **구성 목적** | 이후 JWT 기능 구현 시 Swagger에서도 로그인 후 API 테스트 가능하게 하기 위함입니다. |

<br>

---

### 📌 팀원별 작업 참고 사항

| 역할 | 확인해야 할 부분 |
| --- | --- |
| ✅ **로그인/회원가입/JWT 담당자** | - `JwtAuthenticationFilter` 구현 후 SecurityConfig의 주석 해제 <br>- Swagger와 JWT 연동 적용 필요 |
| ✅ **API 개발 담당자** | - 현재는 인증 없이 API 호출 가능하지만, **향후 인증 필요**를 고려한 설계 권장 |
| ✅ **인프라 담당자** | - SecurityConfig, SwaggerConfig 위치와 현재 상태 숙지 <br>- 추후 인증 정책 변경 시 전체 테스트 환경 영향 고려 |
| 🧪 **테스트 시 참고** | - 현재 Swagger에서는 인증 없이 모든 API 호출 가능 <br>- 로그인 후 토큰이 생기면, Swagger Authorize 버튼을 통해 인증 테스트 가능 예정 |

<br>

---

### ✍️ 현재 SecurityConfig 주요 코드 상태 (요약)

```java
// 현재 모든 API 인증 없이 허용
.anyRequest().permitAll()

// 향후 인증 설정 전환용 주석
// .anyRequest().authenticated()

// JWT 필터 자리만 확보 (구현 전)
// http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

<br>
<br>
<br>

---
## 2. 📛 공통 예외 처리 정책 (Global Exception Handling)
### ✅ 목적
- 예외 응답을 일관된 형식으로 내려주기 위해 설정합니다.
- 에러 코드와 메시지를 한 곳에서 통합 관리함으로써 유지보수를 쉽게 합니다.
- 개발자/사용자 모두에게 명확한 에러 정보를 제공합니다.

---
### 🧩 기본 응답 형식
**❗ 실패(Error) 응답 (`ErrorResponse`)**
```json
{
  "success": false,
  "status": 400,
  "code": "C001",
  "message": "유효하지 않은 입력값입니다."
}
```

**✅ 성공(Success) 응답 (`SuccessResponse`)**
```json
{
  "success": true,
  "message": "조회 성공",
  "data": {
    "title": "콘서트 A",
    "date": "2025-12-01"
  }
}
```

<br>

---
### ⚙️ 전체 구성도
```text
[Controller] → 예외 발생
↓
[BusinessException] 또는 기타 Exception
↓
[GlobalExceptionHandler]에서 처리
↓
ErrorCode에 따라 ErrorResponse 생성
↓
클라이언트에 통일된 에러 응답 반환
```

<br>

---

### 🧱 주요 구성 요소 설명
|파일명|	역할|
|-|-|
|`ErrorCode.java`|	❗ 전체 에러 코드/메시지 관리 (enum으로 구분)|
|`BusinessException.java`|	직접 정의한 예외(비즈니스 로직 위반)를 처리하는 커스텀 예외 클래스|
|`ErrorResponse.java`|	실패 응답 포맷 클래스 (`success: false`)|
|`SuccessResponse.java`|	성공 응답 포맷 클래스 (`success: true`)|
|`GlobalExceptionHandler.java`|	전역 예외 핸들러 (`@RestControllerAdvice`)|

<br>

---

### ✏️ 사용 예시
**1. ❗ 비즈니스 예외 발생**
```java
   if (seat.isAlreadyBooked()) {
        throw new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN);
   }
```
→ 클라이언트에 아래처럼 반환됨:

```json
{
  "success": false,
  "status": 409,
  "code": "B003",
  "message": "이미 선택된 좌석입니다."
}
```
<br>

**2. ✅ 성공 응답 반환**
```java
   return ResponseEntity.ok(SuccessResponse.of("등록 성공", concertDto));
```
→ 클라이언트에 아래처럼 반환됨:

```json
{
  "success": true,
  "message": "등록 성공",
  "data": {
    "concertId": 12,
    "title": "Waiting Cha Live"
  }
}
```

<br>

---

### 💡 팀원 참고사항
| 항목                                                    |	설명|
|-------------------------------------------------------|-|
| **에러 코드는 어디에 정의하나요?**                                 |	`ErrorCode.java` enum에 추가하세요|
| **새로운 예외 만들려면?**                                      |	`throw new BusinessException(ErrorCode.원하는_코드);`|
| **공통 에러 메시지는 어디서 바꾸나요?**                              |	`ErrorCode.java`의 `message` 필드에서 수정|
| **예외 직접 잡고 싶을 땐?**                                    |	`@ExceptionHandler(YourException.class)` 추가 가능|
| **응답에 `success`, `message`, `data` 일관되게 나오게 하고 싶다면?** |	반드시 `SuccessResponse`와 `ErrorResponse`를 사용하세요|


<br>
<br>

---

---

## 🐳 Docker 개발 환경 구성 가이드

> 로컬 개발 환경을 통일하기 위해 Docker를 활용한 구성 가이드를 제공합니다.


<br>

---
### ✅ 구성 요소

| 서비스           | 설명 |
|---------------|------|
| `spring-app`  | 백엔드 애플리케이션 (Dockerfile 기반) |
| `redis-cache` | Redis (좌석 캐싱 및 분산락 처리용) |
|`localstack`|LocalStack (AWS 서비스 모킹: SQS, S3 등 개발용)|
| `(MySQL)`     | DB는 개발 초기에는 Aiven 사용, 추후 AWS Aurora로 마이그레이션 예정 |

<br>

---

### 📁 전제 조건

1. Docker Desktop 설치 (https://www.docker.com/products/docker-desktop)
2. **`.env`, `application-dev.yml` 파일 수령 및 배치**
- `application-dev.yml`, `.env`은 직접 전달 예정
- `application-dev.yml`는 `src/main/resources/`에 `application-dev.yml`로 위치
- `.env`는 팀 디스코드 또는 팀원 공유 경로에서 .env 파일을 받아, 프로젝트 루트 디렉토리에 위치
3. `.env.example`은 참고용으로 제공됩니다.

<br>

---

### 📦 실행 방법

1. `.env` 파일 수령 및 배치

> 팀 디스코드 또는 팀원 공유 경로에서 `.env` 파일을 받아, 루트 디렉토리에 위치시킵니다.

2. Docker 이미지 빌드 및 컨테이너 실행

  ```bash
  docker-compose up --build
  ```

3. 컨테이너 상태 확인
  ```bash
  docker ps
  ```

4. 컨테이너 중지
  ```bash
  docker-compose down
  ```

<br>

---

### 📂 주요 파일 설명
|파일명|	설명|
|-|-|
|Dockerfile|	Spring Boot 애플리케이션 빌드 및 실행 설정|
|docker-compose.yml|	전체 서비스(앱 + Redis + LocalStack) 컨테이너 정의|
|.dockerignore|	빌드 시 Docker에 포함하지 않을 파일 목록|
|.env.example|	환경변수 예시 파일 (민감 정보는 포함되지 않음)|
|.env|로컬 개발 환경 변수 (실제 민감 정보 포함, Git 추적 제외)|
|application.yml|Spring 공통 설정 및 프로필 활성화 기준 정의|
|application-dev.yml|개발 환경 (dev 프로필) 전용 설정|
|application-prod.yml|운영 환경 (prod 프로필) 전용 설정|

<br>

---

### 🧪 테스트 포인트
- localhost:8080에서 백엔드 서비스 확인
- localhost:6379 Redis 정상 기동 여부 확인
- LocalStack SQS: docker exec -it localstack awslocal sqs list-queues 명령으로 SQS 서비스 확인
- Swagger: http://localhost:8080/swagger-ui/index.html

<br>

---

### ⚠️ 보안 및 주의사항
- .env 및 application-dev.yml은 절대 Git에 업로드하지 마세요!
  - .gitignore에 이미 포함되어 있음
  - 팀원 간 수동 전달 방식 유지
- 환경 변수 누락 시 실행 에러가 발생할 수 있습니다.

<br>

---
## ✅ 초기 세팅 가이드 (infra 담당자용)
> 티켓팅 프로젝트의 로컬 개발 및 Docker 실행을 위한 환경 설정 가이드입니다. <br> Aiven MySQL, Redis, Supabase, LocalStack, Swagger 연동까지 한 번에 확인 가능하도록 구성했습니다.

---

### 📁 폴더 및 파일 구성 요약
|파일명	| 설명                                                             |
|-|----------------------------------------------------------------|
|`.env`| 	실제 민감 설정 키 (Aiven, Supabase, JWT 등) – ❌ Git 제외 / ✅ Discord 공유 |
|`.env.example`| 	키 없이 형식만 제공되는 예시 파일 – ✅ Git 포함                                |
|`application.yml`| 	Spring 공통 설정 (프로필 활성화 및 기본 include 포함)                                                 |
|`application-dev.yml`| 	개발 환경 (dev 프로필) 전용 설정                              |
|`application-prod.yml`|운영 환경 (prod 프로필) 전용 설정|
|`docker-compose.yml`| 	Redis, LocalStack, Spring Boot 실행용 컨테이너 구성                    |
|`Dockerfile`| 	Spring Boot 앱의 도커 빌드 설정                                       |

<br>

### ✅ 설정 방식 요약
|항목|	방식|
|-|-|
|프로필 적용|	application.yml의 ${SPRING_PROFILES_ACTIVE:dev} 변수 참조 → application-dev.yml 또는 application-prod.yml 로드. (application.yml의 include: supabase 설정도 함께 적용)|
|.env 연동 방식|	application-dev.yml에서 spring.config.import: optional:file:.env[.properties] 설정 사용|
|민감 키 관리|	.env에만 저장 / application-dev.yml 및 application-prod.yml에는 ${VAR_NAME} 변수명만 작성|
|Docker 실행|	docker-compose up 한 번으로 Redis, LocalStack, Spring Boot 앱 실행 가능|

<br>

### ✅ 체크리스트
|항목| 	설명                                                                                    | 	상태                                            |
|-|----------------------------------------------------------------------------------------|------------------------------------------------|
|🔄 docker-compose up 후 Redis/LocalStack 기동 확인| 	Redis: redis-cli ping → PONG 반환, LocalStack: awslocal sqs list-queues 오류 없이 반환        | 	[x]                                           |
|📄 .env에 실제 키값 반영| 	Discord 등 별도 채널로 공유 (실제 값으로 대체 필요)                                                    | 	[] <br> (작성 완료)                               |
|🔗 .env → application-dev.yml 연동 확인| 	Spring 로그에 Using config data from 'file:.env[.properties]' 확인                         | 	☐                                             |
|🌐 Swagger 접속 확인| 	http://localhost:8080/swagger-ui/index.html	                                          | [x]                                            |
|🪣 Supabase 버킷 생성 + 정책 설정| 	ticketmon-dev-profile-imgs, ticketmon-dev-poster-imgs, ticketmon-dev-seller-docs 등 생성 | 	[x] (설정 완료 / 2MB 제한 + MIME 필터 설정은 구현 단계에서 설정) |
|🐳 Docker로 Spring Boot 실행 후 .env 반영 확인| 	Swagger 정상 접속 + DB 연결 여부 (로그 확인) | 	[x]|

<!--
## ✅ 초기 세팅 체크리스트 (2025-06 기준)

| 항목 | 설명 | 상태 |
|------|------|------|
| 🔄 `docker-compose up` 후 Redis/LocalStack 기동 확인 | Redis `PONG` 응답 확인 / LocalStack SQS 명령어 정상 작동 | ✅ 완료 |
| 📄 `.env`에 실제 키값 반영 | `.env` 파일에는 키가 반영되어 있지만, 현재 application-dev.yml 직접 사용 중 | 🔄 작성 완료 (연동 미완료) |
| 🔗 `.env → application-dev.yml` 연동 테스트 | `spring.config.import=optional:env[.env]` 방식은 아직 미적용 | ⛔ 미완료 |
| 🌐 Swagger 접속 확인 | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) 정상 접속 확인 | ✅ 완료 |
| 🪣 Supabase 버킷 및 정책 설정 | `profile-imgs`, `poster-imgs` 등 버킷 미생성 상태 | ⛔ 미완료 |
| 🐳 Docker로 실행 시 `.env` 적용 여부 | `.env` → SpringBoot 설정이 미적용 상태라 확인 불가 | ⛔ 미완료 |
-->

<br>

### 🛠 확인 명령어 정리
**Redis 상태 확인**
```bash
docker exec -it redis-cache redis-cli ping
# → PONG 반환되면 정상 작동
```
**LocalStack SQS 상태 확인**
```bash
docker exec -it localstack awslocal sqs list-queues
# → 큐 없어도 200 OK 반환되면 정상
```

<br>

### 💬 참고 사항
- .env는 Git에 포함되지 않으며, 형식 제공용 .env.example 파일로 대체됩니다
- Supabase와 LocalStack을 개발 초기 환경으로 사용하며, S3/SQS는 추후 마이그레이션 예정입니다.
- 현재의 docker-compose.yml은 개발용 설정이며, 운영 환경에서는 별도 설정 예정입니다.

<br>
<br>

---

---

### + [추가 문서]
- 📎 [TimeUtil 설명 보기](src/main/java/com/team03/ticketmon/_global/util/TIME_UTIL_GUIDE.md)
- 📦 [파일 업로드 시스템 / 파일 업로드 구조 보기](./STORAGE_GUIDE.md)
