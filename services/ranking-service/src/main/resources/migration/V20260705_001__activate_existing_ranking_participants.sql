-- 사용자·계좌 상태 변경 기능 도입 전에는 생성된 모든 사용자와 계좌를 ACTIVE로 본다.
-- 향후 UserStatusChanged/AccountStatusChanged 계약이 추가되면 이벤트 투영으로 실제 상태를 유지한다.
UPDATE ranking_participants
SET user_status = 'ACTIVE'
WHERE user_status = 'UNKNOWN';

UPDATE ranking_participants
SET account_status = 'ACTIVE'
WHERE account_status = 'UNKNOWN';
