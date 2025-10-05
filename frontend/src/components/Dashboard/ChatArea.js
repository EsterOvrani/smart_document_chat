// src/components/Dashboard/ChatArea.js
import React, { useState, useRef, useEffect } from 'react';
import MessageList from './MessageList';

const ChatArea = ({
  currentSession,
  messages,
  uploadedFiles,
  onFileUpload,
  onRemoveFile,
  onSendMessage,
  onShowDocuments,
  currentUser
}) => {
  const [messageInput, setMessageInput] = useState('');
  const [loading, setLoading] = useState(false);
  const fileInputRef = useRef(null);

  const handleSendMessage = async () => {
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
            <button className="action-btn" onClick={onShowDocuments}>
              📄 מסמכים
            </button>
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
          <div className="document-upload-area">
            {uploadedFiles.map(file => (
              <div key={file.id} className="uploaded-file">
                <span>📄 {file.name}</span>
                <button
                  className="remove-file"
                  onClick={() => onRemoveFile(file.id)}
                  title="הסר"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>

          <div className="input-wrapper">
            <label className="upload-btn" htmlFor="fileUpload" title="העלה קובץ PDF">
              📎
              <input
                ref={fileInputRef}
                type="file"
                id="fileUpload"
                accept=".pdf"
                style={{ display: 'none' }}
                onChange={onFileUpload}
                multiple
              />
            </label>
            <textarea
              className="message-input"
              placeholder="שאל שאלה על המסמכים שלך..."
              value={messageInput}
              onChange={(e) => setMessageInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading}
            />
            <button
              className="send-btn"
              onClick={handleSendMessage}
              disabled={loading || !messageInput.trim()}
            >
              שלח
            </button>
          </div>
        </div>
      )}
    </main>
  );
};

export default ChatArea;