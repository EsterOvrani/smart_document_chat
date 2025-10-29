// frontend/src/components/Dashboard/NewSessionModal.js
import React, { useState, useEffect, useRef } from 'react';
import { chatAPI } from '../../services/api';

const NewSessionModal = ({ onClose, onSubmit }) => {
  const [title, setTitle] = useState('');
  const [files, setFiles] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [statusData, setStatusData] = useState(null);
  const [createdChatId, setCreatedChatId] = useState(null);
  
  const pollingIntervalRef = useRef(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
  }, []);

  const handleFileChange = (e) => {
    const selectedFiles = Array.from(e.target.files);
    
    const invalidFiles = selectedFiles.filter(file => !file.name.toLowerCase().endsWith('.pdf'));
    if (invalidFiles.length > 0) {
      alert('ניתן להעלות רק קבצי PDF');
      return;
    }

    const oversizedFiles = selectedFiles.filter(file => file.size > 50 * 1024 * 1024);
    if (oversizedFiles.length > 0) {
      alert('גודל מקסימלי לקובץ: 50MB');
      return;
    }

    setFiles(prevFiles => [...prevFiles, ...selectedFiles]);
    e.target.value = '';
  };

  const removeFile = (index) => {
    setFiles(prevFiles => prevFiles.filter((_, i) => i !== index));
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const formatEstimatedTime = (seconds) => {
    if (!seconds || seconds <= 0) return 'מסיים...';
    if (seconds < 60) return `${Math.ceil(seconds)} שניות`;
    const minutes = Math.ceil(seconds / 60);
    return `${minutes} דקות`;
  };

  const formatElapsedTime = (seconds) => {
    if (!seconds) return '0 שניות';
    if (seconds < 60) return `${seconds} שניות`;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')} דקות`;
  };

  const getStageLabel = (stage) => {
    const stages = {
      'UPLOADING': 'העלאת קובץ',
      'EXTRACTING_TEXT': 'חילוץ טקסט',
      'CREATING_EMBEDDINGS': 'יצירת אינדקס',
      'STORING': 'שמירה',
      'COMPLETED': 'הושלם'
    };
    return stages[stage] || stage;
  };

  const fetchProcessingStatus = async (chatId) => {
    try {
      const response = await chatAPI.getProcessingStatus(chatId);
      
      console.log('📊 Processing Status Response:', response.data);
      
      if (response.data.success) {
        const data = response.data.data;
        console.log('📊 Status Data:', {
          isReady: data.isReady,
          status: data.status,
          overallProgress: data.overallProgress,
          completedDocuments: data.completedDocuments,
          totalDocuments: data.totalDocuments
        });
        
        setStatusData(data);

        // בדוק מספר תנאים אפשריים לסיום העיבוד
        const isCompleted = 
          data.isReady === true || 
          data.status === 'READY' || 
          data.overallProgress === 100 ||
          (data.completedDocuments > 0 && data.completedDocuments === data.totalDocuments);
        
        if (isCompleted) {
          console.log('✅ Processing completed! Closing modal and loading chat:', chatId);
          
          if (pollingIntervalRef.current) {
            clearInterval(pollingIntervalRef.current);
            pollingIntervalRef.current = null;
          }
          
          // המתן שנייה כדי שהמשתמש יראה את 100%
          setTimeout(() => {
            console.log('🔄 Calling onSubmit with chatId:', chatId);
            onSubmit(chatId);
          }, 1000);
        } else {
          console.log('⏳ Still processing...', {
            isReady: data.isReady,
            status: data.status,
            progress: data.overallProgress,
            docs: `${data.completedDocuments}/${data.totalDocuments}`
          });
        }
      }
    } catch (error) {
      console.error('❌ Error fetching processing status:', error);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!title.trim()) {
      alert('נא להזין כותרת לשיחה');
      return;
    }

    if (files.length === 0) {
      alert('נא להעלות לפחות קובץ PDF אחד');
      return;
    }

    setUploading(true);

    try {
      console.log('📤 Creating new chat:', { title, filesCount: files.length });
      
      const response = await chatAPI.createChat(title, files);
      
      console.log('✅ Chat created response:', response.data);

      if (response.data.success) {
        const newChatId = response.data.chat.id;
        console.log('💾 Saving chatId to state:', newChatId);
        
        setCreatedChatId(newChatId);
        setUploading(false);
        setProcessing(true);
        
        console.log('🔄 Starting polling for chatId:', newChatId);
        
        // התחל polling לעדכוני סטטוס - מיד
        await fetchProcessingStatus(newChatId);
        
        // המשך polling כל 2 שניות
        pollingIntervalRef.current = setInterval(() => {
          fetchProcessingStatus(newChatId);
        }, 2000);
      } else {
        console.error('❌ Create chat failed:', response.data.error);
        alert(response.data.error || 'שגיאה ביצירת שיחה');
        setUploading(false);
      }
    } catch (error) {
      console.error('❌ Error creating session:', error);
      
      if (error.response?.data?.error) {
        alert(error.response.data.error);
      } else {
        alert('שגיאה ביצירת שיחה');
      }
      setUploading(false);
    }
  };

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget && !uploading && !processing) {
      onClose();
    }
  };

  // אם בשלב עיבוד - הצג את מסך העיבוד
  if (processing && statusData) {
    return (
      <div className="modal active" onClick={handleBackdropClick}>
        <div className="modal-content" style={{ maxWidth: '600px' }} onClick={(e) => e.stopPropagation()}>
          <h2 className="modal-header">מעבד מסמכים...</h2>
          
          {/* פס התקדמות */}
          <div style={{ marginBottom: '30px' }}>
            <div style={{
              width: '100%',
              height: '40px',
              background: '#f0f0f0',
              borderRadius: '20px',
              overflow: 'hidden',
              position: 'relative',
              boxShadow: 'inset 0 2px 4px rgba(0,0,0,0.1)'
            }}>
              <div style={{
                height: '100%',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                width: `${statusData.overallProgress}%`,
                transition: 'width 0.5s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <span style={{
                  color: 'white',
                  fontWeight: 'bold',
                  fontSize: '16px'
                }}>
                  {statusData.overallProgress}%
                </span>
              </div>
            </div>
          </div>

          {/* פרטי התקדמות */}
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '15px',
            marginBottom: '30px',
            textAlign: 'right'
          }}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '12px',
              background: '#f8f9ff',
              borderRadius: '8px',
              borderRight: '4px solid #667eea'
            }}>
              <span style={{ fontWeight: 600, color: '#555' }}>📊 התקדמות:</span>
              <span style={{ color: '#333', fontWeight: 500 }}>
                {statusData.completedDocuments} מתוך {statusData.totalDocuments} מסמכים
              </span>
            </div>
            
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '12px',
              background: '#f8f9ff',
              borderRadius: '8px',
              borderRight: '4px solid #667eea'
            }}>
              <span style={{ fontWeight: 600, color: '#555' }}>⏱️ זמן משוער:</span>
              <span style={{ color: '#333', fontWeight: 500 }}>
                {formatEstimatedTime(statusData.estimatedTimeRemaining)}
              </span>
            </div>

            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '12px',
              background: '#f8f9ff',
              borderRadius: '8px',
              borderRight: '4px solid #667eea'
            }}>
              <span style={{ fontWeight: 600, color: '#555' }}>⏲️ זמן שעבר:</span>
              <span style={{ color: '#333', fontWeight: 500 }}>
                {formatElapsedTime(statusData.elapsedTimeSeconds)}
              </span>
            </div>

            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '12px',
              background: '#f8f9ff',
              borderRadius: '8px',
              borderRight: '4px solid #667eea'
            }}>
              <span style={{ fontWeight: 600, color: '#555' }}>📁 שיחה:</span>
              <span style={{ color: '#333', fontWeight: 500 }}>{title}</span>
            </div>
          </div>

          {/* מסמך נוכחי */}
          {statusData.currentDocument && (
            <div style={{
              background: '#f8f9ff',
              border: '2px solid #667eea',
              borderRadius: '12px',
              padding: '20px',
              marginBottom: '20px'
            }}>
              <h3 style={{ fontSize: '16px', color: '#667eea', marginBottom: '15px', fontWeight: 600 }}>
                מעבד כעת:
              </h3>
              <div style={{ textAlign: 'right', marginBottom: '10px' }}>
                <div style={{ fontSize: '16px', fontWeight: 600, color: '#333', marginBottom: '5px' }}>
                  📄 {statusData.currentDocument.name}
                </div>
                <div style={{ fontSize: '14px', color: '#667eea', fontWeight: 500, marginBottom: '3px' }}>
                  {getStageLabel(statusData.currentDocument.stage)} - {statusData.currentDocument.progress}%
                </div>
                <div style={{ fontSize: '13px', color: '#666' }}>
                  {statusData.currentDocument.fileSizeFormatted}
                </div>
              </div>
              <div style={{
                width: '100%',
                height: '8px',
                background: '#e1e8ed',
                borderRadius: '4px',
                overflow: 'hidden',
                marginTop: '10px'
              }}>
                <div style={{
                  height: '100%',
                  background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  width: `${statusData.currentDocument.progress}%`,
                  transition: 'width 0.3s ease'
                }} />
              </div>
            </div>
          )}

          {/* שלבי עיבוד */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: '15px',
            marginBottom: '30px'
          }}>
            {[
              { threshold: 20, icon: statusData.overallProgress >= 20 ? '✓' : '⏳', label: 'העלאת קבצים' },
              { threshold: 50, icon: statusData.overallProgress >= 50 ? '✓' : statusData.overallProgress >= 20 ? '⏳' : '○', label: 'ניתוח טקסט' },
              { threshold: 80, icon: statusData.overallProgress >= 80 ? '✓' : statusData.overallProgress >= 50 ? '⏳' : '○', label: 'יצירת אינדקס' },
              { threshold: 100, icon: statusData.overallProgress === 100 ? '✓' : statusData.overallProgress >= 80 ? '⏳' : '○', label: 'סיום' }
            ].map((step, index) => {
              const isActive = statusData.overallProgress >= (index > 0 ? [20, 50, 80][index - 1] : 0) && statusData.overallProgress < step.threshold;
              const isCompleted = statusData.overallProgress >= step.threshold;
              
              return (
                <div key={index} style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '15px 10px',
                  background: isCompleted ? '#d4edda' : isActive ? '#e8f0fe' : '#f8f9ff',
                  borderRadius: '10px',
                  border: `2px solid ${isCompleted ? '#28a745' : isActive ? '#667eea' : 'transparent'}`,
                  transition: 'all 0.3s'
                }}>
                  <div style={{
                    fontSize: '28px',
                    width: '45px',
                    height: '45px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: '50%',
                    background: isCompleted ? '#28a745' : 'white',
                    color: isCompleted ? 'white' : 'inherit',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                  }}>
                    {step.icon}
                  </div>
                  <div style={{
                    fontSize: '13px',
                    fontWeight: isActive || isCompleted ? 600 : 500,
                    color: isCompleted ? '#28a745' : isActive ? '#667eea' : '#666',
                    textAlign: 'center'
                  }}>
                    {step.label}
                  </div>
                </div>
              );
            })}
          </div>

          {/* סטטיסטיקות מסמכים */}
          {statusData.failedDocuments > 0 && (
            <div style={{
              background: '#fff3cd',
              border: '2px solid #ffc107',
              borderRadius: '8px',
              padding: '15px',
              color: '#856404',
              fontSize: '14px',
              marginBottom: '20px',
              textAlign: 'right'
            }}>
              ⚠️ <strong>{statusData.failedDocuments} מסמכים נכשלו בעיבוד</strong>
            </div>
          )}

          {/* הודעת מידע */}
          <div style={{
            background: '#fff3cd',
            border: '1px solid #ffc107',
            borderRadius: '8px',
            padding: '15px',
            color: '#856404',
            fontSize: '14px',
            marginBottom: '20px',
            textAlign: 'right'
          }}>
            💡 <strong>טיפ:</strong> אל תסגור את החלון עד לסיום העיבוד
          </div>

          {/* אנימציית נקודות */}
          <div style={{
            display: 'flex',
            justifyContent: 'center',
            gap: '8px'
          }}>
            {[0, 0.2, 0.4].map((delay, i) => (
              <div key={i} style={{
                width: '12px',
                height: '12px',
                background: '#667eea',
                borderRadius: '50%',
                animation: `dotFlashing 1.4s infinite`,
                animationDelay: `${delay}s`
              }} />
            ))}
          </div>
        </div>

        <style>{`
          @keyframes dotFlashing {
            0%, 100% {
              opacity: 0.3;
              transform: scale(0.8);
            }
            50% {
              opacity: 1;
              transform: scale(1);
            }
          }
        `}</style>
      </div>
    );
  }

  // תצוגה רגילה - טופס יצירת שיחה
  return (
    <div className="modal active" onClick={handleBackdropClick}>
      <div className="modal-content" style={{ maxWidth: '600px' }} onClick={(e) => e.stopPropagation()}>
        <h2 className="modal-header">שיחה חדשה</h2>
        
        <form onSubmit={handleSubmit}>
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

          <div className="form-group">
            <label>קבצי PDF:</label>
            
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

            <div style={{ 
              fontSize: '12px', 
              color: '#666', 
              marginTop: '10px',
              textAlign: 'right'
            }}>
              * ניתן להעלות מספר קבצי PDF (מקסימום 50MB לכל קובץ)
            </div>
          </div>

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
              מעלה קבצים ויוצר שיחה...<br />
              <small>אנא המתן</small>
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default NewSessionModal;