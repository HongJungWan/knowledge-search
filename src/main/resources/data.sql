-- ===================================================================
-- 즉시 검색 데모용 시드 (LOCAL/H2). ETL(/etl/run) 과 별개로 부팅 직후 검색이 동작하게 한다.
-- content_hash 는 데모용 고정 더미값 — ETL 의 실제 SHA-256 과 충돌하지 않도록 'seed-' 접두사를 둔다.
-- created_at 은 BaseEntity 가 NOT NULL 이므로 명시 채운다.
-- ===================================================================

INSERT INTO knowledge_record (domain, title, body, source_url, code_values, source_updated_at, content_hash, created_at, updated_at)
VALUES
('SETTLEMENT', '정산 주기 및 마감 정책',
 '정산은 주 단위로 집계하며 매주 화요일 23시에 전주 거래를 마감한다. 마감 이후 거래는 다음 주기로 이월된다.',
 'https://wiki.internal/settlement/cycle', '{"settlement_cycle":"WEEKLY"}',
 TIMESTAMP '2026-06-01 09:00:00', 'seed-0001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('SETTLEMENT', '가맹점 수수료율 체계',
 '기본 가맹점 수수료율은 거래액의 2.8%이며 월 거래액 1억원 초과 구간은 2.3%로 체감 적용한다.',
 'https://wiki.internal/settlement/fee-rate', '{"merchant_grade":"GENERAL"}',
 TIMESTAMP '2026-06-02 09:00:00', 'seed-0002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('SETTLEMENT', '미정산 거래(PENDING) 처리 기준',
 '정산 마감 시점에 검증이 끝나지 않은 거래는 미정산(PENDING) 상태로 둔다. 7영업일 이상 미정산이면 운영팀에 알림을 보낸다.',
 'https://wiki.internal/settlement/pending', '{"settlement_status":"PENDING"}',
 TIMESTAMP '2026-06-03 09:00:00', 'seed-0003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('SETTLEMENT', '정산 보류(HOLD) 사유 및 해제',
 '사기 의심·분쟁·가맹점 정보 불일치 시 정산을 보류(HOLD)한다. 사유 해소 후 운영팀 승인으로 해제한다.',
 'https://wiki.internal/settlement/hold', '{"settlement_status":"HOLD"}',
 TIMESTAMP '2026-06-04 09:00:00', 'seed-0004', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('SETTLEMENT', '정산 지급일 규칙(T+2)',
 '정산 확정 후 2영업일째(T+2)에 가맹점 계좌로 지급한다. 공휴일이면 다음 영업일로 순연한다.',
 'https://wiki.internal/settlement/payout', '{"payout_rule":"T_PLUS_2"}',
 TIMESTAMP '2026-06-05 09:00:00', 'seed-0005', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
