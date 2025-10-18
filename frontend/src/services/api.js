// frontend/src/services/api.js
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
    
    return Promise.reject(error);
  }
);

// ==================== Authentication API ====================
export const authAPI = {
  checkStatus: () => api.get('/auth/status'),
  login: (email, password) => api.post('/auth/login', { email, password }),
  register: (userData) => api.post('/auth/signup', userData),
  verify: (data) => api.post('/auth/verify', data),
  checkIfVerified: (email) => api.get(`/auth/check-verified/${encodeURIComponent(email)}`),
  resendVerificationCode: (email) => api.post('/auth/resend', null, { params: { email } }),
  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    return api.post('/auth/logout');
  },
  checkUsername: (username) => api.get(`/auth/check-username/${encodeURIComponent(username)}`),
  checkEmail: (email) => api.get(`/auth/check-email/${encodeURIComponent(email)}`)
};

// ==================== Chat API (חדש!) ====================
export const chatAPI = {
  /**
   * יצירת שיחה חדשה
   * @param {string} title - כותרת השיחה
   * @param {File[]} files - קבצי PDF
   */
  createChat: (title, files) => {
    const formData = new FormData();
    formData.append('title', title);
    
    files.forEach((file) => {
      formData.append('files', file);
    });

    return api.post('/chats', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  },

  /**
   * קבלת כל השיחות
   */
  getAllChats: () => api.get('/chats'),

  /**
   * חיפוש שיחות
   */
  searchChats: (searchTerm) => api.get('/chats/search', {
    params: { q: searchTerm }
  }),

  /**
   * קבלת שיחה ספציפית
   */
  getChat: (chatId) => api.get(`/chats/${chatId}`),

  /**
   * עדכון כותרת שיחה
   */
  updateChatTitle: (chatId, newTitle) => api.put(`/chats/${chatId}`, {
    title: newTitle
  }),

  /**
   * מחיקת שיחה
   */
  deleteChat: (chatId) => api.delete(`/chats/${chatId}`),

  /**
   * שאילת שאלה
   */
  askQuestion: (chatId, question, contextMessageCount = 5) => {
    return api.post(`/chats/${chatId}/ask`, {
      question,
      contextMessageCount,
      includeFullContext: false
    });
  },

  /**
   * קבלת היסטוריית הודעות
   */
  getChatMessages: (chatId) => api.get(`/chats/${chatId}/messages`),

  /**
   * סטטיסטיקות
   */
  getUserStatistics: () => api.get('/chats/statistics')
};

// ==================== Document API (חדש!) ====================
export const documentAPI = {
  /**
   * קבלת מסמכים של שיחה
   */
  getDocumentsByChat: (chatId) => api.get(`/documents/chat/${chatId}`),

  /**
   * קבלת מסמכים מעובדים בלבד
   */
  getProcessedDocuments: (chatId) => api.get(`/documents/chat/${chatId}/processed`),

  /**
   * קבלת מסמך ספציפי
   */
  getDocument: (documentId) => api.get(`/documents/${documentId}`),

  /**
   * הורדת מסמך
   */
  downloadDocument: (documentId) => {
    return api.get(`/documents/${documentId}/download`, {
      responseType: 'blob'
    });
  },

  /**
   * קבלת URL זמני להורדה
   */
  getDownloadUrl: (documentId) => api.get(`/documents/${documentId}/download-url`),

  /**
   * מחיקת מסמך
   */
  deleteDocument: (documentId) => api.delete(`/documents/${documentId}`),

  /**
   * סטטיסטיקות מסמכים
   */
  getDocumentStatistics: (chatId) => api.get(`/documents/chat/${chatId}/stats`)
};

export default api;