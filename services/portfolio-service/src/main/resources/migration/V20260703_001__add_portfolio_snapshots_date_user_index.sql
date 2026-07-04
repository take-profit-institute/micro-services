CREATE INDEX idx_portfolio_snapshots_date_user
    ON portfolio_snapshots (snapshot_date, user_id);
