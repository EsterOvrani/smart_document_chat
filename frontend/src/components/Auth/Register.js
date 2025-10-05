// src/components/Auth/Register.js
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI } from '../../services/api';
import './Register.css';

const Register = () => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  
  const [validations, setValidations] = useState({});
  const [passwordStrength, setPasswordStrength] = useState('');
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState({ message: '', type: '' });
  
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    
    // Real-time validation
    if (name === 'username' && value.length >= 3) {
      validateUsername(value);
    }
    if (name === 'email' && value.includes('@')) {
      validateEmail(value);
    }
    if (name === 'password') {
      validatePassword(value);
    }
    if (name === 'confirmPassword' || name === 'password') {
      validateConfirmPassword(
        name === 'password' ? value : formData.password,
        name === 'confirmPassword' ? value : formData.confirmPassword
      );
    }
  };

  const validateUsername = async (username) => {
    const usernameRegex = /^[a-zA-Z0-9_]{3,20}$/;
    
    if (!usernameRegex.test(username)) {
      setValidations(prev => ({
        ...prev,
        username: { valid: false, message: '3-20 תווים: אותיות אנגליות, מספרים ו-_ בלבד' }
      }));
      return;
    }

    try {
      const response = await authAPI.checkUsername(username);
      if (response.data.available) {
        setValidations(prev => ({
          ...prev,
          username: { valid: true, message: 'שם המשתמש זמין ✓' }
        }));
      } else {
        setValidations(prev => ({
          ...prev,
          username: { valid: false, message: 'שם המשתמש כבר תפוס' }
        }));
      }
    } catch (error) {
      console.error('Error checking username:', error);
    }
  };

  const validateEmail = async (email) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (!emailRegex.test(email)) {
      setValidations(prev => ({
        ...prev,
        email: { valid: false, message: 'פורמט אימייל לא תקין' }
      }));
      return;
    }

    try {
      const response = await authAPI.checkEmail(email);
      if (response.data.available) {
        setValidations(prev => ({
          ...prev,
          email: { valid: true, message: 'האימייל זמין ✓' }
        }));
      } else {
        setValidations(prev => ({
          ...prev,
          email: { valid: false, message: 'האימייל כבר בשימוש' }
        }));
      }
    } catch (error) {
      console.error('Error checking email:', error);
    }
  };

  const validatePassword = (password) => {
    if (password.length < 6) {
      setPasswordStrength('חלשה');
      setValidations(prev => ({
        ...prev,
        password: { valid: false, message: 'סיסמה חייבת להכיל לפחות 6 תווים' }
      }));
      return;
    }

    let strength = 'חלשה';
    if (password.length >= 8 && /[A-Z]/.test(password) && /[0-9]/.test(password)) {
      strength = 'חזקה';
    } else if (password.length >= 6 && (/[A-Z]/.test(password) || /[0-9]/.test(password))) {
      strength = 'בינונית';
    }

    setPasswordStrength(strength);
    setValidations(prev => ({
      ...prev,
      password: { valid: true, message: '' }
    }));
  };

  const validateConfirmPassword = (password, confirmPassword) => {
    if (!confirmPassword) {
      setValidations(prev => ({
        ...prev,
        confirmPassword: { valid: false, message: 'אישור סיסמה הוא שדה חובה' }
      }));
      return;
    }

    if (password !== confirmPassword) {
      setValidations(prev => ({
        ...prev,
        confirmPassword: { valid: false, message: 'הסיסמאות אינן זהות' }
      }));
      return;
    }

    setValidations(prev => ({
      ...prev,
      confirmPassword: { valid: true, message: 'הסיסמאות זהות' }
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setAlert({ message: '', type: '' });

    // Validate all fields
    if (!formData.firstName.trim() || !formData.lastName.trim()) {
      setAlert({ message: 'נא למלא את כל השדות', type: 'error' });
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setAlert({ message: 'הסיסמאות אינן זהות', type: 'error' });
      return;
    }

    setLoading(true);

    try {
      const response = await authAPI.register({
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        email: formData.email.trim(),
        password: formData.password,
        username: formData.username.trim()
      });

      if (response.data.success) {
        setAlert({ message: 'רישום בוצע בהצלחה! מעביר לדף ההתחברות...', type: 'success' });
        
        setTimeout(() => {
          navigate('/login?msg=' + encodeURIComponent('רישום בוצע בהצלחה! אנא התחבר'));
        }, 2000);
      } else {
        setAlert({ message: response.data.error || 'שגיאה ברישום המשתמש', type: 'error' });
      }
    } catch (error) {
      console.error('Registration error:', error);
      if (error.response?.data?.error) {
        setAlert({ message: error.response.data.error, type: 'error' });
      } else {
        setAlert({ message: 'שגיאה בחיבור לשרת', type: 'error' });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="register-page">
      <div className="register-container">
        <div className="logo">📚 Smart Chat</div>
        <div className="subtitle">הצטרף למערכת ניהול המסמכים החכמה</div>

        {alert.message && (
          <div className={`alert alert-${alert.type}`}>{alert.message}</div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="firstName">שם פרטי:</label>
              <input
                type="text"
                id="firstName"
                name="firstName"
                value={formData.firstName}
                onChange={handleChange}
                required
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="lastName">שם משפחה:</label>
              <input
                type="text"
                id="lastName"
                name="lastName"
                value={formData.lastName}
                onChange={handleChange}
                required
                disabled={loading}
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="username">שם משתמש:</label>
            <input
              type="text"
              id="username"
              name="username"
              value={formData.username}
              onChange={handleChange}
              required
              disabled={loading}
            />
            {validations.username && (
              <div className={`field-validation ${validations.username.valid ? 'validation-success' : 'validation-error'}`}>
                {validations.username.message}
              </div>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="email">כתובת אימייל:</label>
            <input
              type="email"
              id="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              required
              disabled={loading}
            />
            {validations.email && (
              <div className={`field-validation ${validations.email.valid ? 'validation-success' : 'validation-error'}`}>
                {validations.email.message}
              </div>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="password">סיסמה:</label>
            <input
              type="password"
              id="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
              disabled={loading}
            />
            {passwordStrength && (
              <div className={`password-strength strength-${passwordStrength === 'חזקה' ? 'strong' : passwordStrength === 'בינונית' ? 'medium' : 'weak'}`}>
                חוזק סיסמה: {passwordStrength}
              </div>
            )}
            {validations.password && !validations.password.valid && (
              <div className="field-validation validation-error">
                {validations.password.message}
              </div>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword">אישור סיסמה:</label>
            <input
              type="password"
              id="confirmPassword"
              name="confirmPassword"
              value={formData.confirmPassword}
              onChange={handleChange}
              required
              disabled={loading}
            />
            {validations.confirmPassword && (
              <div className={`field-validation ${validations.confirmPassword.valid ? 'validation-success' : 'validation-error'}`}>
                {validations.confirmPassword.message}
              </div>
            )}
          </div>

          <button 
            type="submit" 
            className="register-btn"
            disabled={loading}
            style={{ display: loading ? 'none' : 'block' }}
          >
            הירשם
          </button>

          {loading && (
            <div className="loading">
              <div className="spinner"></div>
              <p>מעבד רישום...</p>
            </div>
          )}
        </form>

        <div className="login-link">
          <p>
            כבר יש לך חשבון?{' '}
            <span 
              onClick={() => navigate('/login')}
              style={{ cursor: 'pointer', color: '#667eea', fontWeight: 500 }}
            >
              התחבר כאן
            </span>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Register;