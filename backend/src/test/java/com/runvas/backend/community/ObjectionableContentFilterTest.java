package com.runvas.backend.community;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runvas.backend.common.ApiException;
import org.junit.jupiter.api.Test;

class ObjectionableContentFilterTest {

    private final ObjectionableContentFilter filter = new ObjectionableContentFilter();

    @Test
    void validate_금칙어가_없으면_통과한다() {
        assertThatCode(() -> filter.validate("오늘 한강 러닝 코스 공유합니다", "10km 완주했어요"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_금칙어가_포함되면_예외를_던진다() {
        assertThatThrownBy(() -> filter.validate("이 코스 만든 놈 시발 진짜"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_공백으로_우회해도_감지한다() {
        assertThatThrownBy(() -> filter.validate("시 발 이런 코스가"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_null_텍스트는_무시한다() {
        assertThatCode(() -> filter.validate("정상 텍스트", null))
                .doesNotThrowAnyException();
    }
}
