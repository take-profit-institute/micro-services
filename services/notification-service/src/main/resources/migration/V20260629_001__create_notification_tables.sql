CREATE SCHEMA IF NOT EXISTS notification;

CREATE TYPE notification.notification_type AS ENUM (
    'PRICE_RISE',
    'PRICE_FALL',
    'BUY_FILLED',
    'SELL_FILLED',
    'MARKET_OPEN',
    'MARKET_CLOSE'
);

CREATE TYPE notification.notification_status AS ENUM (
    'UNREAD',
    'READ'
);

CREATE TYPE notification.device_platform AS ENUM (
    'WEB',
    'ANDROID',
    'IOS'
);

CREATE TYPE notification.delivery_status AS ENUM (
    'PENDING',
    'SENT',
    'FAILED'
);

CREATE TABLE notification.notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type notification.notification_type NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    status notification.notification_status NOT NULL,
    meta_json JSONB,
    triggered_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification.device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    fcm_token TEXT NOT NULL,
    platform notification.device_platform NOT NULL,
    device_id TEXT,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_device_tokens_fcm_token UNIQUE (fcm_token)
);

CREATE TABLE notification.notification_deliveries (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    device_token_id UUID NOT NULL,
    status notification.delivery_status NOT NULL,
    fcm_message_id TEXT,
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notification_deliveries_notification
        FOREIGN KEY (notification_id)
        REFERENCES notification.notifications (id),
    CONSTRAINT fk_notification_deliveries_device_token
        FOREIGN KEY (device_token_id)
        REFERENCES notification.device_tokens (id)
);

CREATE INDEX idx_notifications_user_created
    ON notification.notifications (user_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_user_status
    ON notification.notifications (user_id, status);

CREATE INDEX idx_device_tokens_user_active
    ON notification.device_tokens (user_id, active);

CREATE INDEX idx_notification_deliveries_notification_created
    ON notification.notification_deliveries (notification_id, created_at ASC);
