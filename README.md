# 게시글 관리 REST API

Spring Boot 기반 게시판 API 서버. 개발 환경 구성부터 리눅스 서버 배포까지 직접 구축.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1, Spring Data JPA |
| Database | MySQL 8.4 |
| Build | Maven |
| Server | Ubuntu 26.04, Nginx, Docker |

## 시스템 구성

```
[개발] WSL2 Ubuntu / Docker Compose (app + MySQL 컨테이너)
        ↓ git pull
[운영] VirtualBox Ubuntu (192.168.1.72)
   ├ Docker   : app 컨테이너, network_mode: host, 자동 재시작(restart: unless-stopped)
   ├ MySQL    : 3306 (네이티브 설치 — 컨테이너화하지 않고 그대로 사용)
   └ Nginx    : 80 → 외부 노출 (리버스 프록시, / → 127.0.0.1:8080)
```

애플리케이션 포트를 외부에 노출하지 않고 Nginx만 방화벽에서 허용하는 구조. 배포는
로컬에서 jar를 빌드해 scp로 옮기던 방식에서, **VM이 직접 `git pull` 후 이미지를 빌드**하는
방식으로 바꿨다. MySQL은 이미 운영 데이터가 들어있어 컨테이너로 옮기지 않고, 앱 컨테이너가
`network_mode: host`로 붙어 기존과 동일하게 `127.0.0.1:3306`에 접속한다.

## API 명세

| Method | Endpoint | 설명 | 인증 | 성공 응답 |
|---|---|---|---|---|
| POST | `/api/auth/register` | 회원가입 | - | 201 |
| POST | `/api/auth/login` | 로그인 (액세스·리프레시 토큰 발급) | - | 200 |
| POST | `/api/auth/refresh` | 액세스 토큰 재발급 | - | 200 |
| POST | `/api/auth/logout` | 리프레시 토큰 폐기 | - | 204 |
| POST | `/api/posts` | 게시글 생성 | 필요 | 201 + Location |
| GET | `/api/posts` | 목록 조회 (페이징·검색) | - | 200 |
| GET | `/api/posts/{id}` | 단건 조회 | - | 200 |
| PUT | `/api/posts/{id}` | 수정 (본인 글만) | 필요 | 200 |
| DELETE | `/api/posts/{id}` | 삭제 (본인 글만) | 필요 | 204 |
| POST | `/api/posts/{postId}/comments` | 댓글 작성 | 필요 | 201 + Location |
| GET | `/api/posts/{postId}/comments` | 게시글의 댓글 목록 조회 (페이징) | - | 200 |
| PUT | `/api/comments/{commentId}` | 댓글 수정 (본인 댓글만) | 필요 | 200 |
| DELETE | `/api/comments/{commentId}` | 댓글 삭제 (본인 댓글만) | 필요 | 204 |

인증이 필요한 요청은 `Authorization: Bearer {accessToken}` 헤더에 로그인으로 발급받은 액세스 토큰을 담아 보냅니다.

