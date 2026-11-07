import React from 'react';
import { useLocation } from 'react-router-dom';

export function NavBar() {
  const location = useLocation();

  const navigate = (page: string) => {
    window.dispatchEvent(new CustomEvent('nav', { detail: page }));
  };

  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className="nav-bar">
      <button className={`nav-item ${isActive('/') ? 'active' : ''}`} onClick={() => navigate('home')}>
        <svg className="nav-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>
        </svg>
        <span>Главная</span>
      </button>
      <button className={`nav-item ${isActive('/library') ? 'active' : ''}`} onClick={() => navigate('library')}>
        <svg className="nav-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z"/>
        </svg>
        <span>Библиотека</span>
      </button>
      <button className={`nav-item ${isActive('/profile') ? 'active' : ''}`} onClick={() => navigate('profile')}>
        <svg className="nav-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
        </svg>
        <span>Профиль</span>
      </button>
      <button className={`nav-item ${isActive('/kids') ? 'active' : ''}`} onClick={() => navigate('kids')}>
        <svg className="nav-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 21v-11c0-2.21-2.2-4-4-4h-4c-2.21 0-4 1.79-4 4v9h24z"/>
        </svg>
        <span>Детский режим</span>
      </button>
    </nav>
  );
}