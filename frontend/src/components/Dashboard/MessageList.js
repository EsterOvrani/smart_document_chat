// src/components/Dashboard/MessageList.js
import React, { useEffect, useRef } from 'react';

const MessageList = ({ currentSession, messages, loading, currentUser }) => {
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  if (!currentSession) {
    return (
      <div className="messages-container">
        <div className="empty-state">
          <div className="empty-state-icon">💬</div>
          <h2>התחל שיחה חדשה</h2>
          <p>בחר שיחה קיימת או צור שיחה חדשה כדי להתחיל</p>
        </div>
      </div>
    );
  }

  if (currentSession.documentsCount === 0 && messages.length === 0) {
    return (
      <div className="messages-container">
        <div className="empty-state">
          <div className="empty-state-icon">📄</div>
          <h2>העלה מסמך כדי להתחיל</h2>
          <p>העלה קובץ PDF כדי לשאול עליו שאלות</p>
        </div>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="messages-container">
        <div className="empty-state">
          <div className="empty-state-icon">💬</div>
          <h2>שאל שאלה על המסמכים</h2>
          <p>יש {currentSession.documentsCount} מסמכים בשיחה זו</p>
        </div>
      </div>
    );
  }

  return (
    <div className="messages-container">
      {messages.map((message, index) => (
        <Message
          key={index}
          message={message}
          currentUser={currentUser}
        />
      ))}
      
      {loading && <TypingIndicator />}
      
      <div ref={messagesEndRef} />
    </div>
  );
};

const Message = ({ message, currentUser }) => {
  const isUser = message.role === 'user';
  const avatar = isUser 
    ? (currentUser?.fullName?.[0] || 'U')
    : 'AI';

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString('he-IL', {
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div className={`message ${message.role}`}>
      <div className="message-avatar">{avatar}</div>
      <div className="message-content">
        <div className="message-bubble">
          {message.content.split('\n').map((line, i) => (
            <React.Fragment key={i}>
              {line}
              {i < message.content.split('\n').length - 1 && <br />}
            </React.Fragment>
          ))}
        </div>
        <div className="message-time">{formatTime(message.timestamp)}</div>
      </div>
    </div>
  );
};

const TypingIndicator = () => {
  return (
    <div className="message assistant">
      <div className="message-avatar">AI</div>
      <div className="message-content">
        <div className="message-bubble">
          <div className="typing-indicator">
            <div className="typing-dot"></div>
            <div className="typing-dot"></div>
            <div className="typing-dot"></div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MessageList;