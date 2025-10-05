// src/components/Auth/Verify.js
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authAPI } from '../../services/api';
import './Verify.css';

const Verify = () => {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState('loading'); // loading, waiting, success, error
  const [message, setMessage] = useState('');
  const [countdown, setCountdown] = useState(3);
  const [manualCode, setManualCode] = useState('');
  const [showManualInput, setShowManualInput] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  
  const navigate = useNavigate();
  const pollingIntervalRef = useRef(null);
  const email = searchParams.get('email');
  const code = searchParams.get('code');
  const mode = searchParams.get('mode'); // 'wait' או null (direct verify)

  // טיפול בטעינה ראשונית
  useEffect(() => {
    if (mode === 'wait') {
      // מצב המתנה - אחרי רישום
      if (!email) {
        setStatus('error');
        setMessage('כתובת מייל חסרה');
        return;
      }
      setStatus('waiting');
      setMessage('בדוק את תיבת המייל שלך');
      startPolling();
    } else if (email && code) {
      // מצב אימות ישיר - לחיצה על קישור במייל
      verifyEmail(email, code);
    } else {
      setStatus('error');
      setMessage('פרמטרים חסרים');
    }

    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
  }, []);

  // ספירה לאחור להעברה אוטומטית
  useEffect(() => {
    if (status === 'success' && countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown(countdown - 1);
      }, 1000);
      
      return () => clearTimeout(timer);
    } else if (status === 'success' && countdown === 0) {
      navigate('/login?verified=true');
    }
  }, [status, countdown, navigate]);

  // ספירה לאחור לכפתור שליחה מחדש
  useEffect(() => {
    if (resendCooldown > 0) {
      const timer = setTimeout(() => {
        setResendCooldown(resendCooldown - 1);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [resendCooldown]);

  // פונקציה לבדיקה תקופתית אם המשתמש אישר
  const startPolling = () => {
    // בדוק מיד
    checkVerificationStatus();
    
    // המשך לבדוק כל 3 שניות
    pollingIntervalRef.current = setInterval(() => {
      checkVerificationStatus();
    }, 3000);
  };

  // בדיקת סטטוס אימות
  const checkVerificationStatus = async () => {
    if (!email) return;
    
    try {
      const response = await authAPI.checkIfVerified(email);
      
      if (response.data.verified) {
        handleVerificationSuccess();
      }
    } catch (error) {
      // זה בסדר - אנחנו רק בודקים
      console.log('Polling check...');
    }
  };

  // אימות ישיר עם קוד (מקישור או ידני)
  const verifyEmail = async (emailToVerify, codeToVerify) => {
    setStatus('loading');
    
    try {
      const response = await authAPI.verify({ 
        email: emailToVerify, 
        verificationCode: codeToVerify 
      });
      
      if (response.data.success) {
        handleVerificationSuccess();
      } else {
        setStatus('error');
        setMessage(response.data.error || 'אימות נכשל');
      }
    } catch (error) {
      console.error('Verification error:', error);
      setStatus('error');
      
      if (error.response?.data?.error) {
        setMessage(error.response.data.error);
      } else if (error.response?.status === 400) {
        setMessage('קוד האימות לא תקין או שפג תוקפו');
      } else {
        setMessage('שגיאה באימות המייל');
      }
    }
  };

  // טיפול באימות מצליח
  const handleVerificationSuccess = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
    }
    setStatus('success');
    setMessage('המייל אומת בהצלחה!');
    setCountdown(3);
  };

  // אימות ידני
  const handleManualVerify = (e) => {
    e.preventDefault();
    if (!manualCode.trim()) {
      return;
    }
    verifyEmail(email, manualCode.trim());
  };

  // שליחת קוד מחדש
  const handleResendCode = async () => {
    if (!email || resendCooldown > 0) return;

    try {
      const response = await authAPI.resendVerificationCode(email);
      if (response.data.success) {
        setResendCooldown(60); // 60 שניות המתנה
        alert('✅ קוד אימות חדש נשלח למייל שלך');
      }
    } catch (err) {
      alert('❌ ' + (err.response?.data?.error || 'שגיאה בשליחת קוד'));
    }
  };

  return (
    <div className="verify-page">
      <div className="verify-container">
        <div className="logo">📚 Smart Chat</div>
        <div className="subtitle">אימות כתובת מייל</div>

        <div className="verify-status">
          {/* מצב טעינה */}
          {status === 'loading' && (
            <div className="loading">
              <div className="spinner"></div>
              <p>מאמת את המייל שלך...</p>
              <p style={{ fontSize: '14px', color: '#999', marginTop: '10px' }}>
                אנא המתן, זה ייקח רק רגע
              </p>
            </div>
          )}

          {/* מצב המתנה - polling */}
          {status === 'waiting' && (
            <>
              <div className="status-icon waiting">📧</div>
              <div className="alert" style={{ 
                backgroundColor: '#e3f2fd', 
                border: '2px solid #2196f3',
                color: '#1565c0'
              }}>
                <h2 style={{ margin: '0 0 15px 0' }}>בדוק את תיבת המייל שלך</h2>
                <p className="status-message">
                  שלחנו לך מייל לכתובת:<br />
                  <strong style={{ fontSize: '1.1em', color: '#667eea' }}>{email}</strong>
                </p>
                <p className="status-submessage">
                  לחץ על הכפתור במייל כדי לאמת את החשבון
                </p>
                <div className="loading" style={{ marginTop: '20px' }}>
                  <div className="spinner"></div>
                  <p style={{ fontSize: '14px', marginTop: '10px' }}>
                    ממתין לאימות... (בודק אוטומטית כל 3 שניות)
                  </p>
                </div>
              </div>

              {/* אופציה לאימות ידני */}
              {!showManualInput ? (
                <button 
                  className="verify-btn"
                  onClick={() => setShowManualInput(true)}
                  style={{ 
                    background: 'white', 
                    color: '#667eea', 
                    border: '2px solid #667eea',
                    marginTop: '20px'
                  }}
                >
                  יש לי קוד אימות - הזן ידנית
                </button>
              ) : (
                <form onSubmit={handleManualVerify} style={{ marginTop: '20px', width: '100%' }}>
                  <div className="form-group">
                    <input
                      type="text"
                      value={manualCode}
                      onChange={(e) => setManualCode(e.target.value)}
                      placeholder="הזן קוד בן 6 ספרות"
                      maxLength="6"
                      pattern="[0-9]{6}"
                      style={{ 
                        textAlign: 'center', 
                        fontSize: '20px', 
                        letterSpacing: '5px',
                        width: '100%',
                        padding: '12px',
                        border: '2px solid #e1e8ed',
                        borderRadius: '8px'
                      }}
                      autoFocus
                    />
                  </div>
                  <button 
                    type="submit" 
                    className="verify-btn"
                    style={{ width: '100%', marginTop: '10px' }}
                  >
                    אמת
                  </button>
                </form>
              )}

              {/* כפתור שליחה מחדש */}
              <button 
                className="verify-btn"
                onClick={handleResendCode}
                disabled={resendCooldown > 0}
                style={{ 
                  background: 'white', 
                  color: '#667eea', 
                  border: '2px solid #667eea',
                  marginTop: '10px',
                  opacity: resendCooldown > 0 ? 0.5 : 1
                }}
              >
                {resendCooldown > 0 
                  ? `שלח קוד מחדש (${resendCooldown}s)` 
                  : 'שלח קוד מחדש'}
              </button>

              <p style={{ marginTop: '20px', fontSize: '14px', color: '#999' }}>
                לא קיבלת מייל? בדוק גם בתיקיית SPAM
              </p>
            </>
          )}

          {/* מצב הצלחה */}
          {status === 'success' && (
            <>
              <div className="status-icon success">✅</div>
              <div className="alert alert-success">
                <h2 style={{ margin: '0 0 15px 0' }}>{message}</h2>
                <p className="status-submessage">
                  מעביר אותך לדף ההתחברות בעוד {countdown} שניות...
                </p>
                <div className="progress-bar">
                  <div className="progress-fill"></div>
                </div>
              </div>
              <button 
                className="verify-btn" 
                onClick={() => navigate('/login?verified=true')}
              >
                עבור להתחברות עכשיו
              </button>
            </>
          )}

          {/* מצב שגיאה */}
          {status === 'error' && (
            <>
              <div className="status-icon error">❌</div>
              <div className="alert alert-error">
                <h2 style={{ margin: '0 0 15px 0' }}>אימות נכשל</h2>
                <p className="status-message">{message}</p>
              </div>
              <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
                <button 
                  className="verify-btn" 
                  onClick={() => navigate('/login')}
                >
                  חזור להתחברות
                </button>
                <button 
                  className="verify-btn" 
                  onClick={() => navigate('/register')}
                  style={{ background: 'white', color: '#667eea', border: '2px solid #667eea' }}
                >
                  נסה להירשם שוב
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Verify;