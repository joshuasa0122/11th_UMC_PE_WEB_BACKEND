# UMC 영화 평점 서비스 백엔드 (Spring Boot)

## 9주차 — Swagger 문서화 · 단위/통합 테스트

### 실행

```bash
JWT_SECRET=<32자 이상 비밀키> JWT_ACCESS_EXPIRATION=3600 ./gradlew bootRun
# 문서 화면: http://localhost:8080/swagger-ui.html
# OpenAPI JSON: http://localhost:8080/v3/api-docs

./gradlew test   # MySQL·JWT 환경 변수 없이도 실행됩니다 (테스트는 H2 + src/test/resources/application.yaml)
```

### 1. Swagger 붙이기

- `build.gradle`에 `springdoc-openapi-starter-webmvc-ui:2.8.9`를 추가했습니다.
- `SwaggerConfig`에 문서 제목과 `bearerAuth`(HTTP Bearer, JWT) 인증 방식을 등록했습니다.
- 인터셉터가 모든 경로를 잠그고 있어서 처음에는 문서 화면도 `AUTH401_2`로 막혔습니다. `WebConfig` 예외 목록에 `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`를 추가했습니다. `/swagger-ui.html`은 `/swagger-ui/index.html`로 302 리다이렉트되므로 두 경로를 모두 열어야 합니다.
- **`@LoginMember` 숨기기**: 워크북은 `@RequestAttribute`를 쓰지만, 이 프로젝트는 7주차에 만든 `@LoginMember Long memberId`를 씁니다. springdoc은 이 값을 `memberId` 쿼리 파라미터로 문서에 그립니다. 토큰에서 채워지는 값이라 `SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginMember.class)`로 숨겼습니다.

### 2. 문서화한 내용

| API | 문서화 |
| --- | --- |
| `POST /members` | `@Tag("회원")`, `@Operation`, `SignUpRequest`의 `@Schema` 설명·예시 |
| `POST /auth/login` | `@Tag("인증")`, `@Operation`, `LoginRequest`의 `@Schema` 설명·예시 |
| `POST /favorites` | `@Tag("즐겨찾기")`, 클래스 단위 `@SecurityRequirement`, 201·401·409 응답 예시 (`@ApiResponses`는 공통 응답 DTO와 이름이 겹쳐 풀네임 사용) |

자물쇠 표시는 인터셉터 공개 경로와 똑같이 맞췄습니다. 그래서 즐겨찾기 3개 외에 평점 쓰기, 영화 기록, 내 정보 API에도 `@SecurityRequirement`를 붙였습니다.

| 자물쇠 있음 (토큰 필요) | 자물쇠 없음 (공개) |
| --- | --- |
| `/favorites/**`, `POST /ratings`, `PATCH·DELETE /ratings/{id}`, `POST /movie-records`, `GET·PATCH /members/me` | `POST /members`, `POST /auth/login`, `GET /members/{id}/ratings`, `GET /members/nickname·email/{v}` |

Swagger에서 `POST /favorites`를 호출해 같은 요청의 결과가 `201 COMMON201` → `409 FAVORITE409_1`로 바뀌고, 토큰을 빼면 `401 AUTH401_2`가 나오는 것을 확인했습니다.

### 3. 테스트

| 종류 | 파일 | 테스트 |
| --- | --- | --- |
| 단위 (Mockito) | `service/FavoriteServiceTest` | 새 영화면 저장 / 중복이면 `FAVORITE409_1` 예외 + `save` 호출 안 함 |
| 통합 (MockMvc + H2) | `controller/FavoriteApiTest` | 토큰 없음 401 / 등록 201 / 중복 409 / 여부 조회 `isFavorite: false` / `/v3/api-docs` 공개 |

- 테스트 전용 `src/test/resources/application.yaml`에서 H2 메모리 DB(`MODE=MySQL`, `ddl-auto: create-drop`)와 JWT 값을 지정했습니다. 개발 DB를 건드리지 않고, 기존 `DemoApplicationTests`도 MySQL 없이 통과합니다.
- 결과: `./gradlew test` → 8개 통과 (단위 2, 통합 5, 기존 1)

### 4. 일부러 깨뜨리기

`FavoriteService.create`의 중복 검사 `if` 블록을 주석 처리하자 3개가 실패했습니다. 되돌리자 다시 8개 모두 통과했습니다.

