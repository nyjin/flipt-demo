# Flipt Feature Flag Demo

[Flipt](https://github.com/flipt-io/flipt) (v2, Git-native, OSS) 를 로컬에서 구동하고,
**Spring Boot 백엔드**가 컨트롤러 메서드에 붙인 `@FeatureFlag` 어노테이션만으로 특정
API를 동적으로 켜고 끄는 데모입니다.

- 플래그 평가는 [OpenFeature](https://openfeature.dev) 표준 SDK + **OFREP** provider 로 수행합니다.
- Flipt v2 의 git-native **environments** 로 `local` + `dev` / `staging` / `prod` 를 구성합니다.
  - `local` — 이미지에 baked 된 **로컬 git 저장소**. UI 에서 자유롭게 토글(로컬 개발용).
  - `dev` / `staging` / `prod` — 원격 repo 의 **`release/*` 브랜치**를 추적(읽기전용·GitOps).
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
                                            │ X-Flipt-Environment: local|dev|staging|prod
                                            │ X-Flipt-Namespace: default
                                            ▼
                         ┌─────────────────────────────────────────────────────┐
                         │  Flipt v2 server (apps/flipt)                        │
                         │  local    → baked local git repo (/flags)            │
                         │  dev       → remote git  release/dev     (memory)    │
                         │  staging   → remote git  release/staging (memory)    │
                         │  prod      → remote git  release/prod    (memory)    │
                         └───────────────────────────┬─────────────────────────┘
                                                     │ clone + poll (30s)
                                                     ▼
                                   github.com/nyjin/flipt-demo  (release/* 브랜치)

플래그 ON  → 메서드 실행 → 200 OK
플래그 OFF → FeatureDisabledException → 404 Not Found
```

## 디렉터리 구조

```
flipt-demo/
├── docker-compose.yml          # Flipt + 백엔드 로컬 오케스트레이션
├── config/                     # 선언형 플래그 (git-native), 환경별 디렉터리
│   ├── dev/default/features.yaml
│   ├── staging/default/features.yaml
│   └── prod/default/features.yaml
├── apps/
│   ├── flipt/                  # Flipt v2 서버
│   │   ├── Dockerfile          # local 환경용 config/(플래그) baked + 서버 설정 주입 (컨텍스트=레포 루트)
│   │   └── config/config.yml   # 환경/스토리지 정의 (local=baked, dev/staging/prod=원격 release/*)
│   └── backend/                # Spring Boot (Gradle Kotlin DSL, Java 21)
│       ├── src/main/java/com/example/fliptdemo/
│       │   ├── config/         # FliptProperties, OpenFeatureConfig
│       │   ├── featureflag/    # @FeatureFlag, FeatureFlagAspect, 예외
│       │   └── web/            # DemoController, GlobalExceptionHandler
│       └── src/main/resources/ # application{,-local,-dev,-staging,-prod}.yml
└── .github/workflows/          # backend-ci, flipt-validate
```

> 플래그 정의(`config/`)는 Flipt 앱 폴더와 분리되어 레포 최상위에 있습니다. `main` 에는 세 환경
> 폴더가 모두 있지만, **실제 dev/staging/prod 가 서빙하는 소스는 각 `release/*` 브랜치**입니다
> (아래 "환경" 참고). Flipt 서버 설정 `apps/flipt/config/config.yml` 과는 별개입니다.

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

# beta-feature: dev=ON, staging/prod=OFF
curl -i http://localhost:8081/api/demo/beta

# 플래그와 무관하게 항상 동작 (대조군)
curl -i http://localhost:8081/api/health
```

다른 환경으로 백엔드 실행 (해당 `release/*` 브랜치가 원격에 있어야 함):

```bash
SPRING_PROFILES_ACTIVE=staging docker compose up --build backend
SPRING_PROFILES_ACTIVE=prod     docker compose up --build backend

# 네트워크 없이 로컬 플래그만으로 개발하려면 local 환경
SPRING_PROFILES_ACTIVE=local   docker compose up --build backend
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

환경에 따라 방법이 다릅니다 — `local` 은 자유 편집, `dev`/`staging`/`prod` 는 GitOps.

### local 환경 — Flipt UI (라이브)

1. http://localhost:8080 접속, 상단에서 **`local`** 환경 선택
2. `demo-api` 또는 `beta-feature` 의 Enabled 토글 → 즉시 반영(다시 `curl` 시 200 ↔ 404)

> `local` 은 쓰기 가능한 baked git 저장소라 UI 변경이 즉시 커밋·서빙됩니다. 파일을 직접
> 고쳤다면 `docker compose up -d --build flipt` 로 재빌드하세요.

### dev / staging / prod — GitOps (`release/*` 브랜치 push)

이 세 환경은 원격 `release/*` 브랜치를 추적하는 **읽기전용**입니다(서버 UI 토글 불가). 플래그를
바꾸려면 해당 브랜치의 `config/<env>/default/features.yaml` 를 수정해 push 하면, Flipt 가
`poll_interval`(30s) 내에 **재시작 없이** 가져옵니다.

```bash
# 예: dev 환경의 beta-feature 끄기
git switch release/dev
# config/dev/default/features.yaml 에서 beta-feature 의 enabled: false 로 수정
git commit -am "dev: disable beta-feature" && git push

# 약 30초 내 자동 반영 (flipt 재시작 불필요)
curl -i http://localhost:8081/api/demo/beta
```

> **승급(promotion)** 은 같은 변경을 상위 환경 브랜치로 올리는 것입니다:
> `release/dev` → `release/staging` → `release/prod`. 변경 이력·리뷰(PR)·롤백(`git revert`)이
> 모두 git 에 남습니다. 읽기전용이라 자격증명 없이도 안전합니다.

## 환경(local / dev / staging / prod)

`apps/flipt/config/config.yml` 에서 환경마다 **스토리지**를 다르게 매핑합니다. `local` 은
이미지에 baked 된 로컬 git 저장소를, `dev`/`staging`/`prod` 는 원격 repo 의 `release/*`
브랜치를 각각 추적합니다(모두 `directory: config/<env>` 로 폴더 매핑).

| 환경    | 스토리지 소스                         | backend  | 백엔드 프로파일 | directory        |
| ------- | ------------------------------------- | -------- | --------------- | ---------------- |
| local   | baked 로컬 git (`/flags`)             | `local`  | `local`         | `config/dev`     |
| dev     | 원격 `release/dev` 브랜치             | `memory` | `dev` (기본)    | `config/dev`     |
| staging | 원격 `release/staging` 브랜치         | `memory` | `staging`       | `config/staging` |
| prod    | 원격 `release/prod` 브랜치            | `memory` | `prod`          | `config/prod`    |

- `local` 은 `default: true` — `X-Flipt-Environment` 헤더가 없으면(또는 오프라인) 이 환경으로 평가됩니다.
- `dev`/`staging`/`prod` 는 `type: memory` 라 재시작마다 원격에서 fresh 하게 clone 되며 서버 UI 로는 수정 불가(GitOps 전용)입니다.
- 백엔드는 `X-Flipt-Environment` 헤더로 환경을 선택합니다(프로파일별 `application-*.yml` 참고).

## 백엔드를 로컬에서 직접 실행

```bash
# Flipt 만 컨테이너로 띄우고
docker compose up --build flipt

# 백엔드는 Gradle 로 실행 (기본 dev)
cd apps/backend
./gradlew bootRun

# 다른 환경 (staging/prod 는 해당 release/* 브랜치 필요, local 은 오프라인 가능)
SPRING_PROFILES_ACTIVE=local   ./gradlew bootRun
SPRING_PROFILES_ACTIVE=staging ./gradlew bootRun

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

Flipt v2 는 모든 스토리지가 git 기반입니다. `local` 은 이미지에 baked 된 git 저장소를,
`dev`/`staging`/`prod` 는 원격 `release/*` 브랜치를 in-memory 로 clone 해 서빙합니다.

| 검증 대상 | 방법 |
| --- | --- |
| 선언형 플래그 유효성 | `flipt validate` (CI 에서도 사용) |
| `local` 이 git repo 인지 | 컨테이너의 `/flags/.git` 존재 확인(추적 파일은 `config/<env>/...`) |
| 환경별 평가 | `local`/`dev`/`staging`/`prod` 별 OFREP 비교 |
| **원격 브랜치 = 단일 소스** | `release/<env>` push → poll 내 **재시작 없이** 반영 |
| 롤백 | `git revert` 후 push → 자동 반영 |

**1) 선언형 검증 + 환경별 평가**

```bash
# 플래그 YAML 검증
docker run --rm -v "$PWD/config:/flags" -w /flags \
  docker.flipt.io/flipt/flipt:v2 /flipt validate

# Flipt 기동 후 환경별 평가 (dev/staging/prod 는 release/* 브랜치가 원격에 있어야 함)
docker compose up -d --build flipt
for env in local dev staging prod; do
  printf '%-8s ' "$env:"
  curl -s -X POST http://localhost:8080/ofrep/v1/evaluate/flags/beta-feature \
    -H 'Content-Type: application/json' \
    -H "X-Flipt-Environment: $env" -H 'X-Flipt-Namespace: default' -d '{}'; echo
done
# local/dev => value:true,  staging/prod => value:false
```

**2) 원격 GitOps 트리거 — "`release/<env>` push 가 단일 소스" (flipt 재시작 불필요)**

```bash
eval_dev() { curl -s -X POST http://localhost:8080/ofrep/v1/evaluate/flags/beta-feature \
  -H 'Content-Type: application/json' \
  -H 'X-Flipt-Environment: dev' -H 'X-Flipt-Namespace: default' -d '{}'; echo; }

eval_dev                       # => value:true

# release/dev 브랜치에서 beta-feature 끄고 push
git switch release/dev
sed -i '' '/key: beta-feature/,/enabled:/ s/enabled: true/enabled: false/' \
  config/dev/default/features.yaml          # Linux 는 sed -i (빈 따옴표 제거)
git commit -am "dev: disable beta-feature" && git push
git switch -                                 # 원래 브랜치로 복귀

# poll_interval(30s) 대기 후 재평가 — flipt 재시작 없이 false 로 바뀜
sleep 35; eval_dev             # => value:false

# 롤백: 되돌려 push 하면 다시 true (poll 후)
git switch release/dev && git revert --no-edit HEAD && git push && git switch -
sleep 35; eval_dev             # => value:true
```

> `local`(baked) 은 시작 시점의 git HEAD 를 읽어 파일 수정 시 재빌드가 필요하지만, 원격
> 환경은 `poll_interval` 마다 브랜치를 다시 가져오므로 **push 만으로 자동 반영**됩니다. 서버
> UI 로는 원격 환경을 수정할 수 없어(자격증명 없음) git 이 단일 소스로 유지됩니다.

## CI (GitHub Actions)

- **backend-ci** — `apps/backend` 변경 시 JDK 21 로 `./gradlew build`(컴파일 + 테스트).
- **flipt-validate** — `config/**`·`apps/flipt/**` 변경 시(`main` 및 `release/*` 브랜치) `flipt validate` 로 플래그 YAML 검증 + Flipt 이미지 빌드.
