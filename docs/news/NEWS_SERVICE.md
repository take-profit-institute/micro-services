# News Service 운영 문서

![News architecture overview](./assets/news-architecture-overview.svg)

## 1. 목적과 현재 전제

`news-service`는 종목별 최신 뉴스를 수집하고 조회하는 서비스다. 현재 구현은 다음 전제를 가진다.

- gRPC 조회 API는 DB에 저장된 뉴스만 반환한다.
- 뉴스 수집은 Scheduler가 수행한다.
- 수집 시 종목 정보는 `stock-service` gRPC로 조회한다.
- 외부 뉴스 검색은 Naver News REST API를 사용한다.
- `news-service`는 자체 DB schema `news`를 사용한다.
- Kafka, Redis, Outbox, 멱등성 레코드는 현재 구현되어 있지 않다.
- Scheduler 중복 실행 방지는 PostgreSQL advisory lock으로 처리한다.

## 2. news-service 주요 기능

- `NewsService.GetStockNews` gRPC로 종목별 뉴스 조회
- `NewsCollectionScheduler.collectNews()`로 평일 정해진 시간 뉴스 수집
- `CollectionTarget` 기준 활성 종목 수집
- `stock-service`에서 종목명 조회
- Naver News API 검색 결과 필터링
- 뉴스 기사 저장
- 기사와 종목 매핑 저장
- 수집 결과 로그 저장

## 3. 어떤 서비스를 켜야 하는가

최소 구성은 다음과 같다.

| 구성 요소 | 필요 이유 |
| --- | --- |
| PostgreSQL | `news` schema 저장, Flyway migration, Scheduler advisory lock |
| stock-service | 수집 대상 `stock_code`의 종목명 조회 |
| news-service | gRPC 조회와 Scheduler 수집 |
| Naver News API | 실제 뉴스 검색 |

`GetStockNews` 조회만 확인할 때는 Naver API를 호출하지 않는다. 이미 DB에 저장된 데이터만 조회한다.

## 4. 로컬 서비스 기동 순서

1. PostgreSQL을 실행한다.

```bash
docker compose up -d postgres
```

2. stock-service를 실행한다.

```bash
./gradlew :services:stock-service:bootRun
```

3. news-service를 실행한다.

```bash
NEWS_GRPC_PORT=50064 \
NEWS_SERVER_PORT=8093 \
STOCK_SERVICE_GRPC_ADDRESS=static://localhost:50060 \
NAVER_NEWS_CLIENT_ID= \
NAVER_NEWS_CLIENT_SECRET= \
./gradlew :services:news-service:bootRun
```

Naver 인증값은 실제 Secret을 문서에 기록하지 않는다.

## 5. 뉴스 수집 흐름

실제 호출 흐름은 다음과 같다.

```text
NewsCollectionScheduler.collectNews
  -> NewsCollectionLock.tryLock
  -> NewsCollectionService.collectActiveTargets
  -> CollectionTargetJpaRepository.findByActiveTrueOrderByPriorityAsc
  -> GrpcStockClient.getStock
  -> StockService.GetStock
  -> RestNaverNewsClient.search
  -> NewsCollectionWriter.saveArticlesForTarget
  -> ArticleJpaRepository.insertIgnore
  -> ArticleJpaRepository.findByUrl
  -> ArticleStockMappingJpaRepository.insertIgnore
  -> NewsCollectionWriter.recordLog
  -> CollectionLogJpaRepository.save
```

수집 조건은 현재 코드 기준으로 고정되어 있다.

- Naver query: stock-service에서 받은 `stock.name`
- `display = 3`
- `start = 1`
- `sort = date`
- `title` 또는 `description`에 `stockName`이 포함된 뉴스만 저장
- URL은 `originallink`를 우선 사용하고, 없으면 `link`를 사용
- URL이 없거나 blank면 저장하지 않음

## 6. 종목별 뉴스 조회 흐름

gRPC 진입점은 `NewsGrpcService.getStockNews()`이다.

```text
Client
  -> NewsService.GetStockNews
  -> NewsGrpcService.getStockNews
  -> NewsArticleQueryService.getStockNews
  -> DefaultNewsArticleQueryService.getStockNews
  -> ArticleJpaRepository.findTop3ByStockCode
  -> GetStockNewsResponse
```

