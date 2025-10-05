// src/services/api.js
import axios from 'axios';

const API_BASE_URL = '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor - הוסף טוקן לכל בקשה
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

// Interceptor לטיפול בשגיאות
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// Authentication API
export const authAPI = {
  checkStatus: () => api.get('/auth/status'),
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (userData) => api.post('/auth/signup', userData),
  verify: (data) => api.post('/auth/verify', data),
  checkIfVerified: (email) => api.get(`/auth/check-verified/${encodeURIComponent(email)}`),
  resendVerificationCode: (email) => api.post('/auth/resend', null, { 
    params: { email } 
  }),
  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    return api.post('/auth/logout');
  },
  checkUsername: (username) => api.get(`/auth/check-username/${encodeURIComponent(username)}`),
  checkEmail: (email) => api.get(`/auth/check-email/${encodeURIComponent(email)}`)
};

// Sessions API (ללא שינוי)
export const sessionsAPI = {
  getAll: () => api.get('/sessions'),
  getOne: (id) => api.get(`/sessions/${id}`),
  create: (data) => api.post('/sessions', data),
  update: (id, data) => api.put(`/sessions/${id}`, data),
  delete: (id) => api.delete(`/sessions/${id}`),
  chat: (id, data) => api.post(`/sessions/${id}/chat`, data),
  uploadDocument: (id, formData) => api.post(`/sessions/${id}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  getDocuments: (id) => api.get(`/sessions/${id}/documents`),
  deleteDocument: (sessionId, docId) => api.delete(`/sessions/${sessionId}/documents/${docId}`)
};

export default api;