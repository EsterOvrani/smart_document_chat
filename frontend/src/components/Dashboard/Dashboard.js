// frontend/src/components/Dashboard/Dashboard.js
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI, chatAPI } from '../../services/api'; // ✅ ייבוא chatAPI
import Sidebar from './Sidebar';
import ChatArea from './ChatArea';
import NewSessionModal from './NewSessionModal';
import './Dashboard.css';

const Dashboard = () => {
  const [currentUser, setCurrentUser] = useState(null);
  const [currentSession, setCurrentSession] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [messages, setMessages] = useState([]);
  const [uploadedFiles, setUploadedFiles] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [showNewSessionModal, setShowNewSessionModal] = useState(false);
  const [toast, setToast] = useState({ show: false, message: '', type: 'success' });
  const [loading, setLoading] = useState(false);
  
  const navigate = useNavigate();

  useEffect(() => {
    checkAuth();
  }, []);

  // ✅ כשמשתמש מחובר - טען את השיחות
  useEffect(() => {
    if (currentUser) {
      loadSessions();
    }
  }, [currentUser]);

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

  // ==================== ✅ טעינת שיחות ====================
  const loadSessions = async () => {
    try {
      setLoading(true);
      const response = await chatAPI.getAllChats();
      
      if (response.data.success) {
        // המרת הנתונים לפורמט שהקומפוננטות מצפות לו
        const chatsData = response.data.data.chats.map(chat => ({
          id: chat.id,
          title: chat.title,
          documentsCount: chat.documentCount,
          messagesCount: chat.messageCount,
          lastActivityAt: formatDateTime(chat.lastActivityAt),
          createdAt: formatDateTime(chat.createdAt),
          isReady: chat.isReady,
          status: chat.status
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

  // פונקציה לפורמט תאריך
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

  // ==================== ✅ יצירת שיחה חדשה ====================
  const createNewSession = () => {
    setShowNewSessionModal(true);
  };

  const submitNewSession = async (title, files) => {
    if (!title.trim()) {
      showToast('נא להזין כותרת לשיחה', 'error');
      return;
    }

    if (!files || files.length === 0) {
      showToast('נא להעלות לפחות קובץ אחד', 'error');
      return;
    }

    try {
      console.log('📤 Creating new chat:', { title, filesCount: files.length });
      
      const response = await chatAPI.createChat(title, files);
      
      console.log('✅ Chat created:', response.data);

      if (response.data.success) {
        setShowNewSessionModal(false);
        showToast('✅ שיחה חדשה נוצרה בהצלחה! מעבד מסמכים...', 'success');
        
        // טען מחדש את רשימת השיחות
        await loadSessions();
        
        // טען את השיחה החדשה
        const newChatId = response.data.chat.id;
        await loadSession(newChatId);
      } else {
        showToast(response.data.error || 'שגיאה ביצירת שיחה', 'error');
      }
    } catch (error) {
      console.error('❌ Error creating session:', error);
      
      if (error.response?.data?.error) {
        showToast(error.response.data.error, 'error');
      } else {
        showToast('שגיאה ביצירת שיחה', 'error');
      }
    }
  };

// ==================== ✅ טעינת שיחה ספציפית ====================
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
          status: chatData.status
        });
        
        // טען את ההודעות של השיחה
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

  // ==================== ✅ טעינת הודעות ====================
  const loadMessages = async (chatId) => {
    try {
      const response = await chatAPI.getChatMessages(chatId);
      
      if (response.data.success) {
        // המרת הפורמט של ההודעות
        const messagesData = response.data.data.map(msg => ({
          role: msg.role.toLowerCase(), // USER -> user, ASSISTANT -> assistant
          content: msg.content,
          timestamp: msg.createdAt,
          confidenceScore: msg.confidenceScore,
          sources: msg.sources
        }));
        
        setMessages(messagesData);
      }
    } catch (error) {
      console.error('❌ Error loading messages:', error);
      // לא מציגים toast כאן כי זה לא קריטי
    }
  };

  // ==================== ✅ מחיקת שיחה ====================
  const deleteSession = async (sessionId, e) => {
    if (e) e.stopPropagation();
    
    if (!window.confirm('האם אתה בטוח שברצונך למחוק שיחה זו?')) return;

    try {
      console.log('🗑️ Deleting session:', sessionId);
      
      const response = await chatAPI.deleteChat(sessionId);
      
      if (response.data.success) {
        showToast('✅ שיחה נמחקה בהצלחה', 'success');
        
        // אם זו השיחה הנוכחית - נקה אותה
        if (currentSession && currentSession.id === sessionId) {
          setCurrentSession(null);
          setMessages([]);
        }
        
        // טען מחדש את רשימת השיחות
        await loadSessions();
      } else {
        showToast(response.data.error || 'שגיאה במחיקת שיחה', 'error');
      }
    } catch (error) {
      console.error('❌ Error deleting session:', error);
      showToast('שגיאה במחיקת שיחה', 'error');
    }
  };

  // ==================== ✅ שליחת הודעה ====================
  const sendMessage = async (text) => {
    if (!currentSession) {
      showToast('נא לבחור שיחה תחילה', 'error');
      return false;
    }

    if (!text.trim()) {
      showToast('נא להזין שאלה', 'error');
      return false;
    }

    // בדיקה אם השיחה מוכנה
    if (!currentSession.isReady) {
      showToast('⏳ השיחה עדיין מעבדת מסמכים. אנא המתן...', 'error');
      return false;
    }

    // הוסף את הודעת המשתמש מיד (אופטימיסטית)
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
        
        // הוסף את תשובת ה-AI
        const assistantMessage = {
          role: 'assistant',
          content: answerData.answer,
          timestamp: answerData.timestamp,
          confidenceScore: answerData.confidence,
          sources: answerData.sources
        };
        
        setMessages(prev => [...prev, assistantMessage]);
        
        // אם יש רמת ביטחון נמוכה - הצג אזהרה
        if (answerData.confidence < 0.5) {
          showToast('⚠️ רמת ביטחון נמוכה בתשובה', 'warning');
        }
        
        return true;
      } else {
        // הסר את הודעת המשתמש כי נכשל
        setMessages(prev => prev.filter(msg => msg !== userMessage));
        showToast(response.data.error || 'שגיאה בשליחת הודעה', 'error');
        return false;
      }
    } catch (error) {
      console.error('❌ Error sending message:', error);
      
      // הסר את הודעת המשתמש כי נכשל
      setMessages(prev => prev.filter(msg => msg !== userMessage));
      
      if (error.response?.data?.error) {
        showToast(error.response.data.error, 'error');
      } else {
        showToast('שגיאה בשליחת הודעה', 'error');
      }
      return false;
    }
  };

  // ==================== פונקציות עזר ====================
  
  const showToast = (message, type = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => {
      setToast({ show: false, message: '', type: 'success' });
    }, 3000);
  };

  const filteredSessions = sessions.filter(session =>
    session.title.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // ==================== ✅ Render ====================
  
  return (
    <div className="dashboard">
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
          uploadedFiles={uploadedFiles}
          onFileUpload={() => {}} // ✅ לא משתמשים יותר - קבצים רק ביצירה
          onRemoveFile={() => {}} // ✅ לא משתמשים יותר
          onSendMessage={sendMessage}
          onShowDocuments={() => {}} // נוסיף בשלב הבא
          currentUser={currentUser}
        />
      </div>

      {showNewSessionModal && (
        <NewSessionModal
          onClose={() => setShowNewSessionModal(false)}
          onSubmit={submitNewSession}
        />
      )}

      {toast.show && (
        <div className={`toast ${toast.type} show`}>
          <span>{toast.message}</span>
        </div>
      )}

      {/* אינדיקטור טעינה גלובלי */}
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