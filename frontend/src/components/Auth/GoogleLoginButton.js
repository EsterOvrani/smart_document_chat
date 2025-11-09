import React, { useEffect } from 'react';

const GoogleLoginButton = ({ onSuccess, onError }) => {
  useEffect(() => {
    // טעינת Google Identity Services SDK
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    document.body.appendChild(script);

    script.onload = () => {
      window.google.accounts.id.initialize({
        client_id: process.env.REACT_APP_GOOGLE_CLIENT_ID,
        callback: handleCredentialResponse
      });

      window.google.accounts.id.renderButton(
        document.getElementById('google-signin-button'),
        { 
          theme: 'outline', 
          size: 'large',
          text: 'signin_with',
          shape: 'rectangular',
          locale: 'he'
        }
      );
    };

    return () => {
      document.body.removeChild(script);
    };
  }, []);

  const handleCredentialResponse = async (response) => {
    try {
      console.log('Google credential received');
      await onSuccess(response.credential);
    } catch (error) {
      console.error('Google login failed:', error);
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