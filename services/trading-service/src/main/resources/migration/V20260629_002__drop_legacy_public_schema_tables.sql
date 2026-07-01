-- public 스키마에 남아있던 구버전(스키마 분리 이전) 테이블 정리.
-- account_balances/orders/holdings/outbox_events/idempotency_records는
-- account/order_svc/reservation 스키마 분리 이전 모델로, 현재는 사용하지 않는다.
-- 비어있는 상태(개발 단계)에서 정리하며, 데이터 이전(backfill)은 불필요했다.
--
-- holdings는 본 서비스(Trading)의 소유 도메인이 아니므로(Holding/Portfolio Service 소유),
-- 애초에 이 자리에 있어서는 안 되는 테이블이었다.

DROP TABLE IF EXISTS public.orders;
DROP TABLE IF EXISTS public.account_balances;
DROP TABLE IF EXISTS public.holdings;
DROP TABLE IF EXISTS public.outbox_events;
DROP TABLE IF EXISTS public.idempotency_records;