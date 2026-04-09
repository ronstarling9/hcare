// src/navigation/RootNavigator.tsx
import React, { useEffect, useState, useRef } from 'react';
import { NavigationContainer, LinkingOptions } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import type { NavigationContainerRef } from '@react-navigation/native';

import { useAuthStore } from '@/store/authStore';
import { useNotifications } from '@/hooks/useNotifications';
import { AppNavigator }             from './AppNavigator';
import { LoginScreen }              from '@/screens/auth/LoginScreen';
import { DeepLinkHandlerScreen }    from '@/screens/auth/DeepLinkHandlerScreen';
import { LinkExpiredScreen }        from '@/screens/auth/LinkExpiredScreen';
import { WelcomeScreen }            from '@/screens/onboarding/WelcomeScreen';
import { NotificationsScreen }      from '@/screens/onboarding/NotificationsScreen';
import { LocationScreen }           from '@/screens/onboarding/LocationScreen';

const AuthStack = createNativeStackNavigator();
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 2, staleTime: 30_000 } },
});

const linking: LinkingOptions<{}> = {
  prefixes: ['hcare://'],
  config: {
    screens: {
      DeepLinkHandler: 'auth',
    },
  },
};

export function RootNavigator() {
  const { isAuthenticated, firstLogin, rehydrate } = useAuthStore();
  const [hydrated, setHydrated] = useState(false);
  const navigationRef = useRef<NavigationContainerRef<any>>(null);

  useNotifications(navigationRef);

  useEffect(() => {
    rehydrate().finally(() => setHydrated(true));
  }, []);

  if (!hydrated) return null; // Show splash while rehydrating

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <NavigationContainer ref={navigationRef} linking={linking}>
          {isAuthenticated ? (
            firstLogin ? (
              <AuthStack.Navigator screenOptions={{ headerShown: false }}>
                <AuthStack.Screen name="Welcome"       component={WelcomeScreen} />
                <AuthStack.Screen name="Notifications" component={NotificationsScreen} />
                <AuthStack.Screen name="Location"      component={LocationScreen} />
              </AuthStack.Navigator>
            ) : (
              <AppNavigator />
            )
          ) : (
            <AuthStack.Navigator screenOptions={{ headerShown: false }}>
              <AuthStack.Screen name="Login"           component={LoginScreen} />
              <AuthStack.Screen name="DeepLinkHandler" component={DeepLinkHandlerScreen} />
              <AuthStack.Screen name="LinkExpired"     component={LinkExpiredScreen} />
            </AuthStack.Navigator>
          )}
        </NavigationContainer>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
