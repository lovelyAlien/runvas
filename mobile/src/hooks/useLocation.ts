import { useCallback } from 'react';
import * as Location from 'expo-location';
import { Coordinate } from '../types';

export function useLocation() {
  const getCurrentLocation = useCallback(async (): Promise<Coordinate | null> => {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') return null;

    const location = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.High,
    });

    return {
      latitude: location.coords.latitude,
      longitude: location.coords.longitude,
    };
  }, []);

  return { getCurrentLocation };
}
