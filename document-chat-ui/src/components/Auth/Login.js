// src/components/Auth/Login.js
import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { authAPI } from '../../services/api';
import './Login.css';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  
  const navigate = useNavigate();
  const location = useLocation();

  // קבלת הודעת הצלחה מה-URL (אחרי רישום)
  React.useEffect(() => {
    const params = new URLSearchParams(location.search);
    const msg = params.get('msg');
    if (msg) {
      setSuccessMsg(msg);
    }
  }, [location]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authAPI.login(username, password);
      
      if (response.data.success) {
        navigate('/');
      } else {
        setError(response.data.error || 'שגיאה בהתחברות');
      }
    } catch (err) {
      console.error('Login error:', err);
      if (err.response?.data?.error) {
        setError(err.response.data.error);
      } else {
        setError('שגיאה בחיבור לשרת');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="logo">📚 Smart Chat</div>
        <div className="subtitle">מערכת ניהול מסמכים חכמה</div>

        {error && (
          <div className="alert alert-error">{error}</div>
        )}

        {successMsg && (
          <div className="alert alert-success">{successMsg}</div>
        )}

        <form onSubmit={handleSubmit} id="loginForm">
          <div className="form-group">
            <label htmlFor="username">שם משתמש או אימייל:</label>
            <input
              type="text"
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">סיסמה:</label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={loading}
            />
          </div>

          <button 
            type="submit" 
            className="login-btn"
            disabled={loading}
            style={{ display: loading ? 'none' : 'block' }}
          >
            התחבר
          </button>

          {loading && (
            <div className="loading">
              <div className="spinner"></div>
              <p>מתחבר...</p>
            </div>
          )}
        </form>

        <div className="register-link">
          <p>
            אין לך חשבון?{' '}
            <span 
              onClick={() => navigate('/register')}
              style={{ cursor: 'pointer', color: '#667eea', fontWeight: 500 }}
            >
              הירשם כאן
            </span>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;