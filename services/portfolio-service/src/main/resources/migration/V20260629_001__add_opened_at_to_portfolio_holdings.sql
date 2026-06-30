-- 포지션 오픈 시각 (보유기간 통계용). 기존 행은 오픈 시각 불명이므로 NULL 허용.
ALTER TABLE portfolio_holdings
    ADD COLUMN opened_at TIMESTAMP WITH TIME ZONE;