**로그인 / 재발급 응답**

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "09a4ddd3-aaf8-46a7-85aa-5071d3162e34",
  "username": "writer"
}
```

**게시글 목록 조회 파라미터**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 페이지 번호 (0부터 시작) |
| `size` | 10 | 페이지당 개수 (최대 100) |
| `keyword` | - | 제목 검색어 (대소문자 무시, 부분 일치) |

```
GET /api/posts?page=0&size=10&keyword=스프링
```

```json
{
  "content": [ { "id": 5, "title": "...", "...": "..." } ],
  "page": 0,
  "size": 10,
  "totalElements": 23,
  "totalPages": 3
}
```

**댓글 목록 조회**도 같은 형식으로 페이징됩니다 (`page`/`size`, 기본 10개, 작성순 정렬). 검색어는 없습니다.
```
GET /api/posts/{postId}/comments?page=0&size=10
```

## API 문서

Swagger UI로 API 명세 확인 및 직접 테스트 가능

```
http://192.168.1.72/swagger-ui.html
```

우측 상단 **Authorize** 버튼에 `/api/auth/login`으로 받은 `accessToken`을 넣으면, 인증이 필요한 API(자물쇠 아이콘 표시)도 Swagger UI에서 바로 테스트할 수 있습니다.

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
| 로그인 시도 초과 (브루트포스 방어) | 429 |
| 예상치 못한 오류 | 500 |

## 인증 방식

JWT 액세스 토큰 + 리프레시 토큰 조합의 무상태(stateless) 인증입니다.

1. `/api/auth/register`로 회원가입 (비밀번호는 BCrypt로 암호화해 저장)
2. `/api/auth/login`으로 로그인하면 액세스 토큰(기본 만료 1시간)과 리프레시 토큰(기본 만료 7일)을 함께 발급
3. 이후 요청은 `Authorization: Bearer {accessToken}` 헤더로 인증
4. 액세스 토큰이 만료되면 `/api/auth/refresh`에 리프레시 토큰을 보내 재로그인 없이 새 토큰 쌍을 재발급 — 프론트엔드는 401을 받으면 이 과정을 자동으로 수행한다
5. 재발급 시 기존 리프레시 토큰은 즉시 폐기(회전)되어, 탈취된 옛 토큰으로는 더 이상 재발급받을 수 없다
6. `/api/auth/logout`으로 리프레시 토큰을 직접 폐기 가능
7. 게시글/댓글 작성자는 로그인한 사용자로 서버에서 자동 지정 (요청 본문으로 조작 불가)
8. 수정/삭제는 작성자 본인만 가능 — 아니면 403
9. 같은 아이디로 5회 연속 로그인에 실패하면 5분간 잠금 (429) — 성공하면 실패 기록 초기화

액세스 토큰은 서명 검증만으로 확인하는 기존 JWT 방식 그대로 서버에 상태를 두지 않고, 리프레시 토큰만 `refresh_tokens` 테이블에 저장해 폐기·회전이 가능하도록 했습니다.

**브루트포스 방어**는 별도 저장소 없이 서버 메모리(`ConcurrentHashMap`)에 아이디별 실패 횟수를 세는 방식입니다. 구현이 간단하다는 장점이 있지만, 서버를 여러 대로 늘리면 계정당 허용 시도 횟수가 서버 대수만큼 늘어나는 한계가 있어 — 규모가 커지면 Redis 같은 공유 저장소로 옮겨야 합니다.

## 패키지 구조

```
com.example.demo
├── common          # 공통 (에러 응답, 전역 예외 처리)
├── auth            # 회원/인증 도메인
│   ├── User                        # Entity
│   ├── UserRepository              # Repository
│   ├── RefreshToken                # Entity (토큰 회전/폐기 상태 보관)
│   ├── RefreshTokenRepository      # Repository
│   ├── AuthDto                     # Request / Response
│   ├── AuthService                 # 회원가입 / 로그인 / 재발급 / 로그아웃
│   ├── AuthController              # HTTP 처리
│   ├── LoginAttemptService         # 로그인 실패 횟수 추적, 브루트포스 잠금
│   ├── DuplicateUsernameException
│   ├── InvalidCredentialsException
│   ├── InvalidRefreshTokenException
│   └── TooManyLoginAttemptsException
├── security        # JWT 인증/인가
│   ├── JwtTokenProvider            # 액세스 토큰 발급/검증, 리프레시 토큰 값 생성
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
| Repository 통합 테스트 | `PostRepositoryTest`, `CommentRepositoryTest` | `@DataJpaTest`로 실제 DB에 커스텀 쿼리(제목 대소문자 무시 검색, 댓글 작성순 정렬)를 실행해 검증 |

