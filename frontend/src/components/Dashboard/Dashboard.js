// frontend/src/components/Dashboard/Dashboard.js
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI, chatAPI } from '../../services/api';
import Sidebar from './Sidebar';
import ChatArea from './ChatArea';
import NewSessionModal from './NewSessionModal';
import './Dashboard.css';

const Dashboard = () => {
  // ==================== State ====================
  const [currentUser, setCurrentUser] = useState(null);
  const [currentSession, setCurrentSession] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [messages, setMessages] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [showNewSessionModal, setShowNewSessionModal] = useState(false);
  const [toast, setToast] = useState({ show: false, message: '', type: 'success' });
  const [loading, setLoading] = useState(false);
  
  const navigate = useNavigate();

  // ==================== Effects ====================
  useEffect(() => {
    checkAuth();
  }, []);

  useEffect(() => {
    if (currentUser) {
      loadSessions();
    }
  }, [currentUser]);

  // ==================== Auth Functions ====================
  const checkAuth = async () => {
    try {
      const response = await authAPI.checkStatus();
      if (response.data.success && response.data.authenticated && response.data.user) {
        setCurrentUser(response.data.user);
      } else {
        navigate('/login');
      }
    } catch (error) {
      console.error('Error checking auth:', error);
      navigate('/login');
    }
  };

  const logout = async () => {
    if (!window.confirm('האם אתה בטוח שברצונך להתנתק?')) return;

    try {
      await authAPI.logout();
      navigate('/login');
    } catch (error) {
      console.error('Logout error:', error);
      showToast('שגיאה בהתנתקות', 'error');
    }
  };

  // ==================== Session Functions ====================
  const loadSessions = async () => {
    try {
      setLoading(true);
      const response = await chatAPI.getAllChats();
      
      if (response.data.success) {
        const chatsData = response.data.data.chats.map(chat => ({
          id: chat.id,
          title: chat.title,
          documentsCount: chat.documentCount,
          messagesCount: chat.messageCount,
          lastActivityAt: formatDateTime(chat.lastActivityAt),
          createdAt: formatDateTime(chat.createdAt),
          isReady: chat.isReady,
          status: chat.status,
          pendingDocuments: chat.pendingDocuments,
          documentCount: chat.documentCount
        }));
        
        setSessions(chatsData);
      } else {
        showToast('שגיאה בטעינת שיחות', 'error');
      }
    } catch (error) {
      console.error('Error loading sessions:', error);
      showToast('שגיאה בחיבור לשרת', 'error');
    } finally {
      setLoading(false);
    }
  };

  const createNewSession = () => {
    setShowNewSessionModal(true);
  };

  const submitNewSession = async (successfulCompletion) => {
    // אם קיבלנו true - זה אומר שהעיבוד הסתיים בהצלחה
    if (successfulCompletion === true) {
      setShowNewSessionModal(false);
      showToast('✅ שיחה חדשה נוצרה והמסמכים עובדו בהצלחה!', 'success');
      
      // טען מחדש את רשימת השיחות
      await loadSessions();
      
      return;
    }
  };

  const loadSession = async (sessionId) => {
    try {
      setLoading(true);
      console.log('📥 Loading session:', sessionId);
      
      const response = await chatAPI.getChat(sessionId);
      
      if (response.data.success) {
        const chatData = response.data.data;
        
        setCurrentSession({
          id: chatData.id,
          title: chatData.title,
          documentsCount: chatData.documentCount,
          messagesCount: chatData.messageCount,
          isReady: chatData.isReady,
          status: chatData.status,
          pendingDocuments: chatData.pendingDocuments,
          documentCount: chatData.documentCount
        });
        
        await loadMessages(sessionId);
        
        showToast('שיחה נטענה בהצלחה', 'success');
      } else {
        showToast(response.data.error || 'שגיאה בטעינת שיחה', 'error');
      }
    } catch (error) {
      console.error('❌ Error loading session:', error);
      showToast('שגיאה בטעינת שיחה', 'error');
    } finally {
      setLoading(false);
    }
  };

  const loadMessages = async (chatId) => {
    try {
      const response = await chatAPI.getChatMessages(chatId);
      
      if (response.data.success) {
        const messagesData = response.data.data.map(msg => ({
          role: msg.role.toLowerCase(),
          content: msg.content,
          timestamp: msg.createdAt,
          confidenceScore: msg.confidenceScore,
          sources: msg.sources
        }));
        
        setMessages(messagesData);
      }
    } catch (error) {
      console.error('❌ Error loading messages:', error);
    }
  };

  const deleteSession = async (sessionId, e) => {
    if (e) e.stopPropagation();
    
    if (!window.confirm('האם אתה בטוח שברצונך למחוק שיחה זו?')) return;

    try {
      console.log('🗑️ Deleting session:', sessionId);
      
      const response = await chatAPI.deleteChat(sessionId);
      
      if (response.data.success) {
        showToast('✅ שיחה נמחקה בהצלחה', 'success');
        
        if (currentSession && currentSession.id === sessionId) {
          setCurrentSession(null);
          setMessages([]);
        }
        
        await loadSessions();
      } else {
        showToast(response.data.error || 'שגיאה במחיקת שיחה', 'error');
      }
    } catch (error) {
      console.error('❌ Error deleting session:', error);
      showToast('שגיאה במחיקת שיחה', 'error');
    }
  };

  // ==================== Message Functions ====================
  const sendMessage = async (text) => {
    if (!currentSession) {
      showToast('נא לבחור שיחה תחילה', 'error');
      return false;
    }

    if (!text.trim()) {
      showToast('נא להזין שאלה', 'error');
      return false;
    }

    if (!currentSession.isReady || currentSession.status === 'PROCESSING') {
      showToast('⏳ השיחה עדיין מעבדת מסמכים. אנא המתן...', 'warning');
      return false;
    }

    const userMessage = {
      role: 'user',
      content: text,
      timestamp: new Date().toISOString()
    };
    setMessages(prev => [...prev, userMessage]);

    try {
      console.log('💬 Sending message:', text);
      
      const response = await chatAPI.askQuestion(currentSession.id, text);

      if (response.data.success) {
        const answerData = response.data.data;
        
        const assistantMessage = {
          role: 'assistant',
          content: answerData.answer,
          timestamp: answerData.timestamp,
          confidenceScore: answerData.confidence,
          sources: answerData.sources
        };
        
        setMessages(prev => [...prev, assistantMessage]);
        
        if (answerData.confidence < 0.5) {
          showToast('⚠️ רמת ביטחון נמוכה בתשובה', 'warning');
        }
        
        return true;
      } else {
        setMessages(prev => prev.filter(msg => msg !== userMessage));
        showToast(response.data.error || 'שגיאה בשליחת הודעה', 'error');
        return false;
      }
    } catch (error) {
      console.error('❌ Error sending message:', error);
      
      setMessages(prev => prev.filter(msg => msg !== userMessage));
      
      if (error.response?.data?.error) {
        showToast(error.response.data.error, 'error');
      } else {
        showToast('שגיאה בשליחת הודעה', 'error');
      }
      return false;
    }
  };

  // ==================== Helper Functions ====================
  const formatDateTime = (dateTimeString) => {
    if (!dateTimeString) return '';
    
    const date = new Date(dateTimeString);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'עכשיו';
    if (diffMins < 60) return `לפני ${diffMins} דקות`;
    if (diffHours < 24) return `לפני ${diffHours} שעות`;
    if (diffDays < 7) return `לפני ${diffDays} ימים`;
    
    return date.toLocaleDateString('he-IL');
  };

  const showToast = (message, type = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => {
      setToast({ show: false, message: '', type: 'success' });
    }, 3000);
  };

  const filteredSessions = sessions.filter(session =>
    session.title.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // ==================== Render ====================
  return (
    <div className="dashboard">
      {/* ==================== Header ==================== */}
      <header className="header">
        <div className="logo" onClick={() => window.location.reload()}>
          📚 Smart Document Chat
        </div>
        <div className="user-info">
          <span className="welcome-text">
            שלום, {currentUser?.fullName || currentUser?.username}
          </span>
          <button className="logout-btn" onClick={logout}>
            התנתק
          </button>
        </div>
      </header>

      {/* ==================== Main Layout ==================== */}
      <div className="main-layout">
        <Sidebar
          sessions={filteredSessions}
          currentSession={currentSession}
          searchTerm={searchTerm}
          onSearchChange={setSearchTerm}
          onNewSession={createNewSession}
          onSelectSession={loadSession}
          onDeleteSession={deleteSession}
        />

        <ChatArea
          currentSession={currentSession}
          messages={messages}
          onSendMessage={sendMessage}
          onShowDocuments={() => {}}
          currentUser={currentUser}
        />
      </div>

      {/* ==================== Modals ==================== */}
      
      {/* New Session Modal */}
      {showNewSessionModal && (
        <NewSessionModal
          onClose={() => setShowNewSessionModal(false)}
          onSubmit={submitNewSession}
        />
      )}

      {/* Toast Notifications */}
      {toast.show && (
        <div className={`toast ${toast.type} show`}>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Global Loading Spinner */}
      {loading && (
        <div style={{
          position: 'fixed',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          background: 'white',
          padding: '30px',
          borderRadius: '12px',
          boxShadow: '0 4px 20px rgba(0,0,0,0.2)',
          zIndex: 9999
        }}>
          <div className="spinner"></div>
          <p style={{ marginTop: '15px', textAlign: 'center' }}>טוען...</p>
        </div>
      )}
    </div>
  );
};

export default Dashboard;