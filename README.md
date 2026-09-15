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

| Method | Endpoint | 설명 | 성공 응답 |
|---|---|---|---|
| POST | `/api/posts` | 게시글 생성 | 201 + Location |
| GET | `/api/posts` | 목록 조회 | 200 |
| GET | `/api/posts/{id}` | 단건 조회 | 200 |
| PUT | `/api/posts/{id}` | 수정 | 200 |
| DELETE | `/api/posts/{id}` | 삭제 | 204 |
| POST | `/api/posts/{postId}/comments` | 댓글 작성 | 201 + Location |
| GET | `/api/posts/{postId}/comments` | 게시글의 댓글 목록 조회 | 200 |
| PUT | `/api/comments/{commentId}` | 댓글 수정 | 200 |
| DELETE | `/api/comments/{commentId}` | 댓글 삭제 | 204 |

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
| 존재하지 않는 리소스 | 404 |
| 예상치 못한 오류 | 500 |

## 패키지 구조

```
com.example.demo
├── common          # 공통 (에러 응답, 전역 예외 처리)
└── post            # 게시글 / 댓글 도메인
    ├── Post                    # Entity
    ├── PostRepository          # Repository
    ├── PostDto                 # Request / Response
    ├── PostService             # 비즈니스 로직
    ├── PostController          # HTTP 처리
    ├── PostNotFoundException
    ├── Comment                 # Entity (Post와 @ManyToOne)
    ├── CommentRepository       # Repository
    ├── CommentDto              # Request / Response
    ├── CommentService          # 비즈니스 로직
    ├── CommentController       # HTTP 처리
    └── CommentNotFoundException
```

계층별이 아닌 **도메인 단위** 구조로 관련 코드를 한곳에 모았습니다.

## 실행 방법

```bash
git clone https://github.com/아이디/저장소명.git
cd 저장소명

cp src/main/resources/application-example.properties \
   src/main/resources/application.properties
# DB 계정 정보 입력

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

##
