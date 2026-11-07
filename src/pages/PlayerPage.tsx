import React, { useState, useEffect } from 'react';
import { usePlayerContext } from '../components/PlayerProvider';

interface PlayerPageProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function PlayerPage({ isOpen, onClose }: PlayerPageProps) {
  const { currentTrack, isPlaying, progress, duration, toggle, seek } = usePlayerContext();
  const [showLyrics, setShowLyrics] = useState(false);

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  const handleSeek = (time: number) => {
    if (currentTrack) {
      seek(time);
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (!isOpen || !currentTrack) return null;

  const progressPercent = duration > 0 ? (progress / duration) * 100 : 0;

  return (
    <>
      <div className="overlay" onClick={onClose}></div>
      <div className="player-page" onClick={(e) => e.stopPropagation()}>
        <header className="header">
          <button className="back-link" onClick={onClose}>← На главную</button>
          <h1>{currentTrack.title}</h1>
        </header>

        <div className="player-container">
          <div className="player-cover-container">
            <img src={currentTrack.coverUrl || '/iconss.png'} alt="" className="player-cover" />
          </div>

          <div className="player-info">
            <h2 className="player-title">{currentTrack.title}</h2>
            <p className="player-artist">{currentTrack.artist}</p>
            <div className="player-buttons">
              <button className="btn btn-primary" onClick={() => setShowLyrics(!showLyrics)}>
                {showLyrics ? 'Очередь' : 'Текст'}
              </button>
            </div>
          </div>

          <div className="player-progress-container">
            <div className="player-progress-bar" onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect();
              const percent = (e.clientX - rect.left) / rect.width;
              handleSeek(percent * duration);
            }}>
              <div className="player-progress-fill" style={{ width: `${progressPercent}%` }} />
            </div>
            <div className="player-time">
              <span>{formatTime(progress)}</span>
              <span>{formatTime(duration)}</span>
            </div>
          </div>

          <div className="player-controls">
            <button className="control-btn large" onClick={toggle} aria-label={isPlaying ? 'Пауза' : 'Играть'}>
              {isPlaying ? (
                <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M6 19h4v-2H6v2zm8-14v14h4V5h-4z" />
                </svg>
              ) : (
                <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8 5v14l11-7z" />
                </svg>
              )}
            </button>
            <button className="control-btn" onClick={() => seek(progress - 10)} aria-label="Назад 10 сек">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
                <path d="M15 16l-5-5v14l5 5" />
              </svg>
            </button>
            <button className="control-btn" onClick={() => seek(progress + 10)} aria-label="Вперед 10 сек">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 16l5 5 5-5" />
              </svg>
            </button>
            <button className="control-btn" onClick={toggle} aria-label="Повтор">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 11l-1 2v7h14v2h5" />
              </svg>
            </button>
          </div>

          <div className="lyrics-container" style={{ display: showLyrics ? 'block' : 'none' }}>
            <div className="lyrics-content">
              Текст песни будет здесь
            </div>
          </div>
        </div>

        <footer className="footer">
          <p>ЖилиБыли — музыка рядом</p>
        </footer>
      </div>
    </>
  );
}