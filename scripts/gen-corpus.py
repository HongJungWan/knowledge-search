#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
정밀도 압력 평가용 대량 코퍼스 생성기 (재현용).

설계 의도:
- 모든 문서가 동일한 "정산 처리 절차" 공통 단락을 대부분 공유 → 키워드/벡터가 상태를 혼동(텍스트 유사).
- 상태(settlement_status)는 code_values 로만 정확히 구분되고, 본문에는 쿼리 표면형(미정산/정산완료/캔슬/홀드)이
  literal 로 등장하지 않는 약한 패러프레이즈로만 노출 → 키워드는 실패, 벡터는 약신호, 코드 필터가 결정적.
- 가맹점/금액/일자를 변주해 content_hash 중복(적재 누락) 회피.

출력: src/main/resources/sample/settlement-source-large.json  (status 4종 × PER_STATUS 건)
실행: python scripts/gen-corpus.py
"""
import json
import os

PER_STATUS = 20  # 상태별 문서 수 (= gold 의 relevantCount)

# (code, 본문 상태 절 — 쿼리 표면형과 다른 패러프레이즈로만 표현)
STATUSES = [
    ("PENDING",  "현재 이 건은 아직 정산이 확정되지 않아 처리 대기 상태로 남아 있다."),
    ("SETTLED",  "이 건은 정산 절차가 모두 마무리되어 가맹점 지급까지 끝났다."),
    ("HOLD",     "이 건은 점검 사유로 지급이 일시적으로 멈춰 묶여 있는 상태다."),
    ("CANCELED", "이 건은 거래가 무효 처리되어 정산 대상에서 제외되었다."),
]

MERCHANTS = ["가맹점A", "스토어비", "샵씨", "마켓디", "온라인이", "오프라인에프",
             "프랜차이즈지", "개인사업자에이치", "법인아이", "제휴사제이"]
GRADES = ["GENERAL", "PREMIUM", "BASIC"]

COMMON = ("가맹점 {m}의 거래는 정산 주기에 따라 집계되고 검증 절차를 거쳐 처리된다. "
          "마감 시각 이후의 거래는 다음 주기로 이월되며, 지급은 영업일 기준으로 순차 진행된다. "
          "수수료와 조정 항목이 반영되어 최종 정산 금액이 산출되고, 출처 문서와 함께 기록된다. "
          "거래액 {amt}원, 기준일 2026-{mm:02d}-{dd:02d}, 가맹점 등급 {grade}, 사례번호 {idx}. ")


def main():
    docs = []
    idx = 0
    for code, clause in STATUSES:
        for i in range(PER_STATUS):
            idx += 1
            m = MERCHANTS[idx % len(MERCHANTS)]
            grade = GRADES[idx % len(GRADES)]
            amt = 100000 + idx * 1373  # 고유 금액
            mm = (idx % 12) + 1
            dd = (idx % 28) + 1
            body = COMMON.format(m=m, amt=amt, mm=mm, dd=dd, grade=grade, idx=idx) + clause
            docs.append({
                "domain": "settlement",
                "title": f"정산 처리 절차 안내 #{idx}",
                "body": body,
                "sourceUrl": f"https://wiki.internal/settlement/case/{idx}",
                "codeValues": {"settlement_status": code},
            })

    out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "sample", "settlement-source-large.json")
    out = os.path.normpath(out)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(docs, f, ensure_ascii=False, indent=2)
    print(f"generated {len(docs)} docs ({len(STATUSES)} statuses x {PER_STATUS}) -> {out}")


if __name__ == "__main__":
    main()
