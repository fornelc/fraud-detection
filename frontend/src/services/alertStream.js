// Opens the one long-lived SSE connection for live alerts. Called once from App.jsx's mount
// effect; the returned cleanup function closes the connection if App itself ever unmounts
// (in practice: closing the tab or navigating away, not switching between tabs in the app).
export function subscribeToAlerts(onAlert, onError) {
  const es = new EventSource('/api/alerts/stream')

  // Listens specifically for events named "alert" (see AlertBroadcastService.broadcast() on
  // the backend) — a plain 'message' listener would not catch these named SSE events.
  es.addEventListener('alert', (e) => {
    try {
      onAlert(JSON.parse(e.data))
    } catch {
      // malformed event — ignore
    }
  })

  es.onerror = () => {
    if (onError) onError()
    // SSE auto-reconnects by default; we only close on deliberate disconnect
  }

  return () => es.close()
}
