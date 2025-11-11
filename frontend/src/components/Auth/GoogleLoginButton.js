// frontend/src/components/Auth/GoogleLoginButton.js
import React, { useEffect } from 'react';

const GoogleLoginButton = ({ onSuccess, onError }) => {
  useEffect(() => {
    // 🔍 בדיקה 1: האם ה-Client ID קיים?
    const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID;
    
    console.log('🔍 === Google Login Button Debug ===');
    console.log('1️⃣ Client ID from env:', clientId);
    console.log('2️⃣ Client ID type:', typeof clientId);
    console.log('3️⃣ Client ID length:', clientId ? clientId.length : 0);
    console.log('4️⃣ All env vars:', process.env);
    console.log('================================');

    // 🚨 אם אין Client ID - עצור והצג שגיאה
    if (!clientId || clientId === 'undefined') {
      console.error('❌ CRITICAL: REACT_APP_GOOGLE_CLIENT_ID is missing or undefined!');
      console.error('💡 Solution: Create frontend/.env file with:');
      console.error('   REACT_APP_GOOGLE_CLIENT_ID=your-client-id-here');
      console.error('💡 Then restart: npm start');
      return;
    }

    // טעינת Google Identity Services SDK
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    
    script.onload = () => {
      console.log('✅ Google SDK loaded successfully');
      
      try {
        console.log('🔄 Initializing Google Identity...');
        console.log('   Using Client ID:', clientId.substring(0, 20) + '...');
        
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: handleCredentialResponse,
          ux_mode: 'popup',
          auto_select: false
        });

        console.log('✅ Google Identity initialized');
        console.log('🔄 Rendering button...');

        window.google.accounts.id.renderButton(
          document.getElementById('google-signin-button'),
          { 
            theme: 'outline', 
            size: 'large',
            text: 'signin_with',
            shape: 'rectangular',
            locale: 'he',
            width: 300
          }
        );

        console.log('✅ Button rendered successfully');
        
      } catch (error) {
        console.error('❌ Error during initialization:', error);
      }
    };

    script.onerror = () => {
      console.error('❌ Failed to load Google SDK script');
    };

    document.body.appendChild(script);
    console.log('📥 Google SDK script added to page');

    return () => {
      const scriptElement = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
      if (scriptElement) {
        document.body.removeChild(scriptElement);
        console.log('🧹 Cleaned up Google SDK script');
      }
    };
  }, []);

  const handleCredentialResponse = async (response) => {
    try {
      console.log('🎉 Google credential received!');
      console.log('📦 Credential length:', response.credential?.length);
      await onSuccess(response.credential);
    } catch (error) {
      console.error('❌ Google login failed:', error);
      onError(error);
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
      <div id="google-signin-button"></div>
      
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