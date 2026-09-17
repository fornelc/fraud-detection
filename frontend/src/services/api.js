import axios from 'axios'

// Every REST call the frontend makes goes through this one axios instance. baseURL is
// relative ('/api'), not an absolute host — Vite's dev-server proxy (vite.config.js) or
// nginx (nginx.conf, in Docker) resolves it to the real backend address, so nothing in this
// file — or any component using it — ever needs to know where the backend actually is.
const http = axios.create({ baseURL: '/api' })

export const api = {
  postTransaction: (payload) => http.post('/transactions', payload).then(r => r.data),
  getFlaggedTransactions: () => http.get('/transactions/flagged').then(r => r.data),
  getTransactionsByAccount: (id) => http.get(`/transactions/account/${id}`).then(r => r.data),
  getFraudRings: () => http.get('/fraud/rings').then(r => r.data),
  getRiskScore: (accountId) => http.get(`/fraud/accounts/${accountId}/risk-score`).then(r => r.data),
  blacklistAccount: (accountId) => http.post(`/fraud/accounts/${accountId}/blacklist`).then(r => r.data),
  getBlacklisted: () => http.get('/fraud/accounts/blacklisted').then(r => r.data),
  getAlertHistory: () => http.get('/alerts/history').then(r => r.data),
}