`GetStockNews`는 DB만 조회한다. Naver API나 stock-service를 호출하지 않는다.

조회 SQL은 `news.articles`와 `news.article_stock_mappings`를 join하고 다음 정렬을 사용한다.

```sql
ORDER BY a.published_at DESC NULLS LAST, a.created_at DESC
LIMIT 3
```

뉴스가 없으면 빈 `repeated articles`를 반환한다.

## 7. 외부 서비스 계약

### 7.1 stock-service gRPC

호출 위치:

- `GrpcStockClient.getStock(String code)`

호출 RPC:

- `candle.stock.v1.StockService/GetStock`

요청:

- `code`
- `allow_fallback=false`

응답에서 사용하는 필드:

- `stock.stock.code`
- `stock.stock.name`
- `stock.stock.market`
- `stock.stock.sector`
- `stock.stock.market_cap`
- `stock.stock.shares_outstanding`
- `stock.stock.status`

설정:

- channel target: `${STOCK_SERVICE_GRPC_ADDRESS:static://localhost:50060}`
- deadline: `${STOCK_GRPC_DEADLINE:3s}`

### 7.2 Naver News REST API

호출 위치:

- `RestNaverNewsClient.search(NaverNewsSearchRequest request)`

Endpoint:

- `${NAVER_NEWS_BASE_URL:https://openapi.naver.com}/v1/search/news.json`

요청 파라미터:

- `query`
- `display`
- `start`
- `sort`

인증 헤더명:

- `X-Naver-Client-Id`
- `X-Naver-Client-Secret`

timeout:

- connect timeout: `${NAVER_NEWS_CONNECT_TIMEOUT:3s}`
- read timeout: `${NAVER_NEWS_READ_TIMEOUT:5s}`

Naver 응답은 `NaverNewsMapper`에서 HTML tag와 일부 entity를 정리하고, `pubDate`는 RFC 1123 형식으로 `Instant` 변환을 시도한다. 파싱 실패 또는 값 없음이면 `publishedAt`은 `null`이다.

## 8. 데이터 저장 결과

Flyway migration:

- `services/news-service/src/main/resources/migration/V20260703_001__create_news_tables.sql`

생성 schema:

- `news`

생성 enum:

- `news.collection_target_type`
  - `recent_view`
  - `favorite`
  - `popular`
  - `volume_top`
  - `admin`
- `news.collection_status_type`
  - `success`
  - `partial_fail`
  - `fail`

DB FK는 없다.

### 8.1 news.articles

역할:

- 수집된 뉴스 기사 저장

주요 컬럼:

- `id`
- `title`
- `content_summary`
- `url`
- `source`
- `published_at`
- `created_at`
- `updated_at`

제약조건:

- `pk_articles PRIMARY KEY (id)`
- `uq_articles_url UNIQUE (url)`

Index:

- `idx_articles_published_at (published_at DESC)`

중복 방지:

- `ArticleJpaRepository.insertIgnore(...)`
- `ON CONFLICT (url) DO NOTHING`

### 8.2 news.article_stock_mappings

역할:

- 기사와 종목 코드 매핑 저장

주요 컬럼:

- `id`
- `article_id`
- `stock_code`
- `matched_keyword`
- `created_at`
- `updated_at`

제약조건:

- `pk_article_stock_mappings PRIMARY KEY (id)`
- `uq_article_stock_mappings_article_stock UNIQUE (article_id, stock_code)`

Index:

- `idx_article_stock_mappings_stock_created (stock_code, created_at DESC)`
- `idx_article_stock_mappings_article (article_id)`

중복 방지:

- `ArticleStockMappingJpaRepository.insertIgnore(...)`
- `ON CONFLICT (article_id, stock_code) DO NOTHING`

### 8.3 news.collection_targets

역할:

- Scheduler가 수집할 종목 대상 저장

주요 컬럼:

- `id`
- `stock_code`
- `target_type`
- `priority`
- `is_active`
- `created_at`
- `updated_at`

제약조건:

