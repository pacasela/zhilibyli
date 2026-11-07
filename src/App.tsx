import React, { useState, useEffect } from 'react';
import { PlayerProvider, usePlayerContext } from './components/PlayerProvider';
import { NavBar } from './components/NavBar';
import { PlayerBar } from './components/PlayerBar';
import HomePage from './pages/HomePage';
import LibraryPage from './pages/LibraryPage';
import ProfilePage from './pages/ProfilePage';
import KidsModePage from './pages/KidsModePage';
import PlayerPage from './pages/PlayerPage';

function AppContent() {
  const { currentTrack, isPlaying, progress, duration, toggle, seek, offline } = usePlayerContext();
  const [page, setPage] = useState<'home' | 'library' | 'profile' | 'kids'>('home');
  const [isPlayerOpen, setIsPlayerOpen] = useState(false);

  useEffect(() => {
    const handleNav = (e: CustomEvent<'home' | 'library' | 'profile' | 'kids'>) => {
      setPage(e.detail);
    };
    window.addEventListener('nav' as any, handleNav as any);
    return () => window.removeEventListener('nav' as any, handleNav as any);
  }, []);

  return (
    <>
      <div className="page">
        {page === 'home' && <HomePage />}
        {page === 'library' && <LibraryPage offline={offline} />}
        {page === 'profile' && <ProfilePage />}
        {page === 'kids' && <KidsModePage />}
        {currentTrack && (
          <PlayerBar
            track={currentTrack}
            isPlaying={isPlaying}
            progress={progress}
            duration={duration}
            onToggle={toggle}
            onSeek={seek}
            onOpenPlayer={() => setIsPlayerOpen(true)}
          />
        )}
        <PlayerPage
          isOpen={isPlayerOpen}
          onClose={() => setIsPlayerOpen(false)}
        />
      </div>
    </>
  );
}

export default function App() {
  return (
    <PlayerProvider>
      <AppContent />
      <NavBar />
    </PlayerProvider>
  );
}