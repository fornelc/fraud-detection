import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'

// The only place App is ever mounted — runs once when index.html loads. App itself is never
// unmounted again for as long as the page stays open, which is why state that must survive
// tab switches (see App.jsx) lives there rather than in any child component.
//
// Note: React.StrictMode intentionally double-invokes effects in development only (mount,
// synthetic unmount, mount again) to surface missing cleanup logic. In dev this means
// alertStream.js's subscribeToAlerts() briefly opens and closes an EventSource before the
// "real" one opens — expected, and doesn't happen in a production build.
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