| 실패한 테스트 | 오류 메시지 | 이유 |
| --- | --- | --- |
| 단위: 중복이면 예외 | `Expecting actual throwable to be an instance of: ApiException but was: NullPointerException` | 예외 없이 `save`까지 갔고, 가짜 `save`가 `null`을 돌려줘 `FavoriteResponse.from`에서 NPE가 났습니다. |
| 단위: 새 영화면 저장 | `UnnecessaryStubbingException` | `existsBy...` 호출이 사라져, 미리 정해 둔 가짜 응답이 쓰이지 않았습니다. Mockito strict stubs가 이를 실패로 처리합니다. |
| 통합: 중복이면 409 | `JSON path "$.code" expected:<FAVORITE409_1> but was:<COMMON4091>` | DB UNIQUE 제약이 막아 409는 나왔습니다. 하지만 `GlobalExceptionHandler`는 MySQL 메시지의 키 이름(`'favorite.uk_favorite_member_movie'`)으로 code를 고르는데, H2는 메시지 형식이 달라 기본 `COMMON4091`이 나왔습니다. |

마지막 줄은 덤으로 얻은 발견입니다. 동시 요청 대비용 DB 제약 → code 변환은 MySQL 메시지에 기대고 있어서 H2 통합 테스트로는 검증되지 않습니다.

### 5. 미션 — 문서 채우기 · 평점 API 테스트

**문서화**: 모든 API에 `@Operation` 한 줄 설명을 넣었습니다. 토큰 필요 여부는 자물쇠(`@SecurityRequirement`)와 설명 첫 문장("토큰 필요." / "공개 API") 양쪽에 적었습니다. 실패 응답은 `@ApiResponses`에 **상태 코드와 `code`를 함께** 적었습니다.

| API | 토큰 | 성공 | 실패 (상태 · code) |
| --- | --- | --- | --- |
| `POST /ratings` 평점 등록 | 필요 | 201 | 400 `COMMON4001`, 401, 404 `MEMBER404_1`, 409 `RATING409_1` |
| `PATCH /ratings/{ratingId}` 평점 수정 | 필요 | 200 | 400 `COMMON4001`, 401, 403 `RATING403_1`, 404 `RATING404_1` |
| `DELETE /ratings/{ratingId}` 평점 삭제 | 필요 | 200 | 401, 403 `RATING403_1`, 404 `RATING404_1` |
| `GET /members/{memberId}/ratings` 유저별 평점 | 공개 | 200 (배열) | 404 `MEMBER404_1` |
| `GET /members/me` 내 정보 조회 | 필요 | 200 | 401, 404 `MEMBER404_1` |
| `PATCH /members/me` 내 정보 수정 | 필요 | 200 | 400 `COMMON4001`, 401, 404 `MEMBER404_1`, 409 `MEMBER409_1` |
| `GET /favorites` 내 즐겨찾기 | 필요 | 200 | 401 |
| `GET /favorites/{movieId}` 즐겨찾기 여부 | 필요 | 200 (`isFavorite`) | 401 |
| `DELETE /favorites/{movieId}` 즐겨찾기 해제 | 필요 | 200 | 401, 404 `FAVORITE404_1` |

401은 모두 `AUTH401_2`(토큰 없음) / `AUTH401_3`(유효하지 않음) / `AUTH401_4`(만료)로 적었습니다. 미션 범위 밖이지만 문서가 `200 OK`로 잘못 나오던 회원가입(201, 409 `MEMBER4091`·`MEMBER4092`), 로그인(401 `AUTH401_1`), 영화 기록(201, 409 `RATING409_1`·`FAVORITE409_1`)도 실제 응답에 맞췄습니다. 평점·내 정보 수정 요청 DTO에는 `@Schema` 예시 값을 넣었습니다.

실패 응답에는 `content = @Content`를 붙였습니다. 비워 두면 springdoc이 성공 응답 스키마를 실패 응답에도 그려서, 실패해도 `RatingResponse`가 오는 것처럼 보이기 때문입니다.

**평점 테스트**

| 종류 | 테스트 이름 (`@DisplayName`) |
| --- | --- |
| 단위 `RatingServiceTest` | 이미 평점을 남긴 영화에 다시 평점을 등록하면 409 RATING409_1 예외를 던지고 저장하지 않는다 |
| 단위 `RatingServiceTest` | 다른 회원의 평점을 수정하려 하면 403 RATING403_1 예외를 던지고 평점은 바뀌지 않는다 |
| 통합 `RatingApiTest` | 토큰 없이 평점을 등록하면 401과 AUTH401_2를 응답하고 평점은 저장되지 않는다 |
| 통합 `RatingApiTest` | 토큰을 담아 평점을 등록하면 201과 COMMON201, 저장된 영화 ID·별점을 응답한다 |

- 단위 테스트는 예외 종류뿐 아니라 `status`(409/403)와 `code`까지 확인합니다. "저장하지 않았다", "평점이 바뀌지 않았다"도 함께 검증합니다.
- 소유권 검사는 `memberId`를 비교하는데, 이 값은 원래 DB가 채워 줍니다. 단위 테스트에서는 `ReflectionTestUtils.setField`로 직접 넣었습니다.
- 결과: `./gradlew test` → **12개 통과** (단위 4, 통합 7, 기존 1)

### 트러블 슈팅

