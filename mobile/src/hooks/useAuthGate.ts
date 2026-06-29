import { useAuth } from '../contexts/AuthContext';

// useAuth().requireAuth를 의도가 드러나는 이름으로 재노출하는 얇은 래퍼.
// 모달 상태는 AuthContext가 단독 소유하므로 이 훅은 상태를 들지 않는다.
export function useAuthGate() {
  const { requireAuth } = useAuth();
  return { requireAuth };
}
