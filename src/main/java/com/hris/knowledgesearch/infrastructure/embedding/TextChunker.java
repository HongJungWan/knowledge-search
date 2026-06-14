package com.hris.knowledgesearch.infrastructure.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 본문을 문장 단위 청크로 나누는 유틸(로드맵 #3 청킹).
 * <p>
 * 장문 본문을 통째로 임베딩하면 핵심 문장이 보일러플레이트에 희석된다. 문장 청크로 나눠 청크별 임베딩하면
 * 질의가 핵심 문장 청크에 직접 매칭돼 검색 정밀도가 오른다. 한국어 종결("다.")·마침표·줄바꿈에서 자른다.
 * <p>
 * 제목은 <b>별도 청크 1개</b>로만 둔다(제목이 질의와 맞을 때 매칭). 각 문장 청크에 제목을 prepend 하지 않는다 —
 * 제네릭/공통 제목을 모든 청크에 붙이면 청크 임베딩이 다시 희석돼 청킹 이점이 사라지기 때문(측정으로 확인).
 */
public final class TextChunker {

    private static final int MIN_CHUNK_CHARS = 4;

    private TextChunker() {
    }

    public static List<String> chunk(String title, String body) {
        List<String> chunks = new ArrayList<>();
        String header = title == null ? "" : title.trim();
        String text = body == null ? "" : body;

        if (!header.isEmpty()) {
            chunks.add(header); // 제목 청크 1개
        }
        for (String sentence : text.split("(?<=다\\.)|(?<=\\.)|\\n")) {
            String s = sentence.trim();
            if (s.length() >= MIN_CHUNK_CHARS) {
                chunks.add(s); // 순수 문장 청크(제목 미prepend)
            }
        }
        if (chunks.isEmpty() && !text.isBlank()) {
            chunks.add(text.trim());
        }
        return chunks;
    }
}