<details>
<summary>펼치기</summary>

#### ⚡ 이슈 No.1 — 서버 실행 시 `JWT_SECRET`을 찾지 못함

**`이슈`**

👉 `./gradlew bootRun`으로 서버를 켜자 바로 종료됐어요.

```
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'jwtUtil' defined in file [.../build/classes/java/main/com/umc/movie/util/JwtUtil.class]: Unexpected exception during bean creation
Caused by: org.springframework.util.PlaceholderResolutionException: Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}" <-- "${jwt.secret}"
BUILD FAILED in 7s
```

**`문제`**

👉 7주차에 비밀키를 저장소에 올리지 않으려고 `application.yaml`에서 `jwt.secret: ${JWT_SECRET}`처럼 환경 변수를 참조하게 했어요. 이번에는 환경 변수 없이 서버를 켜서 `JwtUtil` Bean을 만들지 못했어요.

**`해결`**

👉 실행할 때 환경 변수를 함께 넘겼어요.

```bash
JWT_SECRET=$(openssl rand -hex 32) JWT_ACCESS_EXPIRATION=3600 ./gradlew bootRun
```

테스트는 매번 환경 변수를 넣지 않아도 되게 `src/test/resources/application.yaml`에 테스트 전용 `jwt.secret` 값을 적었어요.

**`참고레퍼런스`**

- Spring Boot - Externalized Configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html

---

#### ⚡ 이슈 No.2 — Next.js 프론트에서 로그인하면 CORS로 막힘

**`이슈`**

👉 프론트를 Next.js로 옮긴 뒤 로그인하면 "서버와 통신하지 못했어요."가 표시됐어요. curl로 `POST /auth/login`을 보내면 성공했어요. 브라우저가 먼저 보내는 preflight를 같은 조건으로 보내 보니 403이었어요.

```
OPTIONS http://localhost:8080/auth/login
Origin: http://localhost:3000
Access-Control-Request-Method: POST
→ 403
```

**`문제`**

👉 `WebConfig`의 CORS 설정이 Vite 개발 서버 주소 `http://localhost:5173`만 허용하고 있었어요. Next.js 개발 서버는 `localhost:3000`이라 허용 목록에 없는 출처였어요.

**`해결`**

👉 `allowedOrigins`에 `http://localhost:3000`을 추가하고 서버를 다시 켰어요. preflight가 200으로 바뀌고 브라우저 로그인이 성공했어요.

```java
.allowedOrigins("http://localhost:5173", "http://localhost:3000")
```

테스트 코드는 이 문제를 잡지 못했어요. CORS는 브라우저가 거는 제약이라 MockMvc 요청에서는 생기지 않아요. 그래서 테스트가 초록불이어도 브라우저에서 한 번은 직접 확인해야 해요.

**`참고레퍼런스`**

- Spring Framework - CORS: https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html
- MDN - Preflight request: https://developer.mozilla.org/en-US/docs/Glossary/Preflight_request

---

#### ⚡ 이슈 No.3 — 통합 테스트에서 409인데 `code`가 `COMMON4091`

**`이슈`**

👉 실습 6에서 `FavoriteService`의 중복 검사를 주석 처리했어요. 상태 코드는 409로 그대로였는데, `code`가 예상과 달라 통합 테스트가 실패했어요.

```
java.lang.AssertionError: JSON path "$.code" expected:<FAVORITE409_1> but was:<COMMON4091>
```

**`문제`**

👉 중복 검사가 빠져도 DB의 UNIQUE 제약이 두 번째 저장을 막아요. 그때 나는 `DataIntegrityViolationException`을 `GlobalExceptionHandler`가 code로 바꾸는데, **MySQL 오류 메시지의 키 이름**(`'favorite.uk_favorite_member_movie'`)으로 어떤 제약인지 구분해요. 테스트는 H2 메모리 DB를 쓰고, H2는 오류 메시지 형식이 MySQL과 달라요. 그래서 일치하는 조건이 없어 기본값 `COMMON4091`이 나왔어요.

**`해결`**

👉 중복 검사를 되돌려 테스트를 다시 통과시켰어요. 함께 알게 된 점이 있어요. 동시 요청 대비용 "DB 제약 위반 → code 변환"은 MySQL에 기대고 있어서 H2 통합 테스트로는 검증되지 않아요. 이 한계를 README와 PR에 기록했어요. 이 경로까지 검증하려면 실제 MySQL로 테스트하거나(예: Testcontainers), 메시지 대신 제약 이름을 꺼내 비교하는 방식으로 바꿔야 해요.

**`참고레퍼런스`**

- H2 Database - Compatibility: https://www.h2database.com/html/features.html#compatibility
- Testcontainers - MySQL Module: https://java.testcontainers.org/modules/databases/mysql/

---

#### ⚡ 이슈 No.4 — 건드리지 않은 정상 케이스 단위 테스트까지 실패