- `pk_collection_targets PRIMARY KEY (id)`
- `uq_collection_targets_stock_type UNIQUE (stock_code, target_type)`
- `ck_collection_targets_priority_non_negative CHECK (priority >= 0)`

Index:

- `idx_collection_targets_active_priority (is_active, priority)`

조회:

- `CollectionTargetJpaRepository.findByActiveTrueOrderByPriorityAsc()`

### 8.4 news.collection_logs

역할:

- Scheduler 수집 결과 저장

주요 컬럼:

- `id`
- `collected_at`
- `target_count`
- `success_count`
- `fail_count`
- `status`
- `message`
- `created_at`

제약조건:

- `pk_collection_logs PRIMARY KEY (id)`
- `ck_collection_logs_target_count_non_negative CHECK (target_count >= 0)`
- `ck_collection_logs_success_count_non_negative CHECK (success_count >= 0)`
- `ck_collection_logs_fail_count_non_negative CHECK (fail_count >= 0)`

Index:

- `idx_collection_logs_collected_at (collected_at DESC)`

기록 정책:

- 모든 target 처리 후 `NewsCollectionWriter.recordLog(...)`가 1회 저장
- 실패 0건이면 `success`
- 성공 0건이면 `fail`
- 성공과 실패가 모두 있으면 `partial_fail`
- 실패 message는 최대 1000자로 잘라 저장
- 실패가 없으면 message는 `collection completed`

## 9. Scheduler와 중복 실행 방지

Scheduler:

- 클래스: `NewsCollectionScheduler`
- 메서드: `collectNews()`
- cron: `0 0 9,12,15 * * MON-FRI`
- zone: `Asia/Seoul`

중복 실행 방지:

- 클래스: `NewsCollectionLock`
- 방식: PostgreSQL session-level advisory lock
- 획득 SQL:

```sql
SELECT pg_try_advisory_lock(1850001, 1);
```

- 해제 SQL:

```sql
SELECT pg_advisory_unlock(1850001, 1);
```

동작:

- lock 획득 성공 시 `NewsCollectionService.collectActiveTargets()` 실행
- lock 획득 실패 시 수집을 건너뛰고 info log만 남김
- lock 처리 중 `SQLException`이 발생하면 `IllegalStateException`으로 감싸지고, Scheduler가 catch하여 error log를 남김
- lock 처리 실패가 애플리케이션 전체 장애로 전파되지 않음

## 10. 실패와 재실행

Target 단위 실패:

- `NewsCollectionService.collectActiveTargets()`는 target별로 `RuntimeException`을 catch한다.
- 실패 target은 `failCount`에 반영한다.
- 다음 target 처리는 계속한다.
- Naver API 예외는 `NaverNewsApiFailureReason.message()`로 기록한다.
- 그 외 `RuntimeException`은 `STOCK_LOOKUP_FAILED`로 기록한다.

Scheduler 단위 실패:

- `NewsCollectionScheduler.collectNews()`는 `RuntimeException`을 catch한다.
- error log를 남기고 예외를 전파하지 않는다.

Naver API 실패 분류:

- 400: `NAVER_INVALID_REQUEST`
- 401, 403: `NAVER_AUTHORIZATION_FAILED`
- 500 이상: `NAVER_SERVER_ERROR`
- 기타 RestClient 예외: `NAVER_REQUEST_FAILED`
- 응답 body null: `NAVER_EMPTY_RESPONSE`

재실행:

- 별도 retry 정책은 없다.
- 다음 cron 또는 수동 재기동/메서드 호출 흐름에서 다시 수집된다.
- 뉴스 URL과 `(article_id, stock_code)` unique 제약으로 중복 저장은 방지된다.

## 11. 결과 확인 SQL

최근 기사 확인:

```sql
SELECT id, title, url, source, published_at, created_at
FROM news.articles
ORDER BY created_at DESC
LIMIT 20;
```

종목별 매핑 확인:

```sql
SELECT stock_code, article_id, matched_keyword, created_at
FROM news.article_stock_mappings
WHERE stock_code = '005930'
ORDER BY created_at DESC;
```

종목별 조회 API와 같은 기준으로 최신 3건 확인:

