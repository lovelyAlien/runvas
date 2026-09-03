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
            String normalized = text.toLowerCase().replaceAll("\\s+", "");
            for (String term : BANNED_TERMS) {
                if (normalized.contains(term)) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "부적절한 콘텐츠가 포함되어 있습니다");
                }
            }
        }
    }
}
