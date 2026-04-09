import React, { useEffect } from 'react';
import { RootNavigator } from './src/navigation/RootNavigator';
import { openEventStore } from './src/db/events';

export default function App() {
  useEffect(() => {
    openEventStore().catch(console.error);
  }, []);

  return <RootNavigator />;
}
