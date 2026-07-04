-- 거래일 캘린더(휴장일). 권위 소스는 batch(키움/KRX 영업일)이며, 그 전까지 확실한 고정일만 시드한다.
-- 음력 공휴일(설날·추석·부처님오신날)과 대체공휴일은 매년 이동하므로 여기 넣지 않고
-- batch 가 채운다(넣지 않으면 최악의 경우 해당일 WS 가 무의미하게 연결될 뿐, 틱이 없어 무해).
CREATE TABLE market_holidays (
    holiday_date DATE PRIMARY KEY,
    name VARCHAR(100)
);

INSERT INTO market_holidays (holiday_date, name) VALUES
    ('2026-01-01', '신정'),
    ('2026-03-01', '삼일절'),
    ('2026-05-05', '어린이날'),
    ('2026-06-06', '현충일'),
    ('2026-08-15', '광복절'),
    ('2026-10-03', '개천절'),
    ('2026-10-09', '한글날'),
    ('2026-12-25', '성탄절'),
    ('2026-12-31', '연말 휴장')
ON CONFLICT (holiday_date) DO NOTHING;
