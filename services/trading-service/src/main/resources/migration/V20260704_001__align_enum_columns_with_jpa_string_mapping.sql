-- JPA entities use @Enumerated(EnumType.STRING), which binds enum values as VARCHAR.
-- Keep the schema contract aligned with that mapping so inserts/updates do not require
-- PostgreSQL enum casts.

ALTER TABLE account.accounts
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text,
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

DROP INDEX IF EXISTS order_svc.uq_orders_account_symbol_pending;

ALTER TABLE order_svc.orders
    DROP CONSTRAINT IF EXISTS chk_orders_price_krw_by_kind;

ALTER TABLE order_svc.orders
    ALTER COLUMN side TYPE VARCHAR(10) USING side::text,
    ALTER COLUMN order_kind TYPE VARCHAR(20) USING order_kind::text,
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text,
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE order_svc.orders
    ADD CONSTRAINT chk_orders_price_krw_by_kind CHECK (
        (order_kind = 'LIMIT' AND price_krw IS NOT NULL)
            OR (order_kind = 'MARKET' AND price_krw IS NULL)
        );

CREATE UNIQUE INDEX uq_orders_account_symbol_pending
    ON order_svc.orders (account_id, symbol)
    WHERE status = 'PENDING';

DROP INDEX IF EXISTS reservation.idx_reservations_batch_lookup;
DROP INDEX IF EXISTS reservation.uq_reservations_account_symbol_reserved;

ALTER TABLE reservation.reservations
    DROP CONSTRAINT IF EXISTS chk_reservations_timing_order_kind,
    DROP CONSTRAINT IF EXISTS chk_reservations_price_krw;

ALTER TABLE reservation.reservations
    ALTER COLUMN timing TYPE VARCHAR(20) USING timing::text,
    ALTER COLUMN order_kind TYPE VARCHAR(30) USING order_kind::text,
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text,
    ALTER COLUMN status SET DEFAULT 'RESERVED';

ALTER TABLE reservation.reservations
    ADD CONSTRAINT chk_reservations_timing_order_kind CHECK (
        (timing = 'OPEN' AND order_kind IN ('MARKET', 'LIMIT'))
            OR (timing IN ('TODAY_CLOSE', 'PREV_CLOSE') AND order_kind = 'AFTER_HOURS_CLOSE')
        ),
    ADD CONSTRAINT chk_reservations_price_krw CHECK (
        (order_kind = 'LIMIT' AND price_krw IS NOT NULL)
            OR (order_kind != 'LIMIT' AND price_krw IS NULL)
        );

CREATE INDEX idx_reservations_batch_lookup
    ON reservation.reservations (scheduled_date, status, timing)
    WHERE status = 'RESERVED';

CREATE UNIQUE INDEX uq_reservations_account_symbol_reserved
    ON reservation.reservations (account_id, symbol)
    WHERE status = 'RESERVED';
