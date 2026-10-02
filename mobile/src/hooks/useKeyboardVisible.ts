import { useEffect, useState } from 'react';
import { Keyboard, Platform } from 'react-native';

// 키보드가 화면에 올라와 있는지 반환한다.
// iOS는 키보드 애니메이션 시작 시점(will*) 이벤트로 레이아웃을 키보드와 함께 바꾸고,
// Android는 will* 이벤트가 없어 did* 이벤트를 쓴다.
export function useKeyboardVisible(): boolean {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const showSubscription = Keyboard.addListener(showEvent, () => setIsVisible(true));
    const hideSubscription = Keyboard.addListener(hideEvent, () => setIsVisible(false));
    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, []);

  return isVisible;
}
