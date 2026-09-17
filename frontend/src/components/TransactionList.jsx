import React, { useEffect, useState } from 'react'
import { api } from '../services/api'

// Same unmount/remount lifecycle as FraudRings: App.jsx only renders this while
// activeTab === 'transactions', so it's fully torn down (state discarded) when you leave
// the tab and freshly refetched every time you return.
export default function TransactionList() {
  const [txList, setTxList] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Fetches once on mount (empty [] deps). Unlike FraudRings, a failed request surfaces a
  // visible error message instead of silently showing "no data" — see DEMO-DATA-EXPLAINED.md
  // for the real backend 500 this was specifically added to catch instead of hiding.
  useEffect(() => {
    api.getFlaggedTransactions()
      .then(setTxList)
      .catch(err => setError(err.response?.data?.error ?? 'Failed to load flagged transactions'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div style={styles.panel}>
      <h2 style={styles.title}>Flagged Transactions</h2>
      {loading && <p style={styles.dim}>Loading...</p>}
      {error && <p style={styles.error}>{error}</p>}
      {!loading && !error && txList.length === 0 && <p style={styles.dim}>No flagged transactions.</p>}
      <table style={styles.table}>
        <thead>
          <tr>
            {['Transaction ID', 'Amount', 'Status', 'Timestamp'].map(h => (
              <th key={h} style={styles.th}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {txList.map(tx => (
            <tr key={tx.transactionId} style={styles.row}>
              <td style={styles.td}><code>{tx.transactionId}</code></td>
              <td style={styles.td}>${tx.amount?.toFixed(2)}</td>
              <td style={styles.td}>
                <span style={{ ...styles.badge, background: tx.flagged ? '#7f1d1d' : '#14532d', color: tx.flagged ? '#fca5a5' : '#86efac' }}>
                  {tx.status}
                </span>
              </td>
              <td style={styles.td}>{tx.timestamp ? new Date(tx.timestamp).toLocaleString() : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

const styles = {
  panel: { background: '#1e293b', borderRadius: 12, padding: 20 },
  title: { fontSize: 18, fontWeight: 700, marginBottom: 16 },
  dim: { color: '#64748b', fontStyle: 'italic' },
  error: { color: '#ef4444' },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: { textAlign: 'left', padding: '8px 12px', color: '#64748b', fontSize: 12, fontWeight: 600, textTransform: 'uppercase', borderBottom: '1px solid #334155' },
  row: { borderBottom: '1px solid #1e293b' },
  td: { padding: '10px 12px', fontSize: 14, color: '#cbd5e1' },
  badge: { borderRadius: 4, padding: '2px 8px', fontSize: 11, fontWeight: 700 },
}
