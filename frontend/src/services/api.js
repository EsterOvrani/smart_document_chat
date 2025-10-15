// src/services/api.js
import axios from 'axios';

const API_BASE_URL = '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// ✅ Interceptor - הוסף טוקן לכל בקשה
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    // ✅ Debug: הדפס כל בקשה
    console.log('🔵 API Request:', {
      method: config.method.toUpperCase(),
      url: config.url,
      hasToken: !!token,
      data: config.data
    });
    
    return config;
  },
  error => {
    console.error('❌ Request Error:', error);
    return Promise.reject(error);
  }
);

// ✅ Interceptor לטיפול בשגיאות
api.interceptors.response.use(
  response => {
    console.log('✅ API Response:', {
      url: response.config.url,
      status: response.status,
      data: response.data
    });
    return response;
  },
  error => {
    console.error('❌ API Error:', {
      url: error.config?.url,
      status: error.response?.status,
      message: error.message,
      data: error.response?.data
    });
    
    if (error.response?.status === 401) {
      console.warn('⚠️ Unauthorized - Redirecting to login');
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login';
      }
    }
    
    if (error.response?.status === 403) {
      console.error('🚫 Forbidden - Check permissions');
    }
    
    return Promise.reject(error);
  }
);

// ✅ Authentication API
export const authAPI = {
  checkStatus: () => {
    console.log('🔍 Checking auth status...');
    return api.get('/auth/status');
  },
  
  login: (email, password) => {
    console.log('🔑 Attempting login for:', email);
    return api.post('/auth/login', { email, password });
  },
  
  register: (userData) => {
    console.log('📝 Registering user:', userData.email);
    return api.post('/auth/signup', userData);
  },
  
  verify: (data) => {
    console.log('✅ Verifying email:', data.email);
    return api.post('/auth/verify', data);
  },
  
  checkIfVerified: (email) => {
    return api.get(`/auth/check-verified/${encodeURIComponent(email)}`);
  },
  
  resendVerificationCode: (email) => {
    return api.post('/auth/resend', null, { params: { email } });
  },
  
  logout: () => {
    console.log('👋 Logging out...');
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    return api.post('/auth/logout');
  },
  
  checkUsername: (username) => {
    return api.get(`/auth/check-username/${encodeURIComponent(username)}`);
  },
  
  checkEmail: (email) => {
    return api.get(`/auth/check-email/${encodeURIComponent(email)}`);
  }
};

// ✅ Sessions API - TODO: להטמיע בעתיד כשיהיה SessionController
export const sessionsAPI = {
  // TODO: כל הפונקציות האלה יוטמעו בשלב הבא
  getAll: () => {
    console.log('⚠️ TODO: sessionsAPI.getAll - יוטמע בעתיד');
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  getOne: (id) => {
    console.log('⚠️ TODO: sessionsAPI.getOne - יוטמע בעתיד', id);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  create: (data) => {
    console.log('⚠️ TODO: sessionsAPI.create - יוטמע בעתיד', data.title);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  update: (id, data) => {
    console.log('⚠️ TODO: sessionsAPI.update - יוטמע בעתיד', id);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  delete: (id) => {
    console.log('⚠️ TODO: sessionsAPI.delete - יוטמע בעתיד', id);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  chat: (id, data) => {
    console.log('⚠️ TODO: sessionsAPI.chat - יוטמע בעתיד', id);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  uploadDocument: (id, formData) => {
    console.log('⚠️ TODO: sessionsAPI.uploadDocument - יוטמע בעתיד', id);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  getDocuments: (id) => {
    console.log('⚠️ TODO: sessionsAPI.getDocuments - יוטמע בעתיד', id);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  },
  
  deleteDocument: (sessionId, docId) => {
    console.log('⚠️ TODO: sessionsAPI.deleteDocument - יוטמע בעתיד', docId);
    return Promise.reject(new Error('Sessions API not implemented yet'));
  }
};

export default api;