정상 케이스뿐 아니라 존재하지 않는 리소스 조회·수정·삭제 시 예외가 올바르게 던져지는지, 잘못된 입력이 400으로 막히는지, 인증 없는 요청이 401로 막히는지, 본인 소유가 아닌 글/댓글 수정·삭제가 403으로 막히는지까지 함께 검증합니다.

**Repository 테스트는 H2가 아닌 실제 MySQL을 그대로 사용**합니다 (`@AutoConfigureTestDatabase(replace = Replace.NONE)`). `LIKE` 대소문자 처리 등은 DB/컬레이션마다 동작이 달라서, 배포 환경과 다른 임베디드 DB로 통과시켜봐야 신뢰할 수 없기 때문입니다. 로컬 개발 DB를 그대로 쓰지만 `@DataJpaTest`가 각 테스트를 트랜잭션으로 감싸 종료 시 롤백하므로 실제 데이터는 남지 않습니다.

## 실행 방법

### Docker Compose (추천 — MySQL 설치 없이 바로)

```bash
git clone https://github.com/아이디/저장소명.git
cd 저장소명

cp .env.example .env
# .env의 JWT_SECRET을 무작위 문자열로 채우기

docker compose up
```

앱(`http://localhost:8080`)과 MySQL이 함께 뜨고, Flyway가 스키마를 자동으로 만듭니다.
로컬에 MySQL을 따로 설치하거나 계정을 만들 필요가 없습니다. 데이터는 `mysql-data` 볼륨에
남아서, `docker compose down` 후 다시 올려도 유지됩니다 (완전히 지우려면 `docker compose down -v`).

### 운영 배포 (VM)

운영 서버는 MySQL이 이미 네이티브로 설치돼 있고 실제 데이터가 들어있어서, `docker-compose.prod.yml`로
**앱만** 컨테이너로 띄웁니다 (MySQL은 그대로 둠). VM에서:

```bash
git clone https://github.com/아이디/저장소명.git   # 최초 1회
cd 저장소명
git pull                                          # 이후 배포마다

echo "JWT_SECRET=운영용_시크릿" > .env

docker compose -f docker-compose.prod.yml up -d --build
```

`network_mode: host`로 띄우기 때문에 기존과 동일하게 `127.0.0.1:8080`에 바인딩되고,
`127.0.0.1:3306`의 네이티브 MySQL에도 그대로 접속합니다 — Nginx 설정을 바꿀 필요가 없습니다.
로컬에서 jar를 빌드해 scp로 옮기던 방식은 더 이상 쓰지 않습니다.

### 직접 실행

```bash
git clone https://github.com/아이디/저장소명.git
cd 저장소명

cp src/main/resources/application-example.properties \
   src/main/resources/application.properties
# DB 계정 정보, jwt.secret(무작위 문자열)을 입력

./mvnw spring-boot:run
```

이 경우 DB를 미리 생성해야 합니다.

```sql
CREATE DATABASE springdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'springuser'@'localhost' IDENTIFIED BY '비밀번호';
GRANT ALL PRIVILEGES ON springdb.* TO 'springuser'@'localhost';
```

테이블은 직접 만들 필요 없습니다 — 앱을 처음 띄우면 Flyway가 `src/main/resources/db/migration`의 마이그레이션을 실행해 스키마를 만듭니다.

## 스키마 관리 (Flyway)

원래는 `spring.jpa.hibernate.ddl-auto=update`로 Hibernate가 스키마를 알아서 맞추게 했습니다. 문제는 이 방식이
**새 컬럼은 추가해도 안 쓰는 옛날 컬럼은 절대 안 지운다**는 것입니다. `Comment.author`를 문자열에서 `User`
연관관계로 바꿨을 때, 운영 DB에는 옛날 `author` 컬럼이 `NOT NULL`로 그대로 남아 댓글 작성이 500으로 막히는
사고가 실제로 있었습니다 — 로컬은 우연히 문제없었지만 운영은 아니었던, 전형적인 환경별 스키마 드리프트입니다.

