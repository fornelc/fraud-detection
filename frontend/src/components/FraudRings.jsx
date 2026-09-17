import React, { useEffect, useState } from 'react'
import { api } from '../services/api'

// Manages its own local state and fetches independently. Because App.jsx only renders this
// while activeTab === 'rings', leaving the tab unmounts it entirely (discarding rings and
// loading), and returning creates a brand-new instance — which is exactly why the mount
// effect below fires again on every visit, with no explicit "refresh on tab switch" logic
// written anywhere; it falls directly out of React's mount/unmount behavior.
export default function FraudRings() {
  const [rings, setRings] = useState([])
  const [loading, setLoading] = useState(true)

  // Shared by both the mount effect below and the manual Refresh button — same fetch either way.
  const refresh = () => {
    setLoading(true)
    api.getFraudRings()
      .then(setRings)
      .catch(console.error)
      .finally(() => setLoading(false))
  }

  // Empty [] dependency array: runs once per mount, i.e. once every time this component is
  // (re)created — not on every re-render.
  useEffect(refresh, [])

  return (
    <div style={styles.panel}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={styles.title}>Fraud Rings</h2>
        <button style={styles.btn} onClick={refresh}>Refresh</button>
      </div>

      {loading && <p style={styles.dim}>Scanning graph for fraud rings...</p>}
      {!loading && rings.length === 0 && <p style={styles.dim}>No fraud rings detected.</p>}

      <div style={styles.grid}>
        {rings.map((r, i) => (
          <div key={i} style={styles.card}>
            <div style={styles.accounts}>
              <span style={styles.account}>{r.account1Id}</span>
              <span style={styles.link}>↔ linked via</span>
              <span style={styles.account}>{r.account2Id}</span>
            </div>
            {r.sharedDevices?.length > 0 && (
              <p style={styles.detail}>Shared devices: {r.sharedDevices.join(', ')}</p>
            )}
            {r.sharedIpAddresses?.length > 0 && (
              <p style={styles.detail}>Shared IPs: {r.sharedIpAddresses.join(', ')}</p>
            )}
            <p style={styles.count}>{r.sharedTransactionCount} shared transaction(s)</p>
          </div>
        ))}
      </div>
    </div>
  )
}

const styles = {
  panel: { background: '#1e293b', borderRadius: 12, padding: 20 },
  title: { fontSize: 18, fontWeight: 700 },
  btn: { background: '#334155', color: '#e2e8f0', border: 'none', borderRadius: 6, padding: '6px 14px', cursor: 'pointer', fontSize: 13 },
  dim: { color: '#64748b', fontStyle: 'italic' },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 },
  card: { background: '#0f172a', borderRadius: 8, padding: 14, border: '1px solid #ef4444' },
  accounts: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 8 },
  account: { background: '#1e293b', borderRadius: 4, padding: '2px 8px', fontSize: 13, fontWeight: 600, color: '#f87171' },
  link: { color: '#64748b', fontSize: 12 },
  detail: { color: '#94a3b8', fontSize: 12, marginBottom: 4 },
  count: { color: '#f97316', fontSize: 12, fontWeight: 600, marginTop: 6 },
}
