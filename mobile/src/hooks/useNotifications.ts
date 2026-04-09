// src/hooks/useNotifications.ts
import { useEffect } from 'react';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { devicesApi } from '@/api/devices';
import { useAuthStore } from '@/store/authStore';
import type { NavigationContainerRef } from '@react-navigation/native';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

export function useNotifications(navigationRef: React.RefObject<NavigationContainerRef<any>>) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) return;

    // Register push token on every launch
    Notifications.getExpoPushTokenAsync()
      .then(({ data: token }) => {
        return devicesApi.registerPushToken(token, Platform.OS as 'ios' | 'android');
      })
      .catch(console.error);

    // Handle notification tap while app is in background/quit
    const sub = Notifications.addNotificationResponseReceivedListener(response => {
      const data = response.notification.request.content.data as Record<string, string>;
      const nav = navigationRef.current;
      if (!nav) return;

      if (data.shiftId) nav.navigate('Today' as never);
      if (data.threadId) nav.navigate('Messages' as never);
    });

    return () => sub.remove();
  }, [isAuthenticated]);
}
