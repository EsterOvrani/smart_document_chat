// frontend/src/components/Dashboard/ChatArea.js
import React, { useState, useRef, useEffect } from 'react';
import MessageList from './MessageList';

const ChatArea = ({
  currentSession,
  messages,
  onSendMessage,
  onShowDocuments,
  currentUser
}) => {
  const [messageInput, setMessageInput] = useState('');
  const [loading, setLoading] = useState(false);

  // בדיקה אם השיחה מוכנה
  const isSessionReady = currentSession?.isReady && currentSession?.status === 'READY';

  const handleSendMessage = async () => {
    if (!isSessionReady) {
      return;
    }

    if (!messageInput.trim() || loading) return;

    setLoading(true);
    const text = messageInput;
    setMessageInput('');

    const success = await onSendMessage(text);
    setLoading(false);

    if (!success) {
      setMessageInput(text);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <main className="content-area">
      <div className="chat-header">
        <h1 className="chat-title">
          {currentSession?.title || 'בחר שיחה או צור שיחה חדשה'}
        </h1>
        {currentSession && (
          <div className="chat-actions">
            {!isSessionReady && (
              <span style={{
                padding: '8px 16px',
                background: '#fff3cd',
                color: '#856404',
                borderRadius: '6px',
                fontSize: '14px',
                fontWeight: 500
              }}>
                ⏳ מעבד מסמכים...
              </span>
            )}
            {isSessionReady && (
              <>
                <span style={{
                  padding: '8px 16px',
                  background: '#d4edda',
                  color: '#155724',
                  borderRadius: '6px',
                  fontSize: '14px',
                  fontWeight: 500
                }}>
                  ✓ מוכן
                </span>
                <button className="action-btn" onClick={onShowDocuments}>
                  📄 מסמכים
                </button>
              </>
            )}
          </div>
        )}
      </div>

      <MessageList
        currentSession={currentSession}
        messages={messages}
        loading={loading}
        currentUser={currentUser}
      />

      {currentSession && (
        <div className="input-area">
          <div className="input-wrapper">
            <textarea
              className="message-input"
              placeholder={
                isSessionReady 
                  ? "שאל שאלה על המסמכים שלך..." 
                  : "ממתין לסיום עיבוד המסמכים..."
              }
              value={messageInput}
              onChange={(e) => setMessageInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading || !isSessionReady}
              style={{
                cursor: !isSessionReady ? 'not-allowed' : 'text',
                opacity: !isSessionReady ? 0.6 : 1
              }}
            />
            <button
              className="send-btn"
              onClick={handleSendMessage}
              disabled={loading || !messageInput.trim() || !isSessionReady}
              title={!isSessionReady ? 'ממתין לסיום עיבוד המסמכים' : 'שלח הודעה'}
              style={{
                cursor: !isSessionReady ? 'not-allowed' : 'pointer',
                opacity: !isSessionReady ? 0.5 : 1
              }}
            >
              שלח
            </button>
          </div>

          {!isSessionReady && currentSession && (
            <div style={{
              marginTop: '10px',
              padding: '10px',
              background: '#fff3cd',
              border: '1px solid #ffc107',
              borderRadius: '6px',
              fontSize: '14px',
              color: '#856404',
              textAlign: 'center'
            }}>
              ⏳ <strong>המערכת מעבדת את המסמכים שלך.</strong> שליחת הודעות תתאפשר בעוד מספר רגעים...
            </div>
          )}
        </div>
      )}
    </main>
  );
};

export default ChatArea;