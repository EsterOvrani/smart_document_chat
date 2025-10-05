// src/components/Dashboard/Dashboard.js
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI, sessionsAPI } from '../../services/api';
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
  const [showDocumentsModal, setShowDocumentsModal] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [toast, setToast] = useState({ show: false, message: '', type: 'success' });
  
  const navigate = useNavigate();

  useEffect(() => {
    checkAuth();
    loadSessions();
  }, []);

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

  const loadSessions = async () => {
    try {
      const response = await sessionsAPI.getAll();
      if (response.data.success) {
        setSessions(response.data.sessions);
      } else {
        showToast('שגיאה בטעינת שיחות', 'error');
      }
    } catch (error) {
      console.error('Error loading sessions:', error);
      showToast('שגיאה בחיבור לשרת', 'error');
    }
  };

  const createNewSession = () => {
    setShowNewSessionModal(true);
  };

  const submitNewSession = async (title, description) => {
    if (!title.trim()) {
      showToast('נא להזין כותרת לשיחה', 'error');
      return;
    }

    try {
      const response = await sessionsAPI.create({ title, description });

      if (response.data.success) {
        setShowNewSessionModal(false);
        showToast('שיחה חדשה נוצרה בהצלחה', 'success');
        await loadSessions();
        await loadSession(response.data.session.id);
      } else {
        showToast(response.data.error || 'שגיאה ביצירת שיחה', 'error');
      }
    } catch (error) {
      console.error('Error creating session:', error);
      showToast('שגיאה ביצירת שיחה', 'error');
    }
  };

  const loadSession = async (sessionId) => {
    try {
      const response = await sessionsAPI.getOne(sessionId);

      if (response.data.success) {
        setCurrentSession(response.data.session);
        setMessages([]);
        setUploadedFiles([]);
      } else {
        showToast(response.data.error || 'שגיאה בטעינת שיחה', 'error');
      }
    } catch (error) {
      console.error('Error loading session:', error);
      showToast('שגיאה בטעינת שיחה', 'error');
    }
  };

  const deleteSession = async (sessionId, e) => {
    if (e) e.stopPropagation();
    if (!window.confirm('האם אתה בטוח שברצונך למחוק שיחה זו?')) return;

    try {
      const response = await sessionsAPI.delete(sessionId);

      if (response.data.success) {
        showToast('שיחה נמחקה בהצלחה', 'success');
        if (currentSession && currentSession.id === sessionId) {
          setCurrentSession(null);
          setMessages([]);
        }
        await loadSessions();
      } else {
        showToast(response.data.error || 'שגיאה במחיקת שיחה', 'error');
      }
    } catch (error) {
      console.error('Error deleting session:', error);
      showToast('שגיאה במחיקת שיחה', 'error');
    }
  };

  const handleFileUpload = async (e) => {
    if (!currentSession) {
      showToast('נא לבחור או ליצור שיחה תחילה', 'error');
      return;
    }

    const files = Array.from(e.target.files);

    for (const file of files) {
      if (file.type !== 'application/pdf') {
        showToast('ניתן להעלות רק קבצי PDF', 'error');
        continue;
      }

      if (file.size > 50 * 1024 * 1024) {
        showToast('גודל קובץ מקסימלי: 50MB', 'error');
        continue;
      }

      await uploadFile(file);
    }

    e.target.value = '';
  };

  const uploadFile = async (file) => {
    const formData = new FormData();
    formData.append('file', file);

    showToast(`מעלה ${file.name}...`, 'success');

    try {
      const response = await sessionsAPI.uploadDocument(currentSession.id, formData);

      if (response.data.success) {
        showToast(`${file.name} הועלה בהצלחה`, 'success');
        setUploadedFiles(prev => [...prev, {
          name: file.name,
          id: response.data.document.id
        }]);
        await loadSession(currentSession.id);
      } else {
        showToast(response.data.error || 'שגיאה בהעלאת קובץ', 'error');
      }
    } catch (error) {
      console.error('Error uploading file:', error);
      showToast('שגיאה בהעלאת קובץ', 'error');
    }
  };

  const removeUploadedFile = (documentId) => {
    setUploadedFiles(prev => prev.filter(f => f.id !== documentId));
  };

  const sendMessage = async (text) => {
    if (!currentSession) {
      showToast('נא לבחור שיחה תחילה', 'error');
      return false;
    }

    if (!text.trim()) {
      showToast('נא להזין שאלה', 'error');
      return false;
    }

    const userMessage = {
      role: 'user',
      content: text,
      timestamp: new Date()
    };
    setMessages(prev => [...prev, userMessage]);

    try {
      const requestBody = { text };
      if (uploadedFiles.length > 0) {
        requestBody.documentIds = uploadedFiles.map(f => f.id);
      }

      const response = await sessionsAPI.chat(currentSession.id, requestBody);

      if (response.data.success) {
        const assistantMessage = {
          role: 'assistant',
          content: response.data.answer,
          timestamp: new Date()
        };
        setMessages(prev => [...prev, assistantMessage]);
        return true;
      } else {
        showToast(response.data.error || 'שגיאה בשליחת הודעה', 'error');
        return false;
      }
    } catch (error) {
      console.error('Error sending message:', error);
      showToast('שגיאה בשליחת הודעה', 'error');
      return false;
    }
  };

  const showDocuments = async () => {
    if (!currentSession) return;

    try {
      const response = await sessionsAPI.getDocuments(currentSession.id);

      if (response.data.success) {
        setDocuments(response.data.documents);
        setShowDocumentsModal(true);
      } else {
        showToast(response.data.error || 'שגיאה בטעינת מסמכים', 'error');
      }
    } catch (error) {
      console.error('Error loading documents:', error);
      showToast('שגיאה בטעינת מסמכים', 'error');
    }
  };

  const deleteDocument = async (documentId) => {
    if (!window.confirm('האם אתה בטוח שברצונך למחוק מסמך זה?')) return;

    try {
      const response = await sessionsAPI.deleteDocument(currentSession.id, documentId);

      if (response.data.success) {
        showToast('מסמך נמחק בהצלחה', 'success');
        setShowDocumentsModal(false);
        await loadSession(currentSession.id);
      } else {
        showToast(response.data.error || 'שגיאה במחיקת מסמך', 'error');
      }
    } catch (error) {
      console.error('Error deleting document:', error);
      showToast('שגיאה במחיקת מסמך', 'error');
    }
  };

  const showToast = (message, type = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => {
      setToast({ show: false, message: '', type: 'success' });
    }, 3000);
  };

  const filteredSessions = sessions.filter(session =>
    session.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (session.description && session.description.toLowerCase().includes(searchTerm.toLowerCase()))
  );

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
          onFileUpload={handleFileUpload}
          onRemoveFile={removeUploadedFile}
          onSendMessage={sendMessage}
          onShowDocuments={showDocuments}
          currentUser={currentUser}
        />
      </div>

      {showNewSessionModal && (
        <NewSessionModal
          onClose={() => setShowNewSessionModal(false)}
          onSubmit={submitNewSession}
        />
      )}

      {showDocumentsModal && (
        <DocumentsModal
          documents={documents}
          onClose={() => setShowDocumentsModal(false)}
          onDelete={deleteDocument}
        />
      )}

      {toast.show && (
        <div className={`toast ${toast.type} show`}>
          <span>{toast.message}</span>
        </div>
      )}
    </div>
  );
};

const DocumentsModal = ({ documents, onClose, onDelete }) => {
  return (
    <div className="modal active" onClick={onClose}>
      <div className="modal-content" style={{ maxWidth: '600px' }} onClick={(e) => e.stopPropagation()}>
        <h2 className="modal-header">מסמכים בשיחה ({documents.length})</h2>
        <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
          {documents.map(doc => (
            <div key={doc.id} style={{
              padding: '15px',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              marginBottom: '10px'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <strong>📄 {doc.fileName}</strong>
                  <div style={{ fontSize: '12px', color: '#666', marginTop: '5px' }}>
                    גודל: {doc.fileSize} |
                    סטטוס: {doc.status === 'COMPLETED' ? '✅ מעובד' : '⏳ בעיבוד'}
                  </div>
                </div>
                <button
                  className="session-action-btn"
                  onClick={() => onDelete(doc.id)}
                  title="מחק מסמך"
                >
                  🗑️
                </button>
              </div>
              {doc.characterCount && (
                <div style={{ fontSize: '12px', color: '#666', marginTop: '5px' }}>
                  {doc.characterCount.toLocaleString()} תווים |
                  {doc.chunkCount} chunks
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="modal-actions">
          <button className="btn-cancel" onClick={onClose}>סגור</button>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;