# 트러블슈팅 기록

프로젝트 진행 중 마주친 문제와 해결 과정.

---

## 1. Gradle 데몬 연결 실패 → Maven 전환

### 증상

```
Could not connect to the Gradle daemon.
Caused by: java.net.ConnectException: Connection refused
Tried addresses: [/127.0.0.1]
```

### 분석

데몬 로그를 확인하니 데몬 자체는 정상 기동하고 있었다.

```
Daemon server started.
Listening on [... port:38735, addresses:[localhost/127.0.0.1]]
```

즉 서버는 떴는데 클라이언트가 붙지 못하는 상황. loopback 자체를 의심해 직접 검증했다.

```bash
python3 -m http.server 9999 &
curl -o /dev/null -w "%{http_code}" http://127.0.0.1:9999   # 200
```

loopback은 정상. 매 실행마다 데몬 uid와 포트가 달라지는 것으로 보아, 클라이언트가 참조하는 레지스트리와 실제 데몬이 어긋나는 문제로 판단했다.

### 시도한 것

| 조치 | 결과 |
|---|---|
| `--no-daemon` | 실패 (내부적으로 단일 사용 데몬을 띄움) |
| `rm -rf ~/.gradle/daemon` | 실패 |
| `wsl --shutdown` 후 재시작 | 실패 |
| Gradle 9.7 → 8.14 하향 | 실패 |
| `-Djava.net.preferIPv4Stack=true` | 실패 |

### 해결

Maven으로 전환. Maven은 데몬 구조가 없어 문제가 발생하지 않는다.

| Gradle | Maven |
|---|---|
| `./gradlew bootRun` | `./mvnw spring-boot:run` |
| `./gradlew build` | `./mvnw package` |

### 판단 근거

빌드 도구는 학습 목표가 아니었고, 원인을 특정하지 못한 상태에서 시간이 계속 소모되고 있었다. Spring 학습 내용에는 차이가 없으므로 우회를 선택했다.

---

## 2. MySQL 접근 거부 — 1044와 1045 구분

### 증상

배포 서버에서 애플리케이션 기동 실패.

```
HHH000247: ErrorCode: 1044, SQLState: 42000
Access denied for user 'springuser'@'localhost' to database 'springdb'
```

### 분석

로컬 개발 환경에서는 동일한 계정 정보로 정상 동작했다. 오류코드를 확인한 결과:

| 코드 | 의미 |
|---|---|
| 1044 | 계정 인증은 성공, **DB 접근 권한 없음** |
| 1045 | 계정·비밀번호 자체가 틀림 |

1044이므로 비밀번호 문제가 아니라 `GRANT` 누락이었다.

```sql
SHOW GRANTS FOR 'springuser'@'localhost';
-- GRANT USAGE ON *.* ...   ← 로그인만 가능한 상태
```

### 해결

```sql
GRANT ALL PRIVILEGES ON springdb.* TO 'springuser'@'localhost';
FLUSH PRIVILEGES;
```

### 배운 점

`Access denied`라는 문구만 보고 비밀번호를 의심하면 시간을 낭비한다. 오류코드가 원인을 정확히 구분해준다.

---

## 3. 검증(@Valid)이 동작하지 않고 500 반환

### 증상

빈 제목으로 POST 요청 시 400이 아닌 500이 반환됐다.

### 분석

체크할 지점을 순서대로 좁혔다.

```bash
grep -A2 "validation" pom.xml                    # 의존성: 있음
grep -n "Valid" .../PostController.java          # @Valid: 있음
grep -n "jakarta.validation" .../PostDto.java    # import: 없음
```

DTO에 `@NotBlank`는 있는데 import가 없었다. 그런데도 서버가 돌고 있다는 것은, 컴파일이 실패했고 **이전에 컴파일된 클래스로 실행 중**이라는 뜻이었다.

### 해결

import를 추가하고 클린 빌드.

```bash
./mvnw clean compile
```

이 과정에서 추가 오류도 드러났다.

```
cannot find symbol: method Max()
location: @interface jakarta.validation.constraints.Size
```

`@Size(Max = 200)` → `@Size(max = 200)`. 어노테이션 속성명은 소문자로 시작한다.

### 배운 점

`clean` 없이 빌드하면 이전 산출물이 남아 증상을 왜곡한다. 원인이 불분명할 때는 클린 빌드로 상태를 초기화하고 시작한다.

---

