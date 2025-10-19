// frontend/src/components/Dashboard/NewSessionModal.js
import React, { useState } from 'react';

const NewSessionModal = ({ onClose, onSubmit }) => {
  const [title, setTitle] = useState('');
  const [files, setFiles] = useState([]);
  const [uploading, setUploading] = useState(false);

  // פונקציה לטיפול בבחירת קבצים
  const handleFileChange = (e) => {
    const selectedFiles = Array.from(e.target.files);
    
    // בדיקה שכל הקבצים הם PDF
    const invalidFiles = selectedFiles.filter(file => !file.name.toLowerCase().endsWith('.pdf'));
    if (invalidFiles.length > 0) {
      alert('ניתן להעלות רק קבצי PDF');
      return;
    }

    // בדיקת גודל קבצים (מקסימום 50MB לכל קובץ)
    const oversizedFiles = selectedFiles.filter(file => file.size > 50 * 1024 * 1024);
    if (oversizedFiles.length > 0) {
      alert('גודל מקסימלי לקובץ: 50MB');
      return;
    }

    setFiles(prevFiles => [...prevFiles, ...selectedFiles]);
    e.target.value = ''; // אפס את ה-input כדי לאפשר בחירה חוזרת
  };

  // הסרת קובץ מהרשימה
  const removeFile = (index) => {
    setFiles(prevFiles => prevFiles.filter((_, i) => i !== index));
  };

  // פורמט גודל קובץ קריא
  const formatFileSize = (bytes) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // בדיקות תקינות
    if (!title.trim()) {
      alert('נא להזין כותרת לשיחה');
      return;
    }

    if (files.length === 0) {
      alert('נא להעלות לפחות קובץ PDF אחד');
      return;
    }

    setUploading(true);
    await onSubmit(title, files);
    setUploading(false);
  };

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget && !uploading) {
      onClose();
    }
  };

  return (
    <div className="modal active" onClick={handleBackdropClick}>
      <div className="modal-content" style={{ maxWidth: '600px' }} onClick={(e) => e.stopPropagation()}>
        <h2 className="modal-header">שיחה חדשה</h2>
        
        <form onSubmit={handleSubmit}>
          {/* שדה כותרת */}
          <div className="form-group">
            <label htmlFor="sessionTitle">כותרת השיחה:</label>
            <input
              type="text"
              id="sessionTitle"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="לדוגמה: ניתוח חוזה משכנתא"
              autoFocus
              disabled={uploading}
              required
            />
          </div>

          {/* העלאת קבצים */}
          <div className="form-group">
            <label>קבצי PDF:</label>
            
            {/* כפתור בחירת קבצים */}
            <div style={{ marginBottom: '15px' }}>
              <label 
                htmlFor="fileInput" 
                className="btn-submit"
                style={{ 
                  display: 'inline-block',
                  cursor: uploading ? 'not-allowed' : 'pointer',
                  opacity: uploading ? 0.5 : 1
                }}
              >
                📎 בחר קבצים להעלאה
              </label>
              <input
                type="file"
                id="fileInput"
                accept=".pdf"
                multiple
                onChange={handleFileChange}
                style={{ display: 'none' }}
                disabled={uploading}
              />
            </div>

            {/* רשימת קבצים שנבחרו */}
            {files.length > 0 && (
              <div style={{ 
                maxHeight: '200px', 
                overflowY: 'auto',
                border: '1px solid #e1e8ed',
                borderRadius: '8px',
                padding: '10px'
              }}>
                {files.map((file, index) => (
                  <div 
                    key={index}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '8px',
                      background: '#f8f9ff',
                      borderRadius: '6px',
                      marginBottom: '8px'
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 500 }}>📄 {file.name}</div>
                      <div style={{ fontSize: '12px', color: '#666' }}>
                        {formatFileSize(file.size)}
                      </div>
                    </div>
                    {!uploading && (
                      <button
                        type="button"
                        onClick={() => removeFile(index)}
                        style={{
                          background: 'none',
                          border: 'none',
                          cursor: 'pointer',
                          fontSize: '20px',
                          color: '#dc3545'
                        }}
                        title="הסר קובץ"
                      >
                        ✕
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* מידע על דרישות */}
            <div style={{ 
              fontSize: '12px', 
              color: '#666', 
              marginTop: '10px',
              textAlign: 'right'
            }}>
              * ניתן להעלות מספר קבצי PDF (מקסימום 50MB לכל קובץ)
            </div>
          </div>

          {/* כפתורי פעולה */}
          <div className="modal-actions">
            <button 
              type="button"
              className="btn-cancel" 
              onClick={onClose}
              disabled={uploading}
            >
              ביטול
            </button>
            <button 
              type="submit"
              className="btn-submit"
              disabled={uploading || files.length === 0 || !title.trim()}
            >
              {uploading ? '⏳ יוצר שיחה...' : '✓ צור שיחה'}
            </button>
          </div>
        </form>

        {/* אינדיקטור טעינה */}
        {uploading && (
          <div style={{ 
            marginTop: '20px', 
            textAlign: 'center',
            padding: '20px',
            background: '#f8f9ff',
            borderRadius: '8px'
          }}>
            <div className="spinner"></div>
            <p style={{ marginTop: '10px', color: '#666' }}>
              מעלה קבצים ומעבד מסמכים...<br />
              <small>תהליך זה עשוי לקחת מספר דקות</small>
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default NewSessionModal;