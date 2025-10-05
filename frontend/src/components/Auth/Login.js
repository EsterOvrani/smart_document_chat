// src/components/Auth/Login.js
import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { authAPI } from '../../services/api';
import './Login.css';

const Login = () => {
  const [email, setEmail] = useState('');  // ← שינוי מ-username
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  
  const navigate = useNavigate();
  const location = useLocation();

React.useEffect(() => {
    const params = new URLSearchParams(location.search);
    const msg = params.get('msg');
    const verified = params.get('verified');
    const errorParam = params.get('error');
    
    if (verified === 'true') {
      setSuccessMsg('✅ המייל אומת בהצלחה! כעת תוכל להתחבר למערכת');
      // נקה את ה-URL
      window.history.replaceState({}, '', '/login');
    } else if (verified === 'false') {
      setError('❌ אימות המייל נכשל: ' + (errorParam || 'קוד לא תקין או פג תוקף'));
      // נקה את ה-URL
      window.history.replaceState({}, '', '/login');
    } else if (msg) {
      setSuccessMsg(msg);
    }
  }, [location]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authAPI.login(email, password);  // ← שינוי
      
      if (response.data.success) {
        // שמור את הטוקן ב-localStorage
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(response.data.user));
        
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

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email">אימייל:</label>
            <input
              type="email"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
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
          >
            {loading ? 'מתחבר...' : 'התחבר'}
          </button>
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