// frontend/src/services/api.js
import axios from 'axios';

const API_BASE_URL = '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor - add token to every request
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

// Interceptor for error handling
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
  checkEmail: (email) => api.get(`/auth/check-email/${encodeURIComponent(email)}`),
  googleLogin: (credential) => api.post('/auth/google', { credential })
};

// ==================== Chat API ====================
export const chatAPI = {
  /**
   * Create new chat
   * @param {string} title - chat title
   * @param {File[]} files - PDF files
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
   * Get all chats
   */
  getAllChats: () => api.get('/chats'),

  /**
   * Search chats
   */
  searchChats: (searchTerm) => api.get('/chats/search', {
    params: { q: searchTerm }
  }),

  /**
   * Get specific chat
   */
  getChat: (chatId) => api.get(`/chats/${chatId}`),

  /**
   * Update chat title
   */
  updateChatTitle: (chatId, newTitle) => api.put(`/chats/${chatId}`, {
    title: newTitle
  }),

  /**
   * Delete chat
   */
  deleteChat: (chatId) => api.delete(`/chats/${chatId}`),

  /**
   * Get detailed processing status
   */
  getProcessingStatus: (chatId) => api.get(`/chats/${chatId}/processing-status`),

  /**
   * Ask a question
   */
  askQuestion: (chatId, question, contextMessageCount = 5) => {
    return api.post(`/chats/${chatId}/ask`, {
      question,
      contextMessageCount,
      includeFullContext: false
    });
  },

  /**
   * Get message history
   */
  getChatMessages: (chatId) => api.get(`/chats/${chatId}/messages`),

  /**
   * Get statistics
   */
  getUserStatistics: () => api.get('/chats/statistics')
};

// ==================== Document API ====================
export const documentAPI = {
  /**
   * Get documents for a chat
   */
  getDocumentsByChat: (chatId) => api.get(`/documents/chat/${chatId}`),

  /**
   * Get processed documents only
   */
  getProcessedDocuments: (chatId) => api.get(`/documents/chat/${chatId}/processed`),

  /**
   * Get specific document
   */
  getDocument: (documentId) => api.get(`/documents/${documentId}`),

  /**
   * Download document
   */
  downloadDocument: (documentId) => {
    return api.get(`/documents/${documentId}/download`, {
      responseType: 'blob'
    });
  },

  /**
   * Get temporary download URL
   */
  getDownloadUrl: (documentId) => api.get(`/documents/${documentId}/download-url`),

  /**
   * Delete document
   */
  deleteDocument: (documentId) => api.delete(`/documents/${documentId}`),

  /**
   * Get document statistics
   */
  getDocumentStatistics: (chatId) => api.get(`/documents/chat/${chatId}/stats`)
};

export default api;