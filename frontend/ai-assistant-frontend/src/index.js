import React, { useState, useEffect } from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";

import Home from "./pages/Home";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Assistant from "./pages/Assistant";
import ProtectedRoute from "./components/ProtectedRoute";
import { setupAxiosInterceptors } from "./api/axiosConfig";

function GlobalLoader({ isLoading }) {
  if (!isLoading) return null;

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0, 0, 0, 0.7)', display: 'flex',
      flexDirection: 'column', alignItems: 'center', justifyContent: 'center', zIndex: 9999
    }}>
      <div style={{
        width: '50px', height: '50px', border: '5px solid #f3f3f3',
        borderTop: '5px solid #3498db', borderRadius: '50%',
        animation: 'spin 1s linear infinite' // Uses the keyframes from index.css
      }}></div>
      <p style={{ color: 'white', marginTop: '15px', fontSize: '18px', fontWeight: 'bold', textAlign: 'center', fontFamily: 'sans-serif' }}>
        Connecting to Cloud Server... <br />
        <span style={{ fontSize: '14px', fontWeight: 'normal', color: '#ccc' }}>(May take up to 50s or more if server is sleeping)</span>
      </p>
    </div>
  );
}

function MainApp() {
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    // Connect our local state hook straight into the Axios interceptor logic
    setupAxiosInterceptors(setIsLoading);
  }, []);

  return (
    <BrowserRouter>
      <GlobalLoader isLoading={isLoading} />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/chat" element={
          <ProtectedRoute>
            <Assistant />
          </ProtectedRoute>
        } />
      </Routes>
    </BrowserRouter>
  );
}

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <React.StrictMode>
    <MainApp />
  </React.StrictMode>
);