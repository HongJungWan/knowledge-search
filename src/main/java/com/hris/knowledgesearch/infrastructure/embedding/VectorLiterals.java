package com.hris.knowledgesearch.infrastructure.embedding;

/**
 * pgvector 리터럴 유틸 — {@code float[]} 를 {@code "[f1,f2,...]"} 문자열로 변환한다.
 * 바인딩 후 {@code CAST(:param AS vector)} 로 적재/질의한다. 어댑터·백필이 공유한다.
 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
