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

    @Test
    void validate_다시_발을_디뎠다는_오탐하지_않는다() {
        assertThatCode(() -> filter.validate("다시 발을 디뎠다"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_무시_발언은_오탐하지_않는다() {
        assertThatCode(() -> filter.validate("무시 발언은 삼가주세요"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_관악시_발산은_오탐하지_않는다() {
        assertThatCode(() -> filter.validate("관악시 발산 인근 러닝 코스"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_금칙어_뒤에_조사가_공백없이_붙어도_감지한다() {
        assertThatThrownBy(() -> filter.validate("개 새끼야"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_금칙어_뒤에_문장부호가_공백없이_붙어도_감지한다() {
        assertThatThrownBy(() -> filter.validate("시 발!"))
                .isInstanceOf(ApiException.class);
    }
}
