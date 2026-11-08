# UMC 영화 평점 서비스 백엔드 (Spring Boot)

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
