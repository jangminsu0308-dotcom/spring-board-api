# 게시글 관리 REST API

Spring Boot 기반 게시판 API 서버. 개발 환경 구성부터 리눅스 서버 배포까지 직접 구축.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1, Spring Data JPA |
| Database | MySQL 8.4 |
| Build | Maven |
| Server | Ubuntu 26.04, Nginx, systemd |

## 시스템 구성

```
[개발] WSL2 Ubuntu
   Maven 빌드 → JAR
        ↓ scp
[운영] VirtualBox Ubuntu (192.168.1.72)
   ├ systemd  : 자동 시작 / 장애 시 재시작
   ├ Spring   : 8080 (localhost 전용)
   ├ MySQL    : 3306
   └ Nginx    : 80 → 외부 노출 (리버스 프록시)
```

애플리케이션 포트를 외부에 노출하지 않고 Nginx만 방화벽에서 허용하는 구조.

## API 명세

| Method | Endpoint | 설명 | 인증 | 성공 응답 |
|---|---|---|---|---|
| POST | `/api/auth/register` | 회원가입 | - | 201 |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | - | 200 |
| POST | `/api/posts` | 게시글 생성 | 필요 | 201 + Location |
| GET | `/api/posts` | 목록 조회 | - | 200 |
| GET | `/api/posts/{id}` | 단건 조회 | - | 200 |
| PUT | `/api/posts/{id}` | 수정 (본인 글만) | 필요 | 200 |
| DELETE | `/api/posts/{id}` | 삭제 (본인 글만) | 필요 | 204 |
| POST | `/api/posts/{postId}/comments` | 댓글 작성 | 필요 | 201 + Location |
| GET | `/api/posts/{postId}/comments` | 게시글의 댓글 목록 조회 | - | 200 |
| PUT | `/api/comments/{commentId}` | 댓글 수정 (본인 댓글만) | 필요 | 200 |
| DELETE | `/api/comments/{commentId}` | 댓글 삭제 (본인 댓글만) | 필요 | 204 |

인증이 필요한 요청은 `Authorization: Bearer {token}` 헤더에 로그인으로 발급받은 JWT를 담아 보냅니다.

## API 문서

Swagger UI로 API 명세 확인 및 직접 테스트 가능

```
http://192.168.1.72/swagger-ui.html
```

### 에러 응답

모든 예외를 `@RestControllerAdvice`에서 처리해 형식을 통일했습니다.

```json
{
  "timestamp": "2026-09-09T16:52:51",
  "status": 404,
  "error": "Not Found",
  "message": "게시글을 찾을 수 없습니다: 999"
}
```

| 상황 | 상태코드 |
|---|---|
| 검증 실패 (빈 제목 등) | 400 |
| 인증 필요 (토큰 없음/무효) | 401 |
| 본인 소유가 아닌 리소스 수정/삭제 | 403 |
| 존재하지 않는 리소스 | 404 |
| 아이디 중복 | 409 |
| 예상치 못한 오류 | 500 |

## 인증 방식

JWT 기반 무상태(stateless) 인증입니다.

1. `/api/auth/register`로 회원가입 (비밀번호는 BCrypt로 암호화해 저장)
2. `/api/auth/login`으로 로그인하면 JWT 발급 (기본 만료 1시간)
3. 이후 요청은 `Authorization: Bearer {token}` 헤더로 인증
4. 게시글/댓글 작성자는 로그인한 사용자로 서버에서 자동 지정 (요청 본문으로 조작 불가)
5. 수정/삭제는 작성자 본인만 가능 — 아니면 403

세션을 서버에 저장하지 않아 서버를 여러 대로 늘려도 문제없는 구조입니다.

## 패키지 구조

