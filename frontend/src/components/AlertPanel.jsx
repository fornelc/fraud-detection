import React from 'react'

const levelColor = { HIGH: '#ef4444', MEDIUM: '#f97316', LOW: '#22c55e' }

// Presentational ("dumb") component — takes alerts as a prop and only renders it, with no
// state or data-fetching of its own. The real SSE subscription and alert history live in
// App.jsx, which never unmounts; keeping this component dumb avoids a second, competing
// source of truth for the same data. It IS unmounted/remounted whenever you leave and return
// to the Overview tab, but that's harmless here — there's no local state to lose.
export default function AlertPanel({ alerts }) {
  return (
    <div style={styles.panel}>
      <h2 style={styles.title}>
        <span style={styles.dot} />
        Live Alerts
        <span style={styles.badge}>{alerts.length}</span>
      </h2>

      {alerts.length === 0 && (
        <p style={styles.empty}>No alerts yet — monitoring for suspicious activity...</p>
      )}

      <ul style={styles.list}>
        {alerts.map((a, i) => (
          <li key={i} style={{ ...styles.item, borderLeft: `4px solid ${levelColor[a.riskLevel] ?? '#64748b'}` }}>
            <div style={styles.itemHeader}>
              <span style={{ color: levelColor[a.riskLevel], fontWeight: 700 }}>{a.riskLevel}</span>
              <span style={styles.time}>{new Date(a.timestamp).toLocaleTimeString()}</span>
            </div>
            <p style={styles.message}>{a.message}</p>
            <div style={styles.meta}>
              <code>account: {a.accountId}</code>
              <code>tx: {a.transactionId}</code>
              <code>score: {(a.riskScore * 100).toFixed(0)}%</code>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

const styles = {
  panel: { background: '#1e293b', borderRadius: 12, padding: 20, minHeight: 200 },
  title: { display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16, fontSize: 18, fontWeight: 700 },
  dot: { display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: '#ef4444', animation: 'pulse 1.5s infinite' },
  badge: { marginLeft: 'auto', background: '#334155', borderRadius: 999, padding: '2px 10px', fontSize: 13 },
  empty: { color: '#64748b', fontStyle: 'italic' },
  list: { listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 },
  item: { background: '#0f172a', borderRadius: 8, padding: '12px 14px', paddingLeft: 14 },
  itemHeader: { display: 'flex', justifyContent: 'space-between', marginBottom: 6 },
  time: { color: '#64748b', fontSize: 12 },
  message: { marginBottom: 8, color: '#cbd5e1' },
  meta: { display: 'flex', gap: 12, flexWrap: 'wrap', fontSize: 12, color: '#94a3b8' },
}
