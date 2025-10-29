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

  // ✅ פונקציה לזיהוי כיוון הטקסט - עברית או אנגלית
  const detectTextDirection = (text) => {
    if (!text) return 'rtl';
    
    // הסר רווחים וקבל את התו הראשון
    const trimmedText = text.trim();
    if (!trimmedText) return 'rtl';
    
    // טווח תווים עבריים: U+0590 to U+05FF
    const hebrewRegex = /[\u0590-\u05FF]/;
    
    // אם יש תווים עבריים בטקסט - כיוון מימין לשמאל
    if (hebrewRegex.test(text)) {
      return 'rtl';
    }
    
    // אחרת (אנגלית או שפות אחרות) - כיוון משמאל לימין
    return 'ltr';
  };

  const textDirection = detectTextDirection(message.content);

  return (
    <div className={`message ${message.role}`}>
      <div className="message-avatar">{avatar}</div>
      <div className="message-content">
        <div 
          className="message-bubble"
          style={{ 
            direction: textDirection,
            textAlign: textDirection === 'rtl' ? 'right' : 'left'
          }}
        >
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