**`이슈`**

👉 실습 6에서 중복 검사를 지웠을 때 "중복" 테스트만 실패할 줄 알았어요. 그런데 "아직 즐겨찾기하지 않은 영화면 저장한다" 테스트도 실패했어요.

```
org.mockito.exceptions.misusing.UnnecessaryStubbingException:
Unnecessary stubbings detected.
Clean & maintainable test code requires zero unnecessary code.
Following stubbings are unnecessary (click to navigate to relevant line of code):
  1. -> at com.umc.movie.service.FavoriteServiceTest.createFavorite(FavoriteServiceTest.java:49)
Please remove unnecessary stubbings
```

**`문제`**

👉 `@ExtendWith(MockitoExtension.class)`는 기본적으로 **strict stubs** 모드예요. 정상 케이스 테스트는 `given(favoriteRepository.existsBy...).willReturn(false)`로 가짜 응답을 정해 뒀어요. 그런데 중복 검사 코드가 사라져 이 메서드가 한 번도 호출되지 않았어요. Mockito는 쓰이지 않은 stub을 "테스트가 실제 코드와 어긋났다"는 신호로 보고 실패시켜요.

**`해결`**

👉 중복 검사를 되돌리자 stub이 다시 쓰여 통과했어요. 이 실패는 오히려 유용했어요. 결과값만 보면 정상 케이스는 통과할 수 있는데, strict stubs 덕분에 "중복 검사 호출이 사라졌다"는 사실까지 잡혔어요.

**`참고레퍼런스`**

- Mockito - UnnecessaryStubbingException: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/exceptions/misusing/UnnecessaryStubbingException.html
- Mockito - Strictness: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/quality/Strictness.html

---

#### ⚡ 이슈 No.5 — `./gradlew test`가 0.3초 만에 끝나고 테스트 결과가 안 보임

**`이슈`**

👉 테스트 통과 화면을 캡처하려고 실행했는데, PASSED 줄 없이 바로 끝났어요.

```
$ ./gradlew test --console=plain -i | grep -E "PASSED|FAILED|BUILD"
BUILD SUCCESSFUL in 372ms
```

**`문제`**

👉 Gradle은 입력(소스·의존성)이 바뀌지 않으면 이전 결과를 재사용하고 태스크를 건너뛰어요(UP-TO-DATE). 직전에 이미 같은 코드로 테스트를 돌렸기 때문에, 실제로는 테스트가 한 개도 실행되지 않았어요.

**`해결`**

👉 이전 결과를 지우고 다시 실행했어요. 그다음 HTML 리포트로 결과를 확인했어요.

```bash
./gradlew cleanTest test
open build/reports/tests/test/index.html
```

리포트에서 테스트 개수, 실패 수, 클래스별 결과를 한 화면에 확인했어요.

**`참고레퍼런스`**

- Gradle - Incremental build: https://docs.gradle.org/current/userguide/incremental_build.html
- Gradle - Testing in Java & JVM projects: https://docs.gradle.org/current/userguide/java_testing.html

---

#### ⚡ 이슈 No.6 — VS Code에 빨간 줄이 뜨는데 빌드는 성공

**`이슈`**

👉 Swagger 의존성과 테스트를 추가하자 VS Code에 오류가 표시됐어요. 하지만 `./gradlew test`는 성공했어요.

```
SwaggerConfig.java: The import io.swagger cannot be resolved
FavoriteServiceTest.java: The constructor CreateFavoriteRequest(long) is undefined
```

**`문제`**

👉 VS Code Java 확장은 Gradle과 별도로 프로젝트를 분석해요. `build.gradle`에 의존성을 추가해도 확장이 프로젝트 설정을 다시 읽기 전까지는 새 라이브러리를 모르고, Lombok·record 같은 정보도 늦게 반영돼요. 실제 컴파일러(Gradle)는 문제가 없었어요.

**`해결`**

👉 실제 상태는 `./gradlew compileJava` / `./gradlew test` 결과로 판단했어요. VS Code 표시는 명령 팔레트의 `Java: Reload Projects`(또는 `Java: Clean Java Language Server Workspace`)로 갱신하면 돼요. IntelliJ라면 코끼리 아이콘(Gradle 새로고침)이 같은 역할이에요.

**`참고레퍼런스`**

- VS Code - Java build tools: https://code.visualstudio.com/docs/java/java-build

</details>

### 그 외

- 프론트를 Next.js(`localhost:3000`)로 옮기면서 CORS `allowedOrigins`에 `http://localhost:3000`을 추가했습니다.

---

## 8주차 — 즐겨찾기 · 영화 기록 트랜잭션

### 실행 전 준비

`ddl-auto: validate`라서 `favorite` 테이블을 먼저 만들어야 서버가 뜹니다.