```sql
SELECT a.id,
       a.title,
       a.content_summary,
       a.url,
       a.source,
       a.published_at,
       a.created_at
FROM news.articles a
JOIN news.article_stock_mappings m
  ON m.article_id = a.id
WHERE m.stock_code = '005930'
ORDER BY a.published_at DESC NULLS LAST, a.created_at DESC
LIMIT 3;
```

수집 대상 확인:

```sql
SELECT id, stock_code, target_type, priority, is_active, created_at, updated_at
FROM news.collection_targets
ORDER BY is_active DESC, priority ASC, stock_code ASC;
```

최근 수집 로그 확인:

```sql
SELECT collected_at,
       target_count,
       success_count,
       fail_count,
       status,
       message
FROM news.collection_logs
ORDER BY collected_at DESC
LIMIT 20;
```

중복 기사 확인:

```sql
SELECT url, COUNT(*)
FROM news.articles
GROUP BY url
HAVING COUNT(*) > 1;
```

중복 매핑 확인:

```sql
SELECT article_id, stock_code, COUNT(*)
FROM news.article_stock_mappings
GROUP BY article_id, stock_code
HAVING COUNT(*) > 1;
```

## 12. 테스트 방법

컴파일:

```bash
./gradlew :services:news-service:compileJava
```

테스트:

```bash
./gradlew :services:news-service:test
```

현재 테스트 클래스:

- `NewsServiceApplicationTest`
- `NewsCollectionServiceTest`
- `NewsCollectionSchedulerTest`
- `NewsGrpcServiceTest`
- `RestNaverNewsClientTest`
- `NaverNewsMapperTest`
- `NaverNewsSearchRequestTest`

## 13. 운영 설정

`application.yml` 기준 설정:

| 항목 | 설정 |
| --- | --- |
| gRPC port | `${NEWS_GRPC_PORT:50064}` |
| HTTP port | `${NEWS_SERVER_PORT:8093}` |
| datasource url | `${NEWS_DB_URL:jdbc:postgresql://localhost:5432/candle?currentSchema=news,public}` |
| datasource username | `${NEWS_DB_USERNAME:candle}` |
| datasource password | `${NEWS_DB_PASSWORD:candle}` |
| Flyway location | `classpath:migration` |
| JPA DDL | `validate` |
| open-in-view | `false` |
| stock-service target | `${STOCK_SERVICE_GRPC_ADDRESS:static://localhost:50060}` |
| stock gRPC deadline | `${STOCK_GRPC_DEADLINE:3s}` |
| Naver base URL | `${NAVER_NEWS_BASE_URL:https://openapi.naver.com}` |
| Naver connect timeout | `${NAVER_NEWS_CONNECT_TIMEOUT:3s}` |
| Naver read timeout | `${NAVER_NEWS_READ_TIMEOUT:5s}` |

환경변수:

- `NEWS_GRPC_PORT`
- `NEWS_SERVER_PORT`
- `NEWS_DB_URL`
- `NEWS_DB_USERNAME`
- `NEWS_DB_PASSWORD`
- `STOCK_SERVICE_GRPC_ADDRESS`
- `STOCK_GRPC_DEADLINE`
- `NAVER_NEWS_CLIENT_ID`
- `NAVER_NEWS_CLIENT_SECRET`
- `NAVER_NEWS_BASE_URL`
- `NAVER_NEWS_CONNECT_TIMEOUT`
- `NAVER_NEWS_READ_TIMEOUT`

Secret 값은 문서나 repository에 기록하지 않는다.
## 14. 서비스 영향도

`news-service`가 정상 동작하지 않으면 종목별 뉴스 조회 결과가 비거나 오래된 데이터로 유지된다.

- `GetStockNews`는 DB만 조회하므로 수집 Scheduler가 실패해도 gRPC 조회 자체는 계속 동작한다.
- 신규 뉴스 수집이 실패하면 `news.articles`, `news.article_stock_mappings`가 갱신되지 않는다.
- 수집 실패 이력은 `news.collection_logs`에 `fail` 또는 `partial_fail`로 남는다.
- `stock-service` 장애가 있으면 해당 target은 실패 처리되고 다음 target 수집은 계속된다.
- Naver News API 장애 또는 인증 실패가 있으면 해당 target은 실패 처리되고 다음 target 수집은 계속된다.
- Scheduler lock 획득 실패는 장애가 아니라 다른 인스턴스가 이미 실행 중인 상태로 보고 수집을 건너뛴다.