## 4. systemd 서비스 기동 실패

### 증상 1

```
Unknown key 'Execstart' in section [Service], ignoring.
Service has no ExecStart=. Refusing.
```

systemd 유닛 키는 대소문자를 구분한다. `ExecStart`가 정확한 표기.

### 증상 2

```
Unrecognized option: -jar/opt/demo/app.jar
Error: Could not create the Java Virtual Machine.
```

`ExecStart`에서 `-jar`와 경로 사이 공백이 누락됐다. systemd는 명령줄을 그대로 실행하므로 공백이 인자 구분자다.

### 검증 방법

작성 후 반드시 검증한다.

```bash
sudo systemd-analyze verify /etc/systemd/system/demo.service
```

문제가 있는 줄과 키를 정확히 짚어준다. 파일 수정 후에는 `daemon-reload`가 필요하다.

### 최종 유닛 파일

```ini
[Service]
Type=simple
User=demoapp
ExecStart=/usr/bin/java -jar /opt/demo/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
```

`SuccessExitStatus=143`이 없으면 JVM의 정상 종료(SIGTERM, 143)도 실패로 기록된다.

---

## 5. WSL 브리지 네트워크에서 브라우저 접속 불가

### 증상

WSL에서 실행한 서버에 `curl localhost:8080`은 성공하나, Windows 브라우저에서는 `ERR_SOCKET_NOT_CONNECTED`.

### 분석

```bash
hostname -I
# 192.168.1.56 ...  ← Windows 호스트와 동일한 IP
```

WSL이 미러 네트워킹 모드로 동작 중이었다. 이 모드에서 Windows → WSL 포트 포워딩이 정상 동작하지 않았다.

바인딩은 정상이었다.

```bash
ss -tulpn | grep 8080
# tcp LISTEN *:8080     ← 모든 인터페이스에서 수신 중
```

같은 계열의 증상으로 VS Code Java 디버거도 실패했다.

```
connect ECONNREFUSED 127.0.0.1:36247
```

### 대응

`.wslconfig`에서 `networkingMode=nat` 전환을 시도했으나 해결되지 않아, curl 기반 테스트로 전환했다.

```bash
curl -i -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"제목","content":"내용"}'
```

REST API는 GET 외 메서드를 브라우저로 테스트할 수 없으므로, 애초에 curl이 정석적인 방법이다. 학습·개발에 지장이 없다고 판단해 우회했다.

VM 배포 환경(브리지 네트워크)에서는 브라우저 접속이 정상 동작했다.

---

## 6. 컴파일 오류 위치와 실제 원인의 불일치

### 증상

```
PostService.java:[32,39] ';' expected
PostService.java:[32,56] ';' expected
PostService.java:[32,72] ';' expected
PostService.java:[40,27] ';' expected
PostService.java:[40,35] ';' expected
PostService.java:[46,2] reached end of file while parsing
```

### 분석

32번과 40번 줄을 아무리 봐도 문법에 문제가 없었다. 마지막 메시지가 단서였다.

`reached end of file while parsing`은 **닫는 중괄호가 부족하다**는 뜻이다. 실제 원인은 30번 줄에서 메서드가 닫히지 않은 것이었고, 그 아래 코드가 전부 해당 메서드 내부로 해석되면서 32번·40번에 연쇄 오류가 발생했다.

### 배운 점

컴파일러가 지목한 줄이 원인이 아닐 수 있다. 특히 중괄호 문제는 오류가 아래쪽에 몰려서 나타난다.

```bash
grep -c "{" 파일 && grep -c "}" 파일   # 개수가 같아야 함
```

이후 IDE(VS Code + Java 확장)를 도입해 이 유형의 오류를 작성 시점에 잡도록 했다.

---

## 정리

| 문제 | 핵심 |
|---|---|
| Gradle 데몬 | 원인 특정 실패 시 우회 판단도 해결책 |
| MySQL 1044 | 오류코드로 원인 구분 |
| 검증 미동작 | 클린 빌드로 상태 초기화 후 진단 |
| systemd | `systemd-analyze verify`로 사전 검증 |
| WSL 네트워크 | 목적에 필요한 경로만 확보하면 충분 |
| 컴파일 오류 | 지목된 줄 ≠ 실제 원인 |

공통적으로, **증상을 그대로 믿지 않고 어디까지 정상인지 확인해 범위를 좁히는 방식**이 유효했다.

