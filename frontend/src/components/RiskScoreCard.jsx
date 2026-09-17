import React, { useState } from 'react'
import { api } from '../services/api'

const levelColor = { HIGH: '#ef4444', MEDIUM: '#f97316', LOW: '#22c55e' }

// Manages its own local state — unlike AlertPanel, nothing here needs to be shared outside
// this tab. Because App.jsx only renders this while activeTab === 'overview', it fully
// unmounts when you leave the tab and mounts fresh when you return, resetting
// accountId/result/error back to their initial values every time (no lookup is remembered).
export default function RiskScoreCard() {
  const [accountId, setAccountId] = useState('')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  // Looks up one account's risk score on demand — no useEffect here, so nothing fetches
  // automatically; the user has to submit the form.
  const lookup = async (e) => {
    e.preventDefault()
    if (!accountId.trim()) return
    setLoading(true)
    setError(null)
    try {
      setResult(await api.getRiskScore(accountId.trim()))
    } catch (err) {
      setError(err.response?.data?.error ?? 'Request failed')
    } finally {
      setLoading(false)
    }
  }

  // The only way to blacklist an account from the UI at all — see DEMO-DATA-EXPLAINED.md's
  // "manual blacklisting" discussion for why this is a deliberate human-in-the-loop action
  // rather than something the system does automatically. Updates local state immediately
  // ("optimistically") to reflect the change without waiting for a fresh lookup call.
  const blacklist = async () => {
    if (!result) return
    await api.blacklistAccount(result.accountId)
    setResult(r => ({ ...r, blacklisted: true, riskScore: 1.0, riskLevel: 'HIGH' }))
  }

  return (
    <div style={styles.card}>
      <h2 style={styles.title}>Risk Score Lookup</h2>
      <form onSubmit={lookup} style={styles.form}>
        <input
          style={styles.input}
          placeholder="Account ID..."
          value={accountId}
          onChange={e => setAccountId(e.target.value)}
        />
        <button style={styles.btn} type="submit" disabled={loading}>
          {loading ? '...' : 'Check'}
        </button>
      </form>

      {error && <p style={styles.error}>{error}</p>}

      {result && (
        <div style={styles.result}>
          <div style={styles.scoreRow}>
            <div style={{ ...styles.gauge, background: levelColor[result.riskLevel] }}>
              {(result.riskScore * 100).toFixed(0)}%
            </div>
            <div>
              <p style={{ color: levelColor[result.riskLevel], fontWeight: 700, fontSize: 20 }}>
                {result.riskLevel}
              </p>
              <p style={styles.meta}>Account: <code>{result.accountId}</code></p>
              <p style={styles.meta}>Distance to blacklisted: <code>{result.minDistanceToBlacklisted < 0 ? 'N/A' : result.minDistanceToBlacklisted}</code></p>
              <p style={styles.meta}>Blacklisted: <code>{result.blacklisted ? 'YES' : 'No'}</code></p>
            </div>
          </div>
          {!result.blacklisted && (
            <button style={{ ...styles.btn, background: '#dc2626', marginTop: 12 }} onClick={blacklist}>
              Blacklist Account
            </button>
          )}
        </div>
      )}
    </div>
  )
}

const styles = {
  card: { background: '#1e293b', borderRadius: 12, padding: 20 },
  title: { fontSize: 18, fontWeight: 700, marginBottom: 16 },
  form: { display: 'flex', gap: 8, marginBottom: 12 },
  input: { flex: 1, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, padding: '8px 12px', color: '#e2e8f0', fontSize: 14 },
  btn: { background: '#3b82f6', color: '#fff', border: 'none', borderRadius: 6, padding: '8px 16px', cursor: 'pointer', fontWeight: 600 },
  error: { color: '#ef4444', fontSize: 13 },
  result: { marginTop: 12 },
  scoreRow: { display: 'flex', alignItems: 'center', gap: 20 },
  gauge: { width: 70, height: 70, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 18, color: '#fff', flexShrink: 0 },
  meta: { color: '#94a3b8', fontSize: 13, marginTop: 4 },
}