```bash
mysql -h127.0.0.1 -P3307 -uroot -p umc_movie < scripts/favorite.sql
```

### API 명세

모두 `Authorization: Bearer {token}`이 필요합니다. `WebConfig`의 공개 경로 목록에는 추가하지 않았습니다. memberId는 7주차 선택 미션에서 만든 `@LoginMember`로 받습니다.

| 기능 | Method | 경로 | 요청 본문 | 성공 |
| --- | --- | --- | --- | --- |
| 즐겨찾기 등록 | POST | `/favorites` | `{ movieId }` | 201 `result`: favoriteId·movieId·createdAt |
| 내 즐겨찾기 목록 | GET | `/favorites` | 없음 | 200 `result`: 배열 (최신순, 없으면 `[]`) |
| 즐겨찾기 여부 | GET | `/favorites/{movieId}` | 없음 | 200 `result`: `{ isFavorite }` |
| 즐겨찾기 해제 | DELETE | `/favorites/{movieId}` | 없음 | 200 `result`: null |
| 영화 기록 (평점 + 즐겨찾기) | POST | `/movie-records` | `{ movieId, score, comment?, addToFavorites }` | 201 `result`: `{ rating, favorite }` (`addToFavorites=false`면 favorite null) |

로그인 응답에 `memberId`를 추가했습니다. 프론트가 공개 API `GET /members/{memberId}/ratings`를 호출할 때 필요합니다.

### 실패 코드

| 상황 | 상태 | code |
| --- | --- | --- |
| 토큰 없이 호출 | 401 | `AUTH401_2` |
| 같은 영화 즐겨찾기 중복 | 409 | `FAVORITE409_1` (DB `uk_favorite_member_movie` 위반도 같은 code) |
| 즐겨찾기하지 않은 영화 해제 | 404 | `FAVORITE404_1` |
| `movieId` 0 이하 / `addToFavorites` 누락 | 400 | `COMMON4001` |
| 영화 기록: 이미 평점 있음 | 409 | `RATING409_1` |

### 핵심 코드

- `FavoriteService.create`: `existsBy...`로 먼저 검사해 `FAVORITE409_1`을 응답합니다. 동시 요청은 DB UNIQUE 제약이 막고, `GlobalExceptionHandler`가 같은 code로 바꿔 응답합니다.
- `MovieRecordService.create`: `@Transactional` 메서드 하나에서 `rating`과 `favorite`를 저장합니다. `RatingService`와 `FavoriteService`를 연달아 부르면 저장이 두 트랜잭션으로 나뉠 수 있어서 전용 Service로 묶었습니다.
- 즐겨찾기: [Favorite](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week8-josh/src/main/java/com/umc/movie/entity/Favorite.java) · [FavoriteRepository](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week8-josh/src/main/java/com/umc/movie/repository/FavoriteRepository.java) · [FavoriteService](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week8-josh/src/main/java/com/umc/movie/service/FavoriteService.java) · [FavoriteController](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week8-josh/src/main/java/com/umc/movie/controller/FavoriteController.java)
- 영화 기록: [MovieRecordService](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week8-josh/src/main/java/com/umc/movie/service/MovieRecordService.java) · [MovieRecordController](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week8-josh/src/main/java/com/umc/movie/controller/MovieRecordController.java)

### 확인 결과 (curl, 로컬 MySQL)

| 요청 | 결과 |
| --- | --- |
| `POST /favorites` 550 → 같은 요청 | 201 → 409 `FAVORITE409_1` |
| `GET /favorites` → `GET /favorites/550` | 200 배열 1개 → 200 `isFavorite: true` |
| `DELETE /favorites/550` → `GET /favorites/550` → `GET /favorites` | 200 `result: null` → `isFavorite: false` → 200 `[]` |
| `POST /movie-records` 238 (`addToFavorites: true`) | 201, `rating`·`favorite` 모두 채워짐 (DB rating 1, favorite 1) |
| 같은 요청 다시 | 409 `RATING409_1`, DB 행 수 변화 없음 |
| `addToFavorites: false` | 201, `favorite: null`, rating만 1행 추가 |
| 토큰 없음 / 없는 즐겨찾기 해제 / `movieId: 0` | 401 `AUTH401_2` / 404 `FAVORITE404_1` / 400 |

**rollback 확인**: 위 409는 저장 전 사전 검사에서 막힌 경우라 rollback이 일어나지 않습니다. 그래서 영화 40개에 대해 `POST /favorites`와 `POST /movie-records`(`addToFavorites: true`)를 동시에 보냈습니다. 서버 로그에서 `favorite.uk_favorite_member_movie` 위반이 29건 나왔습니다. `/movie-records`는 201 9개, `FAVORITE409_1` 31개였고, DB의 rating은 성공한 9행뿐이었습니다. 즐겨찾기 저장이 DB 제약에 막힌 요청은 앞서 저장한 평점까지 함께 rollback된 것입니다.

