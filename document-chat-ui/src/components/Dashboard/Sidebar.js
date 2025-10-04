// src/components/Dashboard/Sidebar.js
import React from 'react';

const Sidebar = ({
  sessions,
  currentSession,
  searchTerm,
  onSearchChange,
  onNewSession,
  onSelectSession,
  onDeleteSession
}) => {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <button className="new-chat-btn" onClick={onNewSession}>
          <span>➕</span>
          <span>שיחה חדשה</span>
        </button>
        <div className="search-box">
          <input
            type="text"
            placeholder="חיפוש שיחות..."
            value={searchTerm}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
      </div>

      <div className="sessions-list">
        {sessions.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '20px', color: '#666' }}>
            <p>אין שיחות עדיין</p>
            <p style={{ fontSize: '14px', marginTop: '10px' }}>
              צור שיחה חדשה כדי להתחיל
            </p>
          </div>
        ) : (
          sessions.map(session => (
            <div
              key={session.id}
              className={`session-item ${currentSession?.id === session.id ? 'active' : ''}`}
              onClick={() => onSelectSession(session.id)}
            >
              <div className="session-title">
                <span>{session.title}</span>
                <div className="session-actions">
                  <button
                    className="session-action-btn"
                    onClick={(e) => onDeleteSession(session.id, e)}
                    title="מחק"
                  >
                    🗑️
                  </button>
                </div>
              </div>
              <div className="session-meta">
                📄 {session.documentsCount} מסמכים |
                💬 {session.messagesCount} הודעות |
                🕒 {session.lastActivityAt || session.createdAt}
              </div>
            </div>
          ))
        )}
      </div>
    </aside>
  );
};

export default Sidebar;