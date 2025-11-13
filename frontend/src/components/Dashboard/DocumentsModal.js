// frontend/src/components/Dashboard/DocumentsModal.js
import React, { useState, useEffect } from 'react';
import { documentAPI } from '../../services/api';

const DocumentsModal = ({ chatId, onClose }) => {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDocuments();
  }, [chatId]);

  const loadDocuments = async () => {
    try {
      setLoading(true);
      const response = await documentAPI.getDocumentsByChat(chatId);
      if (response.data.success) {
        setDocuments(response.data.data);
      }
    } catch (error) {
      console.error('Error loading documents:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenDocument = async (docId) => {
    try {
      const response = await documentAPI.getDownloadUrl(docId);
      if (response.data.success) {
        window.open(response.data.url, '_blank');
      }
    } catch (error) {
      console.error('Error opening document:', error);
      alert('שגיאה בפתיחת המסמך');
    }
  };

  return (
    <div className="modal active" onClick={onClose}>
      <div className="modal-content" style={{ maxWidth: '600px' }} onClick={(e) => e.stopPropagation()}>
        <h2 className="modal-header">מסמכים בשיחה</h2>
        
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <div className="spinner"></div>
            <p style={{ marginTop: '15px' }}>טוען מסמכים...</p>
          </div>
        ) : documents.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px', color: '#666' }}>
            <div style={{ fontSize: '3rem', marginBottom: '20px' }}>📄</div>
            <p>אין מסמכים בשיחה זו</p>
          </div>
        ) : (
          <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
            {documents.map((doc) => (
              <div
                key={doc.id}
                style={{
                  padding: '15px',
                  marginBottom: '10px',
                  background: '#f8f9ff',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                  border: '1px solid transparent'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#e8f0fe';
                  e.currentTarget.style.borderColor = '#667eea';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = '#f8f9ff';
                  e.currentTarget.style.borderColor = 'transparent';
                }}
                onClick={() => handleOpenDocument(doc.id)}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600, color: '#333', marginBottom: '5px' }}>
                      📄 {doc.originalFileName}
                    </div>
                    <div style={{ fontSize: '13px', color: '#666' }}>
                      {doc.fileSizeFormatted} • {doc.processingStatus === 'COMPLETED' ? '✓ מעובד' : '⏳ מעבד'}
                    </div>
                  </div>
                  <div style={{ fontSize: '20px', color: '#667eea' }}>
                    ↗️
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="modal-actions" style={{ marginTop: '20px' }}>
          <button className="btn-cancel" onClick={onClose}>
            סגור
          </button>
        </div>
      </div>
    </div>
  );
};

export default DocumentsModal;