---

## 7주차 — 로그인 · JWT 인증 · 소유권 검증

### 실행 환경변수

`application.yaml`은 `${JWT_SECRET}`, `${JWT_ACCESS_EXPIRATION}`만 참조하고 값은 실행 환경에 넣습니다. 비밀키는 저장소에 올리지 않습니다.

```bash
JWT_SECRET=<32자 이상 비밀키> JWT_ACCESS_EXPIRATION=3600 ./gradlew bootRun
```

### API 명세

| 기능 | Method | 경로 | 헤더 | 요청 본문 | 성공 |
| --- | --- | --- | --- | --- | --- |
| 로그인 | POST | `/auth/login` | - | `{ email, password }` | 200 `result`: nickname·accessToken |
| 내 정보 조회 | GET | `/members/me` | `Authorization: Bearer {token}` | 없음 | 200 `result`: email·nickname·createdAt |
| 내 정보 수정 | PATCH | `/members/me` | `Authorization: Bearer {token}` | `{ nickname }` | 200 내 정보 조회와 동일 |
| 평점 등록 | POST | `/ratings` | `Authorization: Bearer {token}` | `{ movieId, score, comment? }` | 201 `result`: RatingResponse |
| 평점 수정 | PATCH | `/ratings/{ratingId}` | `Authorization: Bearer {token}` | `{ score, comment? }` | 200 `result`: RatingResponse |
| 평점 삭제 | DELETE | `/ratings/{ratingId}` | `Authorization: Bearer {token}` | 없음 | 200 `result`: null |

회원가입, 닉네임·이메일 중복 체크, 유저별 평점 조회(`GET /members/{memberId}/ratings`)는 토큰 없이 호출합니다.

### 실패 코드

| 상황 | 상태 | code |
| --- | --- | --- |
| 이메일 없음 / 비밀번호 틀림 (구분하지 않음) | 401 | `AUTH401_1` |
| Authorization 헤더 없음 | 401 | `AUTH401_2` |
| 위조된 토큰 | 401 | `AUTH401_3` |
| 만료된 토큰 | 401 | `AUTH401_4` |
| 회원 없음 | 404 | `MEMBER404_1` |
| 수정할 닉네임을 다른 회원이 사용 중 | 409 | `MEMBER409_1` |
| 평점 없음 | 404 | `RATING404_1` |
| 남의 평점 수정·삭제 | 403 | `RATING403_1` |
| 같은 영화에 평점 중복 | 409 | `RATING409_1` (6주차 `RATING4091`에서 변경, DB 제약 위반도 같은 code) |

### 핵심 코드

- [JwtUtil](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/util/JwtUtil.java) — 토큰 발급, 서명·만료 검증 후 memberId 추출
- [AuthInterceptor](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/interceptor/AuthInterceptor.java) · [WebConfig](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/config/WebConfig.java) — 전부 잠그고 공개 경로만 열어 컨트롤러 앞에서 토큰 검증
- [AuthService](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/service/AuthService.java) — `passwordEncoder.matches()`로 비교, 실패 원인을 하나의 응답으로 통일
- [RatingService.findOwnedRating](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/service/RatingService.java) — 404(없음) 확인 후 403(남의 것) 확인

### 확인 결과 (curl, 로컬 MySQL)

A(`a@umc.com`), B(`b@umc.com`) 두 회원으로 시나리오를 확인했습니다.

| 요청 | 결과 |
| --- | --- |
| 로그인 성공 | 200, 토큰 header `{"alg":"HS384"}`, payload `sub`·`email`·`iat`·`exp` |
| 비밀번호 틀림 / 없는 이메일 | 둘 다 401 `AUTH401_1`, 같은 메시지 |
| `/members/me` 헤더 없음 / 토큰 A / payload 한 글자 변경 / 만료 토큰 | 401 `AUTH401_2` / 200 / 401 `AUTH401_3` / 401 `AUTH401_4` |
| 닉네임을 자기 닉네임 그대로 / B 닉네임 / 1자 | 200 / 409 `MEMBER409_1` / 400 `COMMON4001` |
| 토큰 A로 평점 등록 → 중복 등록 → 본인 수정 | 201 → 409 `RATING409_1` → 200 |
| 토큰 B로 A의 평점 수정 | 403 `RATING403_1` |
| 토큰 없이 수정 / 없는 평점 삭제 / 본인 삭제 | 401 `AUTH401_2` / 404 `RATING404_1` / 200 |
| `TOKEN=... ./scripts/race.sh /ratings ...` 20개 동시 | 201 1개, 409 `RATING409_1` 19개, DB에는 1행 |

### 선택 미션 1 — 토큰 만료 직접 겪어보기

`JWT_ACCESS_EXPIRATION=60`으로 재시작한 뒤 같은 토큰으로 `GET /members/me`를 호출했습니다.

