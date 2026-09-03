package com.runvas.backend.community;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ObjectionableContentFilter {

    private static final List<String> BANNED_TERMS = List.of(
            "시발", "씨발", "개새끼", "병신", "지랄", "좆같",
            "fuck", "faggot", "nigger", "kill yourself"
    );

    public void validate(String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase();
            for (String term : BANNED_TERMS) {
                if (lower.contains(term) || containsWhitespaceEvasion(lower, term)) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "부적절한 콘텐츠가 포함되어 있습니다");
                }
            }
        }
    }

    /**
     * "시 발"처럼 금칙어 사이에 공백을 끼워 필터를 우회하는 경우를 감지한다.
     *
     * <p>단순히 전체 공백을 제거한 뒤 부분 문자열로 검사하면(과거 구현) "다시 발을 디뎠다" →
     * "다시발을디뎠다"처럼 우연히 단어 경계를 넘어 금칙어와 같은 부분 문자열이 만들어지는
     * 일반 문장에서 오탐이 발생한다("무시 발언", "관악시 발산"도 동일).
     *
     * <p>이를 피하기 위해, 공백을 제거해서 만들어진 매칭 구간이 원문에서 "단어 경계"에 걸쳐
     * 있는 경우에만(구간 시작 직전이 문자열 시작이거나 공백, 구간 끝 직후가 문자열 끝이거나
     * 공백) 우회 시도로 간주한다. "시 발"은 앞뒤가 공백/문자열 경계이므로 감지되고,
     * "다시 발을"의 "시"는 앞에 "다"가 바로 붙어 있어 경계 조건을 만족하지 못해 감지되지
     * 않는다.
     *
     * <p>한계: 이 방식은 여전히 완벽하지 않다 — 예를 들어 금칙어를 세 단어 이상으로 쪼개
     * 앞뒤를 다른 글자로 감싸는 정교한 우회(예: "가시 발톱깎이"처럼 앞뒤 모두 실제 단어의
     * 일부이면서 우연히 경계 조건까지 만족하는 극단적 사례)는 여전히 오탐/미탐이 발생할 수
     * 있다. 완전한 형태소 분석 없이는 자연어 오탐을 100% 제거할 수 없으므로, 이 구현은
     * "명백한 공백 우회"만 잡고 애매한 사례는 통과시키는 쪽으로 보수적으로 설계했다.
     */
    private boolean containsWhitespaceEvasion(String lower, String term) {
        int length = lower.length();
        int[] indexMap = new int[length];
        StringBuilder normalizedBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = lower.charAt(i);
            if (!Character.isWhitespace(c)) {
                indexMap[normalizedBuilder.length()] = i;
                normalizedBuilder.append(c);
            }
        }
        String normalized = normalizedBuilder.toString();

        int fromIndex = 0;
        while (true) {
            int matchIndex = normalized.indexOf(term, fromIndex);
            if (matchIndex < 0) {
                return false;
            }
            int startOriginal = indexMap[matchIndex];
            int endOriginal = indexMap[matchIndex + term.length() - 1];
            boolean startsAtBoundary = startOriginal == 0 || Character.isWhitespace(lower.charAt(startOriginal - 1));
            boolean endsAtBoundary =
                    endOriginal == length - 1 || Character.isWhitespace(lower.charAt(endOriginal + 1));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            fromIndex = matchIndex + 1;
        }
    }
}
