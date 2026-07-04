CREATE SCHEMA IF NOT EXISTS news;

CREATE TYPE news.collection_target_type AS ENUM (
    'recent_view',
    'favorite',
    'popular',
    'volume_top',
    'admin'
);

CREATE TYPE news.collection_status_type AS ENUM (
    'success',
    'partial_fail',
    'fail'
);

CREATE OR REPLACE FUNCTION news.set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE news.articles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    content_summary TEXT,
    url TEXT NOT NULL,
    source TEXT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_articles PRIMARY KEY (id),
    CONSTRAINT uq_articles_url UNIQUE (url)
);

CREATE TABLE news.article_stock_mappings (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    matched_keyword TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_article_stock_mappings PRIMARY KEY (id),
    CONSTRAINT uq_article_stock_mappings_article_stock UNIQUE (article_id, stock_code)
);

CREATE TABLE news.collection_targets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    stock_code VARCHAR(20) NOT NULL,
    target_type news.collection_target_type NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_collection_targets PRIMARY KEY (id),
    CONSTRAINT uq_collection_targets_stock_type UNIQUE (stock_code, target_type),
    CONSTRAINT ck_collection_targets_priority_non_negative CHECK (priority >= 0)
);

CREATE TABLE news.collection_logs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    target_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    fail_count INTEGER NOT NULL DEFAULT 0,
    status news.collection_status_type NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_collection_logs PRIMARY KEY (id),
    CONSTRAINT ck_collection_logs_target_count_non_negative CHECK (target_count >= 0),
    CONSTRAINT ck_collection_logs_success_count_non_negative CHECK (success_count >= 0),
    CONSTRAINT ck_collection_logs_fail_count_non_negative CHECK (fail_count >= 0)
);

CREATE TRIGGER trg_articles_set_updated_at
BEFORE UPDATE ON news.articles
FOR EACH ROW
EXECUTE FUNCTION news.set_updated_at();

CREATE TRIGGER trg_article_stock_mappings_set_updated_at
BEFORE UPDATE ON news.article_stock_mappings
FOR EACH ROW
EXECUTE FUNCTION news.set_updated_at();

CREATE TRIGGER trg_collection_targets_set_updated_at
BEFORE UPDATE ON news.collection_targets
FOR EACH ROW
EXECUTE FUNCTION news.set_updated_at();

CREATE INDEX idx_articles_published_at
    ON news.articles (published_at DESC);

CREATE INDEX idx_article_stock_mappings_stock_created
    ON news.article_stock_mappings (stock_code, created_at DESC);

CREATE INDEX idx_article_stock_mappings_article
    ON news.article_stock_mappings (article_id);

CREATE INDEX idx_collection_targets_active_priority
    ON news.collection_targets (is_active, priority);

CREATE INDEX idx_collection_logs_collected_at
    ON news.collection_logs (collected_at DESC);
