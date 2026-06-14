# 한국어 형태소 분석기(nori) 포함 OpenSearch 이미지 (로드맵 #7).
# 표준 분석기는 한국어 조사/복합어를 제대로 못 나눈다(예: "정산을"→토큰 "정산을"). nori 는 형태소로 분해
# (예: "정산을"→["정산"]) → BM25 매칭 품질 향상. 빌드 시 플러그인 설치(인터넷 필요).
FROM opensearchproject/opensearch:2.19.0
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-nori
