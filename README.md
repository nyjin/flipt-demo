# Flipt Feature Flag Demo

[Flipt](https://github.com/flipt-io/flipt) (v2, Git-native, OSS) 를 로컬에서 구동하고,
**Spring Boot 백엔드**가 컨트롤러 메서드에 붙인 `@FeatureFlag` 어노테이션만으로 특정
API를 동적으로 켜고 끄는 데모입니다.

- 플래그 평가는 [OpenFeature](https://openfeature.dev) `Client` 추상화로 수행합니다 — **기본은 Flipt
  client-side SDK(`flipt-client-java`)로 in-memory 평가**, `flipt.mode=ofrep` 로 **OFREP(서버 사이드)
  평가**로도 전환 가능합니다(아래 ["Flipt 평가 방식"](#flipt-평가-방식--in-memory기본--ofrep) 참고).
- Flipt v2 의 git-native **environments** 로 `local` + `dev` / `staging` / `prod` 를 구성합니다.
  - `local` — 이미지에 baked 된 **로컬 git 저장소**. UI 에서 자유롭게 토글(로컬 개발용).
  - `dev` / `staging` / `prod` — 원격 repo 의 **`release/*` 브랜치**를 추적(읽기전용·GitOps).
- 같은 백엔드가 활성 Spring 프로파일에 따라 다른 Flipt 환경을 평가합니다.
- 비교를 위해 [GrowthBook](https://www.growthbook.io) 을 **self-host**(+ MongoDB) 로 함께 띄우고,
  같은 플래그 키를 GrowthBook 네이티브 Java SDK 로 평가하는 `/api/growthbook/*` 엔드포인트를
  Flipt 의 `/api/demo/*` 와 1:1 로 제공합니다 — 아래 ["GrowthBook 비교"](#growthbook-비교) 참고.

## 아키텍처

```
                         ┌───────────────────────────────────────────────┐
  GET /api/demo/hello    │  Spring Boot backend (apps/backend)            │
  ────────────────────▶  │                                                │
                         │  DemoController                                │
                         │    @FeatureFlag("demo-api")  ◀── 어노테이션      │
                         │          │                                     │
                         │          ▼                                     │
                         │  FeatureFlagAspect (AOP)                       │
                         │    OpenFeature Client                          │
                         │      ├─ (기본) FliptInMemoryProvider            │
                         │      │     → flipt-client-java (in-memory)      │
                         │      └─ (flipt.mode=ofrep) OFREP provider       │
                         └──────────────────┬─────────────────────────────┘
                                            │ 기본 : 기동 시 스냅샷 fetch + 폴링(30s) → 이후 평가는 JVM 메모리
                                            │ ofrep: POST /ofrep/v1/evaluate/flags/{key} (평가마다 호출)
                                            │ X-Flipt-Environment: local|dev|staging|prod
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
├── docker-compose.yml          # Flipt + GrowthBook(+ MongoDB) + 백엔드 로컬 오케스트레이션
├── .env.example                # GrowthBook SDK 키/시크릿 (복사 → .env)
├── config/                     # 선언형 플래그 (git-native), 환경별 디렉터리
│   ├── dev/default/features.yaml
│   ├── staging/default/features.yaml
│   └── prod/default/features.yaml
├── apps/
│   ├── flipt/                  # Flipt v2 서버
│   │   ├── Dockerfile          # local 환경용 config/(플래그) baked + 서버 설정 주입 (컨텍스트=레포 루트)
│   │   └── config/config.yml   # 환경/스토리지 정의 (local=baked, dev/staging/prod=원격 release/*)
│   └── backend/                # Spring Boot (Gradle Kotlin DSL, Java 25)
│       ├── src/main/java/com/example/fliptdemo/
│       │   ├── config/         # FliptProperties/OpenFeatureConfig/FliptInMemoryProvider(Flipt), GrowthBookProperties/GrowthBookConfig(GrowthBook)
│       │   ├── featureflag/    # @FeatureFlag·@GrowthBookFlag 와 각 Aspect, 예외
│       │   └── web/            # DemoController(Flipt), GrowthBookController(GrowthBook), GlobalExceptionHandler
│       └── src/main/resources/ # application{,-local,-dev,-staging,-prod}.yml
└── .github/workflows/          # backend-ci, flipt-validate

# GrowthBook 은 선언형 git config 가 없습니다 — 플래그/SDK 키는 UI 에서 생성(MongoDB 저장)합니다.
```

> 플래그 정의(`config/`)는 Flipt 앱 폴더와 분리되어 레포 최상위에 있습니다. `main` 에는 세 환경
> 폴더가 모두 있지만, **실제 dev/staging/prod 가 서빙하는 소스는 각 `release/*` 브랜치**입니다
> (아래 "환경" 참고). Flipt 서버 설정 `apps/flipt/config/config.yml` 과는 별개입니다.

## 요구 사항

- Docker / Docker Compose (Flipt 실행 및 빌드)
- (선택) 로컬에서 백엔드를 직접 구동할 경우 JDK 25 — 없어도 Gradle 툴체인이 자동 설치합니다.

## 빠른 시작

```bash
# Flipt + GrowthBook(+ MongoDB) + 백엔드를 함께 기동 (백엔드는 기본 dev 환경)
docker compose up --build

# Flipt UI:       http://localhost:8080
# GrowthBook UI:  http://localhost:3000   (API: http://localhost:3100)
# 백엔드 API:     http://localhost:8081/api/...
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

`FeatureFlagAspect` 가 메서드 실행 전에 OpenFeature `Client` 로 플래그를 평가합니다(기본
in-memory, `flipt.mode` 로 OFREP 전환 — 아래 ["Flipt 평가 방식"](#flipt-평가-방식--in-memory기본--ofrep) 참고).

- 플래그 **ON** → 메서드 정상 실행 → `200 OK`
- 플래그 **OFF** → `FeatureDisabledException` → `GlobalExceptionHandler` 가 `404 Not Found` 로 매핑
  (숨김 처리. 403/503 으로 바꾸려면 `GlobalExceptionHandler` 수정)
- Flipt 불가/플래그 없음 → 어노테이션의 `fallback`(기본 `false`) 사용

## Flipt 평가 방식 — in-memory(기본) / OFREP

`@FeatureFlag` 는 항상 같은 OpenFeature `Client` 를 쓰고, 백엔드는 `flipt.mode` 로 그 뒤의
provider 만 바꿉니다(어노테이션·컨트롤러·테스트 코드는 그대로).

| `flipt.mode` | 평가 위치 | 동작 | 특징 |
| --- | --- | --- | --- |
| `in-memory` (기본) | 클라이언트 사이드 | `flipt-client-java`(Rust 코어 FFI)가 기동 시 스냅샷을 받아 **JVM 메모리에서 로컬 평가**(~0.1ms). 폴링(기본 30s) 또는 스트리밍으로 동기화 | 평가 시 네트워크 0, 서버가 잠시 끊겨도 마지막 스냅샷으로 평가 지속 |
| `ofrep` | 서버 사이드 | OpenFeature **OFREP** provider 가 평가마다 Flipt 서버를 호출 | OpenFeature 표준(OFREP) 호환을 그대로 사용 |

```bash
# 기본은 in-memory — 추가 설정 없이 그대로 기동
docker compose up --build backend

# OFREP(서버 사이드) 평가로 전환
FLIPT_MODE=ofrep docker compose up --build backend
# 로컬 직접 실행이면:  FLIPT_MODE=ofrep ./gradlew bootRun
```

관련 설정(`application.yml`, 모두 환경변수로 오버라이드 가능):

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `flipt.mode` | `in-memory` | `in-memory` 또는 `ofrep` (`FLIPT_MODE`) |
| `flipt.sync-mode` | `polling` | in-memory 동기화: `polling` 또는 `streaming`(SSE) (`FLIPT_SYNC_MODE`) |
| `flipt.update-interval-seconds` | `30` | 폴링 주기(초) (`FLIPT_UPDATE_INTERVAL`) |
| `flipt.url` | `http://localhost:8080` | Flipt 서버 주소 — **두 모드 모두** 사용(in-memory도 스냅샷 fetch) (`FLIPT_URL`) |

> in-memory 모드도 Flipt 서버가 필요합니다(스냅샷을 받아옴). 평가만 백엔드 메모리에서 일어나므로,
> 스냅샷을 한 번 받은 뒤에는 Flipt 서버가 잠시 끊겨도 평가가 계속됩니다(폴링 갱신만 지연).

## 시나리오: Multivariate · 세그먼트 타겟팅 · 퍼센트 롤아웃

단순 on/off(`@FeatureFlag`) 외에, 세 가지 고급 시나리오를 **평가 결과를 그대로 반환**하는 엔드포인트로
시연합니다. 사용자 컨텍스트는 HTTP 헤더로 전달합니다.

| 헤더 | 용도 | 기본값 |
| --- | --- | --- |
| `X-User-Id` | 퍼센트 롤아웃 버킷팅 엔티티(같은 id = 일관된 결과) | `anonymous` |
| `X-User-Tier` | 세그먼트 타겟팅 속성(예: `premium`) | (없음) |
| `X-User-Country` | 추가 타겟팅 속성 | (없음) |

Flipt(`/api/demo/*`)와 GrowthBook(`/api/growthbook/*`)이 1:1로 대응합니다. 응답은
`{flag, enabled|variant, reason, userId, attributes, provider}` 형태입니다.

| 시나리오 | 플래그 | Flipt | GrowthBook |
| --- | --- | --- | --- |
| Multivariate | `ui-theme` | `GET /api/demo/variant` | `GET /api/growthbook/variant` |
| 세그먼트 타겟팅 | `premium-feature` | `GET /api/demo/targeted` | `GET /api/growthbook/targeted` |
| 퍼센트 롤아웃 | `gradual-rollout` | `GET /api/demo/rollout` | `GET /api/growthbook/rollout` |

```bash
# Multivariate — userId 해시로 control/treatment 분배 (dev=50/50)
curl -s localhost:8081/api/demo/variant -H 'X-User-Id: u1'
# => {"provider":"flipt","flag":"ui-theme","variant":"treatment","reason":"MATCH_EVALUATION_REASON",...}

# 세그먼트 타겟팅 — tier=premium 만 on
curl -s localhost:8081/api/demo/targeted -H 'X-User-Tier: premium'   # enabled=true
curl -s localhost:8081/api/demo/targeted -H 'X-User-Tier: free'      # enabled=false

# 퍼센트 롤아웃 — 같은 userId 는 항상 동일, 전체적으로 ~50%(dev)
for u in $(seq 1 20); do curl -s localhost:8081/api/demo/rollout -H "X-User-Id: user-$u"; echo; done
```

> 환경별 비중: variant `treatment` 와 롤아웃 %가 **dev > staging > prod**로 줄어듭니다
> (variant treatment 50/20/10%, rollout 50/20/10%) — `config/<env>/default/features.yaml` 참고.
> 이 데모 백엔드는 기본 `dev` 프로파일이 원격 `release/dev`를 평가하므로, 로컬에서 새 플래그를
> 바로 보려면 `SPRING_PROFILES_ACTIVE=local`(baked `config/dev`)로 실행하세요.

### Flipt 정의 (git-native, 즉시 동작)

`config/<env>/default/features.yaml`에 선언형으로 존재합니다 — `ui-theme`(`VARIANT_FLAG_TYPE`,
variants+rules+distributions), `premium-feature`(`rollouts` segment `premium-tier`),
`gradual-rollout`(`rollouts` threshold). 추가 셋업 없이 동작합니다. boolean 타겟팅/롤아웃 플래그는
기본값을 off(`enabled: false`)로 두고 룰이 on으로 올립니다.

### GrowthBook 정의 (UI → MongoDB, 수동 셋업)

GrowthBook은 git config가 없으므로 **동일 키를 UI에서 생성**해야 시연됩니다(키 없으면 fallback=off/control):

1. Features에서 `ui-theme`(String, control/treatment), `premium-feature`(Boolean), `gradual-rollout`(Boolean) 생성
2. `premium-feature` 타겟팅 룰: `tier == premium → ON`
3. `gradual-rollout` percentage rollout 50% (hash attribute `id`)
4. `ui-theme` experiment/rollout으로 control/treatment 분배
5. SDK 키 주입(아래 "GrowthBook 셋업") 후 `/api/growthbook/{variant,targeted,rollout}`로 비교

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

Flipt 서버의 OFREP 엔드포인트는 백엔드의 `flipt.mode` 와 무관하게 항상 열려 있습니다. SDK 없이
서버 평가 동작을 확인하려면:

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
- `FliptInMemoryProviderTest` — in-memory provider 가 SDK 결과를 반영하고, 클라이언트 부재/예외 시 fallback

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

## GrowthBook 비교

같은 백엔드가 **Flipt** 와 **GrowthBook** 두 provider 를 각각 평가하도록 구성되어 있습니다.
엔드포인트가 1:1 로 대응되어 동일 시나리오를 나란히 비교할 수 있습니다.

| 시나리오 | Flipt | GrowthBook |
| --- | --- | --- |
| 기본 API (`demo-api`) | `GET /api/demo/hello` | `GET /api/growthbook/hello` |
| 베타 (`beta-feature`) | `GET /api/demo/beta` | `GET /api/growthbook/beta` |
| 대조군(게이팅 없음) | `GET /api/health` | `GET /api/growthbook/health` |

- 평가 방식: Flipt 는 OpenFeature `Client`(**기본 in-memory `flipt-client-java`**, `flipt.mode=ofrep`
  로 OFREP 전환), GrowthBook 은 **네이티브 Java SDK**(`GrowthBookClient`). **둘 다 기본은 클라이언트
  사이드 in-memory 평가**라 호출당 네트워크가 없습니다.
- 게이팅 동작은 동일합니다 — 플래그 **OFF → 404**, 평가 불가/미설정 → 어노테이션 `fallback`(기본 off).
- **핵심 차이**: Flipt 는 플래그가 git(`config/`)에 선언형으로 존재하지만, **GrowthBook 은 git
  config 가 없어 플래그·SDK 키를 UI 에서 만들어야 합니다**(MongoDB 저장). 그래서 GrowthBook 은
  MongoDB 가 필수이고, SDK 키는 아래처럼 수동 발급 → `.env` 주입으로 연결합니다.

### GrowthBook 셋업 (최초 1회)

```bash
cp .env.example .env          # 키는 비워둔 채 시작해도 백엔드는 기동됨
docker compose up --build     # mongo → growthbook → flipt → backend
```

1. **계정/조직 생성** — http://localhost:3000 접속 후 최초 관리자 계정을 만듭니다(MongoDB 에 저장).
2. **Feature 생성** — Features 메뉴에서 Flipt 와 **동일한 키**로 boolean feature 를 만듭니다.
   - `demo-api` → 기본값 **on**
   - `beta-feature` → 기본값 **on**(끄고 켜며 비교)
3. **SDK Connection 생성** — Settings → SDK Connections → 새로 생성 후 **Client Key** 복사.
4. **키 주입** — `.env` 의 `GROWTHBOOK_CLIENT_KEY=` 에 붙여넣고 백엔드만 재기동합니다.

   ```bash
   docker compose up -d --build backend
   ```

> 백엔드(컨테이너)는 GrowthBook API 를 `http://growthbook:3100` 으로 호출합니다(compose 네트워크
> 내부 주소, `GROWTHBOOK_API_HOST`). UI 가 보여주는 `http://localhost:3100` 대신 이 값을 씁니다.
> 키가 비어 있으면 `/api/growthbook/*` 는 fallback(off)으로 404 를 반환하지만 백엔드는 정상 기동합니다.

### 비교 호출

```bash
# 두 provider 모두 demo-api 가 on → 둘 다 200
curl -i http://localhost:8081/api/demo/hello
curl -i http://localhost:8081/api/growthbook/hello

# GrowthBook UI 에서 beta-feature 를 off 로 바꾸면 → GrowthBook 만 404, Flipt 는 그대로
curl -i http://localhost:8081/api/demo/beta        # Flipt
curl -i http://localhost:8081/api/growthbook/beta  # GrowthBook
```

> 로컬에서 백엔드를 직접 실행(`./gradlew bootRun`)할 때는 GrowthBook API 가 `http://localhost:3100`
> 이므로 `GROWTHBOOK_API_HOST` 를 그대로 두고 `GROWTHBOOK_CLIENT_KEY` 만 환경변수로 주면 됩니다.

## CI (GitHub Actions)

- **backend-ci** — `apps/backend` 변경 시 JDK 25 로 `./gradlew build`(컴파일 + 테스트).
- **flipt-validate** — `config/**`·`apps/flipt/**` 변경 시(`main` 및 `release/*` 브랜치) `flipt validate` 로 플래그 YAML 검증 + Flipt 이미지 빌드.