```
com.example.demo
├── common          # 공통 (에러 응답, 전역 예외 처리)
├── auth            # 회원/인증 도메인
│   ├── User                        # Entity
│   ├── UserRepository              # Repository
│   ├── AuthDto                     # Request / Response
│   ├── AuthService                 # 회원가입 / 로그인
│   ├── AuthController              # HTTP 처리
│   ├── DuplicateUsernameException
│   └── InvalidCredentialsException
├── security        # JWT 인증/인가
│   ├── JwtTokenProvider            # 토큰 발급 / 검증
│   ├── JwtAuthenticationFilter     # 요청마다 토큰 검사 후 SecurityContext 설정
│   ├── JwtAuthenticationEntryPoint # 인증 실패(401) 응답
│   └── SecurityConfig              # 엔드포인트별 인증 요구 여부
└── post            # 게시글 / 댓글 도메인
    ├── Post                    # Entity (author: User)
    ├── PostRepository          # Repository
    ├── PostDto                 # Request / Response
    ├── PostService             # 비즈니스 로직 (소유권 검사 포함)
    ├── PostController          # HTTP 처리
    ├── PostNotFoundException
    ├── Comment                 # Entity (Post와 @ManyToOne, author: User)
    ├── CommentRepository       # Repository
    ├── CommentDto              # Request / Response
    ├── CommentService          # 비즈니스 로직 (소유권 검사 포함)
    ├── CommentController       # HTTP 처리
    └── CommentNotFoundException
```

계층별이 아닌 **도메인 단위** 구조로 관련 코드를 한곳에 모았습니다.

## 테스트

```bash
./mvnw test
```

| 종류 | 대상 | 방식 |
|---|---|---|
| Service 단위 테스트 | `PostServiceTest`, `CommentServiceTest`, `AuthServiceTest` | Mockito로 Repository를 목킹, DB 없이 비즈니스 로직만 검증 |
| Controller 슬라이스 테스트 | `PostControllerTest`, `CommentControllerTest`, `AuthControllerTest` | `@WebMvcTest` + MockMvc, Service를 목킹해 요청/응답·검증·예외 처리(401/403/404/400)를 검증 |

정상 케이스뿐 아니라 존재하지 않는 리소스 조회·수정·삭제 시 예외가 올바르게 던져지는지, 잘못된 입력이 400으로 막히는지, 인증 없는 요청이 401로 막히는지, 본인 소유가 아닌 글/댓글 수정·삭제가 403으로 막히는지까지 함께 검증합니다.

## 실행 방법

```bash
git clone https://github.com/아이디/저장소명.git
cd 저장소명

cp src/main/resources/application-example.properties \
   src/main/resources/application.properties
# DB 계정 정보, jwt.secret(무작위 문자열)을 입력

./mvnw spring-boot:run
```

DB는 미리 생성해야 합니다.

```sql
CREATE DATABASE springdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'springuser'@'localhost' IDENTIFIED BY '비밀번호';
GRANT ALL PRIVILEGES ON springdb.* TO 'springuser'@'localhost';
```

## 설계 시 고려한 점

**Entity에 Setter를 두지 않음**
값을 아무 곳에서나 바꿀 수 있으면 변경 추적이 불가능해집니다. `update()` 메서드로 변경 통로를 하나로 제한했습니다.

**Entity와 DTO 분리**
Entity를 그대로 응답하면 내부 구조 변경이 API 스펙 변경으로 이어지고, 노출하면 안 되는 필드까지 나갑니다.

**트랜잭션 기본값을 읽기 전용으로**
클래스에 `@Transactional(readOnly = true)`를 걸고 쓰기 메서드에만 개별 적용했습니다. 실수로 쓰기가 일어나는 것을 막고, 하이버네이트가 스냅샷을 만들지 않아 성능에도 유리합니다.

**애플리케이션을 root로 실행하지 않음**
전용 시스템 계정(`demoapp`)을 만들어 systemd에서 지정했습니다.

**댓글은 게시글에 종속**
`@OneToMany(cascade = ALL, orphanRemoval = true)`로 연관관계를 맺어, 게시글이 삭제되면 댓글도 함께 삭제되도록 했습니다. 댓글만 따로 존재할 이유가 없기 때문입니다.

**인증을 세션이 아닌 JWT로**
서버가 클라이언트 상태를 들고 있지 않는 stateless 구조라 서버를 여러 대로 늘려도 세션 동기화 문제가 없습니다. 단점은 발급된 토큰을 서버에서 즉시 무효화할 수 없다는 점인데, 만료 시간을 짧게(1시간) 잡아 완화했습니다.

**작성자를 요청 본문이 아닌 인증 정보에서 결정**
초기 버전은 댓글 작성자를 클라이언트가 보내는 문자열로 그대로 믿었습니다. 인증 도입 후에는 JWT에서 추출한 로그인 사용자로 서버가 직접 지정하도록 바꿔, 클라이언트가 임의로 "다른 사람 이름"을 보낼 수 없게 했습니다.

##
