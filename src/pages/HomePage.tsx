import React, { useState, useEffect } from 'react';
import { api } from '../lib/api';
import type { Track, Collection } from '../lib/types';
import { usePlayerContext } from '../components/PlayerProvider';

export default function HomePage() {
  const [collections, setCollections] = useState<Collection[]>([]);
  const [loading, setLoading] = useState(true);
  const { play } = usePlayerContext();

  useEffect(() => {
    api<Collection[]>('/api/collections')
      .then(setCollections)
      .catch(() => setCollections([]))
      .finally(() => setLoading(false));
  }, []);

  const handlePlay = (track: Track) => {
    play(track);
  };

  if (loading) {
    return <div className="page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>Загрузка...</div>;
  }

  return (
    <div>
      <header className="header">
        <h1>ЖилиБыли</h1>
      </header>
      
      {collections.map(collection => (
        <section key={collection.id} style={{ marginBottom: '1.5rem' }}>
          <h2 style={{ padding: '0 1rem', fontSize: '1.25rem', marginBottom: '0.75rem' }}>
            {collection.title}
          </h2>
          <div className="grid">
            {collection.tracks.slice(0, 6).map(track => (
              <div
                key={track.id}
                className="track-card"
                onClick={() => handlePlay(track)}
              >
                {track.coverUrl && (
                  <img src={track.coverUrl} alt="" className="track-cover" />
                )}
                <div className="track-title">{track.title}</div>
                <div className="track-artist">{track.artist}</div>
              </div>
            ))}
          </div>
        </section>
      ))}

      {collections.length === 0 && (
        <div style={{ padding: '2rem 1rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
          Нет доступных коллекций
        </div>
      )}
    </div>
  );
}