| 시각 | 경과 | 결과 |
| --- | --- | --- |
| 16:13:13 | 발급 (`exp` 16:14:13) | 200 |
| 16:14:16 | 63초 | **200** (`exp`는 지났지만 통과) |
| 16:15:11 | 118초 | **200** |
| 16:15:18 | 125초 | 401 `AUTH401_4` |

`exp`가 지나도 1분 동안 통과한 이유는 `JwtUtil`의 `clockSkewSeconds(60)` 때문입니다. 서버·클라이언트 시계 오차를 60초까지 허용하므로, 실제 만료 판정은 `exp + 60초`에 일어납니다. 그래서 유효시간을 60초로 두면 **2분이 지나야** `AUTH401_4`가 나옵니다. 헤더가 없을 때는 같은 401이어도 `AUTH401_2`라서 code만 보고 원인을 구분할 수 있습니다.

### 선택 미션 2 — `@LoginMember`로 memberId 꺼내기

- [LoginMember](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/resolver/LoginMember.java): 파라미터용 어노테이션
- [LoginMemberArgumentResolver](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/resolver/LoginMemberArgumentResolver.java): `@LoginMember Long` 파라미터에 인터셉터가 담아둔 memberId를 넣습니다. 값이 없으면(공개 경로에 잘못 붙인 경우) null 대신 401 `AUTH401_2`를 던집니다.
- [WebConfig](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week7-josh/src/main/java/com/umc/movie/config/WebConfig.java): `addArgumentResolvers`에 등록

```java
// 적용 전
public ResponseEntity<...> create(@RequestAttribute(AuthInterceptor.MEMBER_ID) Long memberId, ...)

// 적용 후
public ResponseEntity<...> create(@LoginMember Long memberId, ...)
```

컨트롤러에서 `@RequestAttribute`와 `AuthInterceptor.MEMBER_ID`(문자열 `"memberId"`)를 쓰는 곳이 없어졌습니다. 적용 후 내 정보 조회·수정, 평점 등록·수정(403 포함)·삭제를 다시 호출해 응답이 이전과 같은 것을 확인했습니다.

---

## 6주차 — 회원가입 · 평점 쓰기 API

### API 명세

모든 성공·실패 응답은 같은 형식을 사용합니다. (DELETE 204는 본문 없음)

```json
{ "isSuccess": true, "code": "COMMON201", "message": "회원가입에 성공했습니다.", "result": { "memberId": 3, "email": "umc@example.com", "nickname": "moviefan" } }
```

| 기능 | Method | 경로 | 요청 본문 | 성공 |
| --- | --- | --- | --- | --- |
| 회원가입 | POST | `/members` | `{ email, password, nickname }` | 201 `result`: memberId·email·nickname (password 없음) |
| 평점 등록 | POST | `/members/{memberId}/ratings` | `{ movieId, score, comment? }` | 201 `result`: ratingId |
| 평점 수정 | PATCH | `/members/{memberId}/ratings/{ratingId}` | `{ score, comment? }` | 200 `result`: null |
| 평점 삭제 | DELETE | `/members/{memberId}/ratings/{ratingId}` | 없음 | 204 본문 없음 |

입력 규칙

- email: 이메일 형식, 필수
- password: 대문자·소문자·숫자·특수문자(`!@#$%^&*`) 포함 8자 이상, bcrypt 해시(60자)로 저장
- nickname: 2자 이상 12자 이하
- movieId: 양의 정수(TMDB ID) / score: 1~5 정수 / comment: 500자 이하, 선택

오류 응답

| 상황 | 상태 | code | message |
| --- | --- | --- | --- |
| 입력값 검증 실패 | 400 | `COMMON4001` | 첫 번째 검증 실패 메시지 |
| 이메일 중복 | 409 | `MEMBER4091` | 이미 사용 중인 이메일입니다. |
| 닉네임 중복 | 409 | `MEMBER4092` | 이미 사용 중인 닉네임입니다. |
| 같은 영화에 평점 중복 | 409 | `RATING4091` | 이미 이 영화에 평점을 남기셨습니다. |
| 다른 회원의 평점 수정·삭제 | 403 | `COMMON403` | 본인이 작성한 평점만 수정하거나 삭제할 수 있습니다. |
| 없는 회원 / 없는 평점 | 404 | `COMMON404` | 존재하지 않는 유저입니다. / 존재하지 않는 평점입니다. |

### 핵심 코드

