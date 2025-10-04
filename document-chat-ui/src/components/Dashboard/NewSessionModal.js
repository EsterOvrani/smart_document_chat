// src/components/Dashboard/NewSessionModal.js
import React, { useState } from 'react';

const NewSessionModal = ({ onClose, onSubmit }) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(title, description);
  };

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div className="modal active" onClick={handleBackdropClick}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <h2 className="modal-header">שיחה חדשה</h2>
        <div className="form-group">
          <label htmlFor="sessionTitle">כותרת השיחה:</label>
          <input
            type="text"
            id="sessionTitle"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="לדוגמה: ניתוח מסמכים משפטיים"
            autoFocus
          />
        </div>
        <div className="form-group">
          <label htmlFor="sessionDescription">תיאור (אופציונלי):</label>
          <textarea
            id="sessionDescription"
            rows="3"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="תיאור קצר של מטרת השיחה"
          />
        </div>
        <div className="modal-actions">
          <button className="btn-cancel" onClick={onClose}>
            ביטול
          </button>
          <button className="btn-submit" onClick={handleSubmit}>
            צור שיחה
          </button>
        </div>
      </div>
    </div>
  );
};

export default NewSessionModal;