# Flipt Feature Flag Demo

[Flipt](https://github.com/flipt-io/flipt) (v2, Git-native, OSS) 를 로컬에서 구동하고,
**Spring Boot 백엔드**가 컨트롤러 메서드에 붙인 `@FeatureFlag` 어노테이션만으로 특정
API를 동적으로 켜고 끄는 데모입니다.

- 플래그 평가는 [OpenFeature](https://openfeature.dev) 표준 SDK + **OFREP** provider 로 수행합니다.
- `dev` / `preview` / `prod` 를 Flipt v2 의 git-native **environments** 로 구성합니다.
- 같은 백엔드가 활성 Spring 프로파일에 따라 다른 Flipt 환경을 평가합니다.

## 아키텍처

```
                         ┌─────────────────────────────────────────┐
  GET /api/demo/hello    │  Spring Boot backend (apps/backend)      │
  ────────────────────▶  │                                          │
                         │  DemoController                          │
                         │    @FeatureFlag("demo-api")  ◀── 어노테이션 │
                         │          │                               │
                         │          ▼                               │
                         │  FeatureFlagAspect (AOP)                 │
                         │    OpenFeature Client → OFREP provider   │
                         └──────────────────┬───────────────────────┘
                                            │ POST /ofrep/v1/evaluate/flags/{key}
                                            │ X-Flipt-Environment: dev|preview|prod
                                            │ X-Flipt-Namespace: default
                                            ▼
                         ┌─────────────────────────────────────────┐
                         │  Flipt v2 server (apps/flipt)            │
                         │  environments: dev / preview / prod      │
                         │  storage: local git repo (/flags)        │
                         └─────────────────────────────────────────┘

플래그 ON  → 메서드 실행 → 200 OK
플래그 OFF → FeatureDisabledException → 404 Not Found
```

## 디렉터리 구조

```
flipt-demo/
├── docker-compose.yml          # Flipt + 백엔드 로컬 오케스트레이션
├── apps/
│   ├── flipt/                  # Flipt v2 서버
│   │   ├── Dockerfile          # flipt:v2 이미지에 config/flags 주입
│   │   ├── config/config.yml   # dev/preview/prod 환경 + local 스토리지
│   │   └── flags/              # 선언형 플래그 (git-native)
│   │       ├── dev/default/features.yaml
│   │       ├── preview/default/features.yaml
│   │       └── prod/default/features.yaml
│   └── backend/                # Spring Boot (Gradle Kotlin DSL, Java 21)
│       ├── src/main/java/com/example/fliptdemo/
│       │   ├── config/         # FliptProperties, OpenFeatureConfig
│       │   ├── featureflag/    # @FeatureFlag, FeatureFlagAspect, 예외
│       │   └── web/            # DemoController, GlobalExceptionHandler
│       └── src/main/resources/ # application{,-dev,-preview,-prod}.yml
└── .github/workflows/          # backend-ci, flipt-validate
```

## 요구 사항

- Docker / Docker Compose (Flipt 실행 및 빌드)
- (선택) 로컬에서 백엔드를 직접 구동할 경우 JDK 21 — 없어도 Gradle 툴체인이 자동 설치합니다.

## 빠른 시작

```bash
# Flipt + 백엔드를 함께 기동 (백엔드는 기본 dev 환경)
docker compose up --build

# Flipt UI:    http://localhost:8080
# 백엔드 API:  http://localhost:8081/api/...
```

API 호출 예시:

```bash
# demo-api 플래그가 켜져 있으면 200
curl -i http://localhost:8081/api/demo/hello

# beta-feature: dev=ON, preview/prod=OFF
curl -i http://localhost:8081/api/demo/beta

# 플래그와 무관하게 항상 동작 (대조군)
curl -i http://localhost:8081/api/health
```

다른 환경으로 백엔드 실행:

```bash
SPRING_PROFILES_ACTIVE=preview docker compose up --build backend
SPRING_PROFILES_ACTIVE=prod    docker compose up --build backend
```

## 컨트롤러 메서드 on/off 동작 방식

컨트롤러 메서드에 `@FeatureFlag("<flag-key>")` 만 붙이면 됩니다.

```java
@GetMapping("/demo/hello")
@FeatureFlag("demo-api")          // 이 플래그가 OFF 면 메서드가 실행되지 않음
public Map<String, String> hello() { ... }
```

`FeatureFlagAspect` 가 메서드 실행 전에 OpenFeature(→ Flipt OFREP)로 플래그를 평가합니다.

- 플래그 **ON** → 메서드 정상 실행 → `200 OK`
- 플래그 **OFF** → `FeatureDisabledException` → `GlobalExceptionHandler` 가 `404 Not Found` 로 매핑
  (숨김 처리. 403/503 으로 바꾸려면 `GlobalExceptionHandler` 수정)
- Flipt 불가/플래그 없음 → 어노테이션의 `fallback`(기본 `false`) 사용

## 플래그 토글 방법

### 방법 1 — Flipt UI (라이브, 권장)

1. http://localhost:8080 접속
2. 상단에서 환경(dev/preview/prod) 선택
3. `demo-api` 또는 `beta-feature` 플래그의 Enabled 토글
4. 즉시 반영 — 다시 `curl` 호출 시 200 ↔ 404 가 바뀜

> Flipt UI 변경은 내부 git 저장소에 커밋되어 즉시 서빙됩니다.

### 방법 2 — 선언형 파일 수정 (GitOps)

`apps/flipt/flags/<env>/default/features.yaml` 의 `enabled` 값을 바꾼 뒤 Flipt 를 재시작:

```bash
# 예: dev 의 demo-api 를 끄기 → enabled: false 로 수정 후
docker compose up -d --build flipt
```

> local 스토리지 백엔드는 시작 시점의 git 커밋을 읽으므로, 파일 수정 후에는 재빌드/재시작이
> 필요합니다(또는 위 UI 방식 사용).

## 환경(dev / preview / prod)

`apps/flipt/config/config.yml` 에서 세 환경을 하나의 git 저장소(`/flags`) 안의 서로 다른
디렉터리로 매핑합니다(Flipt v2 의 "environment per directory" 모델).

| 환경    | Flipt directory | 백엔드 프로파일 | beta-feature 시드 |
| ------- | --------------- | --------------- | ----------------- |
| dev     | `dev/`          | `dev` (기본)    | ON                |
| preview | `preview/`      | `preview`       | OFF               |
| prod    | `prod/`         | `prod`          | OFF               |

백엔드는 `X-Flipt-Environment` 헤더로 환경을 선택합니다(프로파일별 `application-*.yml` 참고).

## 백엔드를 로컬에서 직접 실행

```bash
# Flipt 만 컨테이너로 띄우고
docker compose up --build flipt

# 백엔드는 Gradle 로 실행 (기본 dev)
cd apps/backend
./gradlew bootRun

# 다른 환경
SPRING_PROFILES_ACTIVE=preview ./gradlew bootRun

# 테스트
./gradlew test
```

## Flipt OFREP API 직접 호출

SDK 없이 평가 동작을 확인하려면:

```bash
curl -s -X POST http://localhost:8080/ofrep/v1/evaluate/flags/demo-api \
  -H 'Content-Type: application/json' \
  -H 'X-Flipt-Environment: dev' \
  -H 'X-Flipt-Namespace: default' \
  -d '{}'
# => {"key":"demo-api","reason":"DEFAULT","variant":"true","value":true}
```

## 테스트

### 백엔드 테스트

```bash
cd apps/backend
./gradlew test
```

- `FeatureFlagAspectTest` — 플래그 ON 이면 메서드 실행, OFF 면 `FeatureDisabledException`
- `DemoControllerTest` — 플래그 ON→200, OFF→404(`$.flag` 확인), `/api/health` 는 항상 200

### git-native 동작 검증

Flipt v2 의 `local` 스토리지는 `/flags` 를 **실제 git 저장소**로 다룹니다
("플래그 = 선언형 파일", "변경 = git 커밋"). 아래처럼 단계별로 검증할 수 있습니다.

| 검증 대상 | 방법 |
| --- | --- |
| 선언형 플래그 유효성 | `flipt validate` (CI 에서도 사용) |
| 저장소가 진짜 git repo 인지 | 컨테이너의 `/flags/.git` 존재 확인 |
| 환경별 디렉터리 격리 | env 별 OFREP 평가 비교 |
| **커밋이 플래그 상태를 결정** | 커밋 → flipt 재시작 → 재평가 |
| 롤백 | `git revert` → 재시작 → 재평가 |

**1) 선언형 검증 + 저장소/격리 확인**

```bash
# 플래그 YAML 검증
docker run --rm -v "$PWD/apps/flipt/flags:/flags" -w /flags \
  docker.flipt.io/flipt/flipt:v2 /flipt validate

# Flipt 기동 후, /flags 가 git repo 인지 + 환경별 격리 확인
docker compose up -d --build flipt
docker compose exec flipt ls -la /flags/.git          # objects/refs/HEAD 등 존재
for env in dev preview prod; do
  printf '%-8s ' "$env:"
  curl -s -X POST http://localhost:8080/ofrep/v1/evaluate/flags/beta-feature \
    -H 'Content-Type: application/json' \
    -H "X-Flipt-Environment: $env" -H 'X-Flipt-Namespace: default' -d '{}'; echo
done
# dev => value:true,  preview/prod => value:false
```

**2) GitOps 라운드트립 — "git 커밋이 플래그 상태를 결정한다"**

