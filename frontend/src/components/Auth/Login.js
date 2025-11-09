// frontend/src/components/Auth/Login.js
import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { authAPI } from '../../services/api';
import GoogleLoginButton from './GoogleLoginButton';
import './Login.css';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const msg = params.get('msg');
    const verified = params.get('verified');
    const errorParam = params.get('error');
    
    if (verified === 'true') {
      setSuccessMsg('✅ המייל אומת בהצלחה! כעת תוכל להתחבר למערכת');
      window.history.replaceState({}, '', '/login');
    } else if (verified === 'false') {
      setError('❌ אימות המייל נכשל: ' + (errorParam || 'קוד לא תקין או פג תוקף'));
      window.history.replaceState({}, '', '/login');
    } else if (msg) {
      setSuccessMsg(msg);
    }
  }, [location]);

  // ==================== Regular Login ====================
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authAPI.login(email, password);
      
      if (response.data.success) {
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

  // ==================== Google Login ====================
  const handleGoogleLogin = async (credential) => {
    setLoading(true);
    setError('');

    try {
      console.log('🔵 Google login attempt...');
      const response = await authAPI.googleLogin(credential);
      
      if (response.data.success) {
        console.log('✅ Google login successful');
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(response.data.user));
        navigate('/');
      } else {
        setError(response.data.error || 'שגיאה בהתחברות עם Google');
      }
    } catch (err) {
      console.error('❌ Google login error:', err);
      if (err.response?.data?.error) {
        setError(err.response.data.error);
      } else {
        setError('שגיאה בהתחברות עם Google');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleError = (error) => {
    console.error('Google login error:', error);
    setError('שגיאה בהתחברות עם Google. אנא נסה שוב.');
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

        {/* ==================== Google Login Button ==================== */}
        <GoogleLoginButton 
          onSuccess={handleGoogleLogin}
          onError={handleGoogleError}
          disabled={loading}
        />

        {/* ==================== Regular Login Form ==================== */}
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