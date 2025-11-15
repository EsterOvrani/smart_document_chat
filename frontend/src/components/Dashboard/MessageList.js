// frontend/src/components/Dashboard/MessageList.js
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

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString('he-IL', {
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const detectTextDirection = (text) => {
    if (!text) return 'rtl';
    
    const trimmedText = text.trim();
    if (!trimmedText) return 'rtl';
    
    const hebrewRegex = /[\u0590-\u05FF]/;
    
    if (hebrewRegex.test(text)) {
      return 'rtl';
    }
    
    return 'ltr';
  };

  const textDirection = detectTextDirection(message.content);

  return (
    <div className={`message ${message.role}`}>
      <div className="message-avatar">
        {isUser && currentUser?.profilePictureUrl ? (
          <img 
            src={currentUser.profilePictureUrl} 
            alt="Profile"
            onError={(e) => {
              console.error('Image failed to load:', currentUser.profilePictureUrl);
              e.target.style.display = 'none';
              e.target.parentElement.innerHTML = `<span>${currentUser?.fullName?.[0] || 'U'}</span>`;
            }}
          />
        ) : (
          <span>{isUser ? (currentUser?.fullName?.[0] || 'U') : 'AI'}</span>
        )}
      </div>
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