데모는 플래그를 이미지에 굽기 때문에, 라운드트립은 별도 git repo 를 bind-mount 해
검증합니다(기존 스택은 그대로 둔 채 8090 포트 사용):

```bash
# 1. 호스트에 git repo 생성 (flipt 유저 uid 100 이 읽도록 권한 부여)
REPO="$(mktemp -d)/repo"; mkdir -p "$REPO"
cp -R apps/flipt/flags/. "$REPO/"
git -C "$REPO" init -q -b main && git -C "$REPO" add . && git -C "$REPO" commit -qm seed
chmod -R a+rwX "$REPO"

# 2. bind-mount 로 flipt 기동
docker run -d --name flipt-gitnative -p 8090:8080 \
  -v "$REPO:/flags" \
  -v "$PWD/apps/flipt/config/config.yml:/etc/flipt/config/default.yml:ro" \
  docker.flipt.io/flipt/flipt:v2

eval_demo() { curl -s -X POST http://localhost:8090/ofrep/v1/evaluate/flags/demo-api \
  -H 'Content-Type: application/json' \
  -H 'X-Flipt-Environment: dev' -H 'X-Flipt-Namespace: default' -d '{}'; echo; }

sleep 3; eval_demo            # => value:true

# 3. demo-api 만 끄도록 커밋 후 재시작 → 평가가 false 로 바뀜 (커밋이 상태를 결정)
sed -i '' '/key: demo-api/,/enabled:/ s/enabled: true/enabled: false/' \
  "$REPO/dev/default/features.yaml"        # Linux 는 sed -i (빈 따옴표 제거)
git -C "$REPO" commit -aqm "disable demo-api in dev"
docker restart flipt-gitnative; sleep 3
eval_demo                     # => value:false

# 4. 롤백: git revert 로 되돌리면 다시 true
git -C "$REPO" revert --no-edit HEAD
docker restart flipt-gitnative; sleep 3
eval_demo                     # => value:true

# 5. 정리
docker rm -f flipt-gitnative; rm -rf "$(dirname "$REPO")"
```

> `local` 백엔드는 **시작 시점의 git HEAD** 를 읽으므로, 외부에서 만든 커밋을 반영하려면
> 재시작이 필요합니다. 반대로 **Flipt UI 토글**은 즉시 서빙되며 내부 git 에 자동
> 커밋됩니다(write→commit).

## CI (GitHub Actions)

- **backend-ci** — `apps/backend` 변경 시 JDK 21 로 `./gradlew build`(컴파일 + 테스트).
- **flipt-validate** — `apps/flipt` 변경 시 `flipt validate` 로 플래그 YAML 검증 + Flipt 이미지 빌드.
