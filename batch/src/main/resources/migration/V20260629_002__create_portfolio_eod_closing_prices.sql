CREATE TABLE batch_portfolio_eod_closing_prices (
    job_instance_id BIGINT                   NOT NULL,
    business_date   DATE                     NOT NULL,
    symbol          VARCHAR(20)              NOT NULL,
    closing_price   BIGINT                   NOT NULL CHECK (closing_price > 0),
    quoted_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (job_instance_id, symbol),
    CONSTRAINT fk_eod_price_job_instance
        FOREIGN KEY (job_instance_id) REFERENCES batch_job_instance(job_instance_id)
);
