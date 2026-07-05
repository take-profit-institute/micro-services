-- stocks 카탈로그에 적재된 재무 스냅샷을 stock_financials 로 백필한다.
-- stock_financials 가 비어 있어 종목 상세의 재무 탭이 0으로 보이던 문제 해결 — 서비스/BFF 코드 변경 없이
-- 데이터만 채운다(기존 조회 경로: DefaultStockCatalogService → StockFinancialsReader).
-- 금액 단위는 stocks 와 동일(억원)로 그대로 복사한다. 원 단위 변환은 BFF 경계에서 수행한다.
-- 재실행 안전(ON CONFLICT). 실제 분기 재무('YYYYQn')가 적재되면 문자열 정렬상 그쪽이 최신으로 우선한다
-- ('2025' < '2025Q1' → findTop...OrderByFiscalPeriodDesc 가 실제 분기 데이터를 먼저 고른다).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'stocks'
          AND column_name = 'revenue'
    ) THEN
        EXECUTE '
            INSERT INTO stock_financials (stock_id, fiscal_period, revenue, operating_profit, net_income, per, pbr, roe)
            SELECT stock_id, ''2025'', revenue, operating_profit, net_income, per, pbr, roe
            FROM stocks
            WHERE revenue IS NOT NULL
               OR operating_profit IS NOT NULL
               OR net_income IS NOT NULL
               OR per IS NOT NULL
               OR pbr IS NOT NULL
               OR roe IS NOT NULL
            ON CONFLICT (stock_id, fiscal_period) DO UPDATE SET
                revenue          = EXCLUDED.revenue,
                operating_profit = EXCLUDED.operating_profit,
                net_income       = EXCLUDED.net_income,
                per              = EXCLUDED.per,
                pbr              = EXCLUDED.pbr,
                roe              = EXCLUDED.roe
        ';
    END IF;
END
$$;