- 회원가입: [SignUpRequest](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/dto/SignUpRequest.java) · [MemberService.signUp](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/service/MemberService.java) · [MemberController](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/controller/MemberController.java) · [PasswordConfig](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/config/PasswordConfig.java)
- 평점 쓰기: [CreateRatingRequest](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/dto/CreateRatingRequest.java) · [UpdateRatingRequest](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/dto/UpdateRatingRequest.java) · [RatingService](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/service/RatingService.java) · [RatingController](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/controller/RatingController.java)
- 공통 응답·예외: [ApiResponse](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/dto/ApiResponse.java) · [ErrorCode](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/exception/ErrorCode.java) · [GlobalExceptionHandler](https://github.com/joshuasa0122/11th_UMC_PE_WEB_BACKEND/blob/week6-josh/src/main/java/com/umc/movie/controller/GlobalExceptionHandler.java)

### 확인 포인트

- **password가 응답에 없는가?** 응답은 `MemberResponse(memberId, email, nickname)`만 담습니다. DB에는 `$2a$`로 시작하는 60자 bcrypt 해시가 저장됩니다.
- **중복 평점이 DB에 저장되지 않는가?** 같은 평점 등록 요청 20개를 동시에 보내도 `rating`에는 1행만 남습니다. 나머지 19개는 409 `RATING4091`로 응답합니다.
- **DELETE가 204로 응답하는가?** 본문 없이 204 No Content로 응답하고, 다시 조회하면 목록에서 사라집니다.

### 선택 미션 — 중복 오류 구분과 DB 제약 위반 변환

이메일·닉네임·평점 중복을 `ErrorCode`로 나눠 각각 다른 code와 message로 응답합니다. 서비스의 사전 중복 체크(`CustomException`)와 DB UNIQUE 제약 위반(`DataIntegrityViolationException`)이 **같은 ErrorCode**로 변환되어, 화면은 어느 단계에서 막혔는지 몰라도 같은 응답을 받습니다.

사전 중복 체크만으로는 부족한 이유: `existsByEmail` 조회와 `save` 사이에는 틈이 있습니다. 같은 이메일로 요청 두 개가 거의 동시에 들어오면 둘 다 "없음"을 확인한 뒤 저장으로 넘어갑니다. 이때 먼저 커밋한 요청만 성공하고, 나머지는 DB의 UNIQUE 제약에 막혀 `Duplicate entry ... for key 'member.email'`가 발생합니다. 그래서 DB 제약을 마지막 안전망으로 두고, 그 예외도 전역 예외 처리에서 같은 형식으로 바꿉니다.

재현: `scripts/race.sh`로 같은 요청을 20개 동시에 보냅니다.

```bash
./scripts/race.sh /members '{"email":"race@example.com","password":"Umc!1234","nickname":"racer"}'
grep -c "Duplicate entry" <서버 로그>   # 사전 체크를 통과해 DB에서 막힌 요청 수
```

로컬 결과: 회원가입·평점 등록 모두 20개 중 201은 1개, 409는 19개였습니다. 그중 각각 9건은 서버 로그에 `Duplicate entry`가 찍혀 DB 제약에서 막혔지만, 응답은 `MEMBER4091` / `RATING4091`로 사전 체크와 같았습니다. DB에는 각각 1행만 저장됐습니다.

### ⚡ 트러블슈팅 — 동시 요청 테스트 뒤 memberId가 4 → 14 → 24로 건너뜀

**`이슈`**

👉 같은 회원가입 요청 20개를 동시에 보내는 테스트를 세 번 반복했다. 성공한 회원의 memberId가 4, 14, 24로 10씩 건너뛰었고, DB에도 4·14·24번 행만 있었다.

**`원인`**

👉 MySQL InnoDB는 INSERT를 실행할 때 AUTO_INCREMENT 값을 먼저 배정한다. UNIQUE 제약 위반으로 INSERT가 실패해도 그 번호는 돌려주지 않는다. MySQL 8의 기본값 `innodb_autoinc_lock_mode = 2`에서 실제로 확인했다. 사전 중복 체크를 통과해 DB까지 간 요청이 실패할 때마다 번호가 하나씩 소모된 것이다. 이 수치는 동시 요청 중 몇 개가 DB 제약에서 막혔는지 보여 주는 증거이기도 하다.

**`해결`**

👉 버그가 아니라 정상 동작이므로 코드는 바꾸지 않았다. 테스트 데이터를 지운 뒤 `ALTER TABLE member AUTO_INCREMENT = 4`로 번호를 되돌려 캡처용 데이터를 정리했다. ID는 행을 구분하는 용도로만 쓰고, 연속된 번호라고 가정하지 않는다.

**`배운 점`**

👉 AUTO_INCREMENT의 빈 번호는 실패한 INSERT나 롤백의 흔적이다. ID로 "가입자 수"나 "순서"를 계산하면 틀린다. 동시성 테스트 뒤에 번호가 비어 있다면, DB 제약까지 도달한 요청이 있었다는 신호로 읽을 수 있다.

**`참고 레퍼런스`**

- [MySQL 8.4 Reference — AUTO_INCREMENT Handling in InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html)
- [Spring Framework — DataIntegrityViolationException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/DataIntegrityViolationException.html)
