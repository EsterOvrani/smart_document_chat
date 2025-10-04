// src/services/api.js
import axios from 'axios';

// ה-API URL יהיה יחסי - NGINX ינתב את /api/* לשרת
const API_BASE_URL = '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true, // חשוב לשליחת cookies
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor לטיפול בשגיאות
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // Redirect to login only if not already on login page
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
  login: (username, password) => api.post('/auth/login', { username, password }),
  register: (userData) => api.post('/auth/register', userData),
  logout: () => api.post('/auth/logout'),
  checkUsername: (username) => api.get(`/auth/check-username/${encodeURIComponent(username)}`),
  checkEmail: (email) => api.get(`/auth/check-email/${encodeURIComponent(email)}`)
};

// Sessions API
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