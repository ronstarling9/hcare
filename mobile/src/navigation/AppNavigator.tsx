// src/navigation/AppNavigator.tsx
import React from 'react';
import { View } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { TabBar } from './TabBar';

// Screen imports — stubs until each task creates them
import { TodayScreen }           from '@/screens/today/TodayScreen';
import { OpenShiftsScreen }       from '@/screens/openShifts/OpenShiftsScreen';
import { MessagesInboxScreen }    from '@/screens/messages/MessagesInboxScreen';
import { MessageThreadScreen }    from '@/screens/messages/MessageThreadScreen';
import { ProfileScreen }          from '@/screens/profile/ProfileScreen';
import { SettingsScreen }         from '@/screens/settings/SettingsScreen';
import { VisitScreen }            from '@/screens/visit/VisitScreen';
import { CarePlanScreen }         from '@/screens/carePlan/CarePlanScreen';
import { ConflictDetailScreen }   from '@/screens/conflict/ConflictDetailScreen';
import { ClockInSheet }           from '@/screens/clockIn/ClockInSheet';

const Tab   = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

// Placeholder rendered when the ClockIn tab slot is somehow focused directly.
// The FAB in TabBar navigates to the ClockInSheet modal instead of this tab,
// so this screen should never be visible to the user.
function ClockInPlaceholder() {
  return <View style={{ flex: 1, backgroundColor: '#f6f6fa' }} />;
}

function MessageStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MessagesInbox"  component={MessagesInboxScreen} />
      <Stack.Screen name="MessageThread"  component={MessageThreadScreen} />
    </Stack.Navigator>
  );
}

export function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Tabs" component={TabNavigator} />
      <Stack.Screen name="Visit"          component={VisitScreen} />
      <Stack.Screen name="CarePlan"       component={CarePlanScreen} />
      <Stack.Screen name="Settings"       component={SettingsScreen} />
      <Stack.Screen name="ConflictDetail" component={ConflictDetailScreen} />
      {/* ClockInSheet must be a modal in the ROOT stack so it floats above the tab bar */}
      <Stack.Screen name="ClockInSheet"   component={ClockInSheet}
                    options={{ presentation: 'modal', headerShown: false }} />
    </Stack.Navigator>
  );
}

function TabNavigator() {
  return (
    <Tab.Navigator tabBar={(props) => <TabBar {...props} />}
                   screenOptions={{ headerShown: false }}>
      <Tab.Screen name="Today"      component={TodayScreen} />
      <Tab.Screen name="OpenShifts" component={OpenShiftsScreen} />
      {/* ClockIn tab slot exists for layout purposes only — FAB navigates to the ClockInSheet modal */}
      <Tab.Screen name="ClockIn"    component={ClockInPlaceholder} />
      <Tab.Screen name="Messages"   component={MessageStack} />
      <Tab.Screen name="Profile"    component={ProfileScreen} />
    </Tab.Navigator>
  );
}
