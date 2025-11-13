// frontend/src/components/Auth/GoogleLoginButton.js
import React, { useEffect, useRef } from 'react';

const GoogleLoginButton = ({ onSuccess, onError }) => {
  const googleClientRef = useRef(null);

  useEffect(() => {
    const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID;
    
    console.log('🔍 Client ID:', clientId);
    
    if (!clientId || clientId === 'undefined') {
      console.error('❌ CRITICAL: REACT_APP_GOOGLE_CLIENT_ID is missing!');
      return;
    }

    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    
    script.onload = () => {
      console.log('✅ Google SDK loaded');
      
      try {
        // ✅ אתחול Google Identity
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: handleCredentialResponse,
          // ⭐ אלה המפתחות - מונעים שמירת חשבון על הכפתור
          auto_select: false,
          cancel_on_tap_outside: true,
        });
        
        console.log('✅ Google initialized');
        
      } catch (error) {
        console.error('❌ Initialization error:', error);
      }
    };

    script.onerror = () => {
      console.error('❌ Failed to load Google SDK');
    };

    document.body.appendChild(script);

    return () => {
      const scriptElement = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
      if (scriptElement) {
        document.body.removeChild(scriptElement);
      }
    };
  }, []);

  const handleCredentialResponse = async (response) => {
    try {
      console.log('🎉 Credential received');
      await onSuccess(response.credential);
    } catch (error) {
      console.error('❌ Login failed:', error);
      onError(error);
    }
  };

  const handleButtonClick = () => {
    console.log('🔘 Button clicked');
    if (window.google?.accounts?.id) {
      // ⭐ זה פותח את חלון בחירת החשבון תמיד מחדש
      window.google.accounts.id.prompt((notification) => {
        console.log('Prompt notification:', notification);
        if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
          console.log('Prompt was not displayed, showing manual selection');
          // אם ה-prompt לא הוצג, נציג רשימת חשבונות באופן ידני
        }
      });
    }
  };

  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center',
      gap: '15px',
      margin: '20px 0'
    }}>
      {/* ✅ כפתור מותאם אישית שלא מציג חשבון עליו */}
      <button
        onClick={handleButtonClick}
        type="button"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '12px',
          padding: '10px 20px',
          backgroundColor: 'white',
          border: '1px solid #dadce0',
          borderRadius: '4px',
          cursor: 'pointer',
          fontSize: '14px',
          fontFamily: 'Roboto, arial, sans-serif',
          fontWeight: '500',
          color: '#3c4043',
          width: '300px',
          height: '40px',
          transition: 'background-color 0.3s, box-shadow 0.3s',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.backgroundColor = '#f8f9fa';
          e.currentTarget.style.boxShadow = '0 1px 2px 0 rgba(60,64,67,.30), 0 1px 3px 1px rgba(60,64,67,.15)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.backgroundColor = 'white';
          e.currentTarget.style.boxShadow = 'none';
        }}
      >
        <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fillRule="evenodd">
            <path d="M17.6 9.2l-.1-1.8H9v3.4h4.8C13.6 12 13 13 12 13.6v2.2h3a8.8 8.8 0 0 0 2.6-6.6z" fill="#4285F4"/>
            <path d="M9 18c2.4 0 4.5-.8 6-2.2l-3-2.2a5.4 5.4 0 0 1-8-2.9H1V13a9 9 0 0 0 8 5z" fill="#34A853"/>
            <path d="M4 10.7a5.4 5.4 0 0 1 0-3.4V5H1a9 9 0 0 0 0 8l3-2.3z" fill="#FBBC05"/>
            <path d="M9 3.6c1.3 0 2.5.4 3.4 1.3L15 2.3A9 9 0 0 0 1 5l3 2.4a5.4 5.4 0 0 1 5-3.7z" fill="#EA4335"/>
          </g>
        </svg>
        <span>היכנס באמצעות Google</span>
      </button>
      
      <div style={{ 
        display: 'flex', 
        alignItems: 'center', 
        width: '100%',
        margin: '10px 0'
      }}>
        <div style={{ flex: 1, height: '1px', background: '#e1e8ed' }}></div>
        <span style={{ padding: '0 10px', color: '#666', fontSize: '14px' }}>או</span>
        <div style={{ flex: 1, height: '1px', background: '#e1e8ed' }}></div>
      </div>
    </div>
  );
};

export default GoogleLoginButton;