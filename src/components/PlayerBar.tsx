import React, { useState } from 'react';
import { usePlayerContext } from '../components/PlayerProvider';
import type { Track } from '../lib/types';

interface PlayerBarProps {
  track: Track;
  isPlaying: boolean;
  progress: number;
  duration: number;
  onToggle: () => void;
  onSeek: (time: number) => void;
  onOpenPlayer: () => void;
}

export function PlayerBar({ track, isPlaying, progress, duration, onToggle, onSeek, onOpenPlayer }: PlayerBarProps) {
  const { offline } = usePlayerContext();
  const { download, remove, isOffline, loading } = offline;
  const [childMode, setChildMode] = useState(false);

  const formatTime = (seconds: number) => {
    if (!seconds || isNaN(seconds)) return '0:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const handleDownload = async () => {
    await download(track);
  };

  const handleRemove = async () => {
    await remove(track.id);
  };

  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const percent = (e.clientX - rect.left) / rect.width;
    onSeek(percent * duration);
  };

  const toggleChildMode = () => setChildMode(!childMode);

  return (
    <div className="player-bar">
      <div className="player-info" onClick={onOpenPlayer}>
        <img src={track.coverUrl || '/iconss.png'} alt="" className="player-cover-mini" />
        <div className="player-text">
          <div className="player-title">{track.title}</div>
          <div className="player-artist">{track.artist}</div>
        </div>
      </div>
      <div className="player-controls-mini">
        <button
          className="download-btn"
          onClick={handleDownload}
          disabled={loading || isOffline(track.id)}
          aria-label="Скачать"
        >
          {loading ? (
            <span className="spin">↻</span>
          ) : isOffline(track.id) ? (
            '✓'
          ) : (
            '↓'
          )}
        </button>
        <button className="play-btn" onClick={onToggle} aria-label={isPlaying ? 'Пауза' : 'Играть'}>
          {isPlaying ? '⏸' : '▶'}
        </button>
        <button className="remove-btn" onClick={handleRemove} aria-label="Удалить">
          🗑
        </button>
      </div>
      <div className="player-progress-bar" onClick={handleSeek}>
        <div className="player-progress-fill" style={{ width: `${progress}%` }} />
      </div>
      <div className="player-time">
        <span>{formatTime((progress / 100) * duration)}</span>
        <span>{formatTime(duration)}</span>
      </div>
    </div>
  );
}