지금은 `db/migration/V1__init.sql`로 스키마를 버전 관리하고, `spring.jpa.hibernate.ddl-auto=validate`로
바꿔서 **엔티티와 실제 DB 스키마가 다르면 앱이 아예 기동하지 않도록** 했습니다. 조용히 어긋나는 대신 시끄럽게
실패하는 쪽을 택한 것입니다.

기존에 `ddl-auto=update`로 이미 만들어져 있던 로컬/운영 DB는 `spring.flyway.baseline-on-migrate=true`
덕분에 "이미 V1까지 적용됨"으로 표시만 되고 마이그레이션이 실제로 실행되지는 않습니다. 반대로 CI처럼
완전히 빈 DB에서는 `V1__init.sql`이 그대로 실행되어 지금 엔티티가 기대하는 스키마를 처음부터 만듭니다.
앞으로 스키마를 바꿀 땐 `V2__xxx.sql`처럼 새 마이그레이션 파일을 추가하는 방식으로 진행합니다.

## 설계 시 고려한 점

**Entity에 Setter를 두지 않음**
값을 아무 곳에서나 바꿀 수 있으면 변경 추적이 불가능해집니다. `update()` 메서드로 변경 통로를 하나로 제한했습니다.

**Entity와 DTO 분리**
Entity를 그대로 응답하면 내부 구조 변경이 API 스펙 변경으로 이어지고, 노출하면 안 되는 필드까지 나갑니다.

**트랜잭션 기본값을 읽기 전용으로**
클래스에 `@Transactional(readOnly = true)`를 걸고 쓰기 메서드에만 개별 적용했습니다. 실수로 쓰기가 일어나는 것을 막고, 하이버네이트가 스냅샷을 만들지 않아 성능에도 유리합니다.

**애플리케이션을 root로 실행하지 않음**
Docker 컨테이너는 기본적으로 root로 실행되기 쉬운데, `Dockerfile`에서 전용 유저(`appuser`)를
만들어 `USER appuser`로 전환해뒀습니다. (Docker 이전에는 systemd에 전용 계정 `demoapp`을
지정하는 방식으로 같은 원칙을 지켰습니다.)

**댓글은 게시글에 종속**
`@OneToMany(cascade = ALL, orphanRemoval = true)`로 연관관계를 맺어, 게시글이 삭제되면 댓글도 함께 삭제되도록 했습니다. 댓글만 따로 존재할 이유가 없기 때문입니다.

**인증을 세션이 아닌 JWT로**
서버가 클라이언트 상태를 들고 있지 않는 stateless 구조라 서버를 여러 대로 늘려도 세션 동기화 문제가 없습니다. 단점은 발급된 토큰을 서버에서 즉시 무효화할 수 없다는 점인데, 만료 시간을 짧게(1시간) 잡아 완화했습니다.

**JWT 시크릿은 이미지에 넣지 않음**
`jwt.secret`은 로컬 `application.properties`(gitignore 대상)에만 두고, Docker 이미지 자체에는
포함하지 않습니다. 운영 서버에서는 `docker-compose.prod.yml`이 VM의 `.env`(마찬가지로 gitignore)에서
`JWT_SECRET`을 읽어 컨테이너 환경변수로 주입합니다. 로컬 개발 이미지를 그대로 배포해도 운영 시크릿이
따로 유지되는 구조입니다. CI에서는 GitHub Actions Secrets로 별도 값을 주입합니다.

**작성자를 요청 본문이 아닌 인증 정보에서 결정**
초기 버전은 댓글 작성자를 클라이언트가 보내는 문자열로 그대로 믿었습니다. 인증 도입 후에는 JWT에서 추출한 로그인 사용자로 서버가 직접 지정하도록 바꿔, 클라이언트가 임의로 "다른 사람 이름"을 보낼 수 없게 했습니다.

##
