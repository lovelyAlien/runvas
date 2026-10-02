import React from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleProp,
  StyleSheet,
  ViewStyle,
} from 'react-native';

interface KeyboardAvoidingModalContainerProps {
  /** 반투명 배경 등 오버레이 전체에 적용할 스타일 */
  style?: StyleProp<ViewStyle>;
  /** 카드 정렬(가운데 정렬, 여백 등)에 적용할 스타일 */
  contentContainerStyle?: StyleProp<ViewStyle>;
  children: React.ReactNode;
}

// 화면 가운데 뜨는 Modal 카드가 키보드에 가려지지 않게 감싸는 오버레이.
// iOS는 키보드 높이만큼 padding을 줘서 카드를 남은 영역 가운데로 올리고, 카드가 남은 영역보다
// 크면(작은 화면 + 사유 입력란이 있는 탈퇴 모달 등) ScrollView로 스크롤할 수 있게 한다.
// Android는 RN Modal 창이 이미 adjustResize로 동작하므로 behavior를 주지 않는다
// (다른 화면의 KeyboardAvoidingView와 같은 규칙).
export default function KeyboardAvoidingModalContainer({
  style,
  contentContainerStyle,
  children,
}: KeyboardAvoidingModalContainerProps) {
  return (
    <KeyboardAvoidingView
      style={[styles.container, style]}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[styles.content, contentContainerStyle]}
        keyboardShouldPersistTaps="handled"
        bounces={false}
        showsVerticalScrollIndicator={false}
      >
        {children}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
  },
});
