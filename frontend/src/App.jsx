import React, { useEffect, useState } from 'react'
import AlertPanel from './components/AlertPanel'
import RiskScoreCard from './components/RiskScoreCard'
import TransactionList from './components/TransactionList'
import FraudRings from './components/FraudRings'
import { subscribeToAlerts } from './services/alertStream'
import { api } from './services/api'

// Root component — the only one that never unmounts while the page is open (mounted once by
// main.jsx). Owns every piece of state that either must survive tab switches (alerts,
// sseConnected — see FRONTEND-ARCHITECTURE.md for why) or belongs to the Ingest form, which
// is written inline below rather than as its own component.
export default function App() {
  const [alerts, setAlerts] = useState([])
  const [sseConnected, setSseConnected] = useState(false)
  const [ingestForm, setIngestForm] = useState({
    transactionId: '', accountId: '', accountName: '', accountEmail: '',
    amount: '', deviceId: '', deviceType: 'mobile', deviceFingerprint: '',
    ipAddress: '', ipCountry: 'US', merchantId: '', merchantName: '', merchantCategory: 'retail',
  })
  const [ingestResult, setIngestResult] = useState(null)
  const [ingestError, setIngestError] = useState(null)
  const [activeTab, setActiveTab] = useState('overview')

  // Runs once on mount (empty [] deps), not on every re-render: fetches past alerts so the
  // feed isn't empty on first load, then opens the long-lived SSE connection for future ones.
  // The cleanup function (return unsub) only runs if App itself unmounts — in practice that
  // means closing the tab or navigating away, never just switching tabs within the app.
  useEffect(() => {
    api.getAlertHistory().then(setAlerts).catch(console.error)

    const unsub = subscribeToAlerts(
      (alert) => {
        setSseConnected(true)
        setAlerts(prev => [alert, ...prev].slice(0, 50))
      },
      () => setSseConnected(false)
    )
    setSseConnected(true)
    return unsub
  }, [])

  // Submits the Ingest form. Clears any previous error before each attempt, but note
  // ingestResult from a prior successful submission is NOT cleared here — a failed retry
  // right after a success will show the old result and the new error at the same time.
  const handleIngest = async (e) => {
    e.preventDefault()
    setIngestError(null)
    try {
      const result = await api.postTransaction({
        ...ingestForm,
        amount: parseFloat(ingestForm.amount),
      })
      setIngestResult(result)
    } catch (err) {
      setIngestError(err.response?.data?.error ?? 'Request failed')
    }
  }

  const tabs = ['overview', 'ingest', 'rings', 'transactions']

  return (
    <div style={styles.app}>
      {/* Always rendered, regardless of activeTab — never conditionally mounted/unmounted.
          That's exactly why sseConnected has to live in App rather than in a child: this is
          the only place able to show it on every tab. */}
      <header style={styles.header}>
        <div style={styles.headerInner}>
          <h1 style={styles.logo}>🛡 Fraud Detection Engine</h1>
          <div style={styles.sseStatus}>
            <span style={{ ...styles.dot, background: sseConnected ? '#22c55e' : '#ef4444' }} />
            {sseConnected ? 'Live' : 'Disconnected'}
          </div>
        </div>
        <nav style={styles.nav}>
          {tabs.map(t => (
            <button key={t} style={{ ...styles.tab, ...(activeTab === t ? styles.tabActive : {}) }} onClick={() => setActiveTab(t)}>
              {t.charAt(0).toUpperCase() + t.slice(1)}
            </button>
          ))}
        </nav>
      </header>

      <main style={styles.main}>
        {/* AlertPanel is "dumb" — just renders the alerts prop, nothing lost if it unmounts.
            RiskScoreCard manages its own state and IS unmounted/remounted every time you
            leave and return to this tab, losing whatever it was showing. */}
        {activeTab === 'overview' && (
          <div style={styles.grid}>
            <div style={{ gridColumn: '1 / -1' }}><AlertPanel alerts={alerts} /></div>
            <RiskScoreCard />
          </div>
        )}

        {/* Written inline rather than as its own component — which is why ingestForm/
            ingestResult/ingestError live in App above instead of a local useState here. */}
        {activeTab === 'ingest' && (
          <div style={styles.card}>
            <h2 style={styles.cardTitle}>Ingest Transaction</h2>
            <form onSubmit={handleIngest} style={styles.formGrid}>
              {Object.entries(ingestForm).map(([key, val]) => (
                <div key={key} style={styles.field}>
                  <label style={styles.label}>{key}</label>
                  <input
                    style={styles.input}
                    value={val}
                    onChange={e => setIngestForm(f => ({ ...f, [key]: e.target.value }))}
                    placeholder={key}
                  />
                </div>
              ))}
              <button style={{ ...styles.submitBtn, gridColumn: '1 / -1' }} type="submit">
                Submit Transaction
              </button>
            </form>

            {ingestError && <p style={styles.error}>{ingestError}</p>}
            {ingestResult && (
              <div style={styles.result}>
                <p style={{ color: ingestResult.flagged ? '#ef4444' : '#22c55e', fontWeight: 700 }}>
                  {ingestResult.flagged ? '⚠ FLAGGED — High Risk' : '✓ Transaction accepted'}
                </p>
                <pre style={styles.pre}>{JSON.stringify(ingestResult, null, 2)}</pre>
              </div>
            )}
          </div>
        )}

        {/* Both fully unmount when you leave their tab and mount a brand-new instance when you
            return — that remount is what triggers each one's own mount effect to refetch,
            with no explicit "refresh on tab switch" logic written anywhere in this file. */}
        {activeTab === 'rings' && <FraudRings />}
        {activeTab === 'transactions' && <TransactionList />}
      </main>
    </div>
  )
}

const styles = {
  app: { minHeight: '100vh', background: '#0f172a' },
  header: { background: '#1e293b', borderBottom: '1px solid #334155', padding: '0 24px' },
  headerInner: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 0' },
  logo: { fontSize: 20, fontWeight: 800, color: '#e2e8f0' },
  sseStatus: { display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#94a3b8' },
  dot: { display: 'inline-block', width: 8, height: 8, borderRadius: '50%' },
  nav: { display: 'flex', gap: 4, paddingBottom: 0 },
  tab: { background: 'none', border: 'none', color: '#64748b', padding: '12px 16px', cursor: 'pointer', fontSize: 14, fontWeight: 500, borderBottom: '2px solid transparent' },
  tabActive: { color: '#3b82f6', borderBottom: '2px solid #3b82f6' },
  main: { padding: 24, maxWidth: 1200, margin: '0 auto' },
  grid: { display: 'grid', gridTemplateColumns: '1fr 380px', gap: 20, alignItems: 'start' },
  card: { background: '#1e293b', borderRadius: 12, padding: 24 },
  cardTitle: { fontSize: 18, fontWeight: 700, marginBottom: 20 },
  formGrid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 },
  field: { display: 'flex', flexDirection: 'column', gap: 4 },
  label: { fontSize: 12, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' },
  input: { background: '#0f172a', border: '1px solid #334155', borderRadius: 6, padding: '8px 12px', color: '#e2e8f0', fontSize: 14 },
  submitBtn: { background: '#3b82f6', color: '#fff', border: 'none', borderRadius: 8, padding: '12px', cursor: 'pointer', fontWeight: 700, fontSize: 15, marginTop: 8 },
  error: { color: '#ef4444', marginTop: 12 },
  result: { marginTop: 16, background: '#0f172a', borderRadius: 8, padding: 16 },
  pre: { color: '#94a3b8', fontSize: 12, marginTop: 8, overflowX: 'auto' },
}