현재 구현에서 `news-service`는 Kafka, Redis, Outbox를 사용하지 않는다. 따라서 뉴스 수집 실패가 다른 서비스 이벤트 발행 실패로 전파되는 구조는 없다.

## 15. MSA 설계 의도

`news-service`는 종목 정보를 직접 소유하지 않는다. 종목 코드, 종목명, 시장, 상장 상태 같은 종목 기준 정보는 `stock-service`가 소유한다.

뉴스 수집 시 `stock-service`를 호출하는 이유는 다음과 같다.

- `news-service`가 `stock-service` DB를 직접 읽지 않기 위해서다.
- 종목명 조회 책임을 `stock-service`에 유지하기 위해서다.
- Naver News API 검색어는 현재 `stock.name`을 사용하므로, 종목명 원천을 `stock-service`로 단일화한다.
- `GrpcStockClient.getStock`은 `StockService.GetStock`을 호출하고 `allow_fallback=false`로 요청한다.

현재 호출 계약:

```text
NewsCollectionService.collectActiveTargets
  -> GrpcStockClient.getStock
  -> candle.stock.v1.StockService/GetStock
```

요청 필드:

- `code`
- `allow_fallback=false`

## 16. DB 저장 이유

`GetStockNews`는 요청 시점에 Naver News API를 호출하지 않고 `news` DB만 조회한다.

Scheduler가 미리 수집해 DB에 저장하는 이유는 현재 구현 기준으로 다음과 같다.

- 조회 API에서 외부 Naver API latency와 장애를 분리한다.
- 같은 종목 조회마다 Naver API를 반복 호출하지 않는다.
- URL unique 제약으로 같은 뉴스 중복 저장을 막는다.
- `(article_id, stock_code)` unique 제약으로 같은 기사-종목 매핑 중복 저장을 막는다.
- `collection_logs`로 수집 성공/실패 결과를 운영자가 확인할 수 있다.
- `GetStockNews`는 DB 조회만 수행하므로 뉴스가 없을 때 빈 `repeated articles`를 안정적으로 반환한다.

## 17. 현재 운영 정책

현재 코드 기준 운영 정책은 다음과 같다.

- Scheduler 실행 시간: `0 0 9,12,15 * * MON-FRI`
- Scheduler timezone: `Asia/Seoul`
- Scheduler 중복 실행 방지: PostgreSQL advisory lock
- lock 획득 SQL: `SELECT pg_try_advisory_lock(1850001, 1)`
- lock 해제 SQL: `SELECT pg_advisory_unlock(1850001, 1)`
- lock 획득 실패 시 수집을 건너뛰고 info log만 남긴다.
- target별 실패는 전체 수집을 중단하지 않고 다음 target으로 진행한다.
- 전체 수집 결과는 `news.collection_logs`에 저장한다.
- Naver API retry는 없다.
- stock-service gRPC retry는 없다.
- `GetStockNews`는 Naver API와 stock-service를 호출하지 않는다.
- 운영 Secret 값은 환경변수로 주입하고 문서와 repository에 기록하지 않는다.

필수 운영 의존성:

- PostgreSQL
- stock-service gRPC
- Naver News API credential

## 18. 미구현/향후 계획

현재 미구현:

- Outbox
- Kafka 또는 Redpanda 발행
- Redis/cache
- 멱등성 레코드
- retry 정책
- DB FK
- REST Controller
- 수동 수집 gRPC 또는 관리 API
- Naver API backoff
- 오래된 기사 정리 Scheduler
- 수집 로그 정리 Scheduler
- collection target 자동 생성 정책

향후 변경 지점:

- `collection_targets` 초기 데이터 투입 방식 확정
- Naver 운영 credential 주입 방식 확정
- 운영 관측 지표와 알림 정책 추가
- target type별 생성 주체 확정
- 기사/수집 로그 보관 기간 정책 확정
- Naver API 또는 stock-service 장애 시 재시도 정책 검토
- 수동 재수집 진입점이 필요하면 별도 gRPC 또는 운영 도구 설계 필요
