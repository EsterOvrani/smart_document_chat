// src/components/Error/ErrorPage.js
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './ErrorPage.css';

const ErrorPage = () => {
  const [showDetails, setShowDetails] = useState(false);
  const navigate = useNavigate();

  const currentTime = new Date().toLocaleString('he-IL');
  const currentPath = window.location.pathname;

  return (
    <div className="error-page">
      <div className="error-container">
        <div className="error-icon">⚠️</div>
        <h1 className="error-title">אופס! משהו השתבש</h1>
        <p className="error-message">
          מצטערים, אירעה שגיאה במערכת. אנא נסה שוב מאוחר יותר או צור קשר עם התמיכה הטכנית.
        </p>

        <div className="error-actions">
          <button className="btn btn-primary" onClick={() => navigate('/')}>
            חזור לדף הבית
          </button>
          <button className="btn btn-secondary" onClick={() => navigate('/login')}>
            חזור להתחברות
          </button>
        </div>

        <span className="show-details" onClick={() => setShowDetails(!showDetails)}>
          {showDetails ? 'הסתר פרטים טכניים' : 'הצג פרטים טכניים'}
        </span>

        {showDetails && (
          <div className="error-details">
            <strong>פרטי השגיאה:</strong><br />
            Status: 500 - Internal Server Error<br />
            Path: {currentPath}<br />
            Time: {currentTime}<br />
            Session ID: N/A
          </div>
        )}
      </div>
    </div>
  );
};

export default ErrorPage;