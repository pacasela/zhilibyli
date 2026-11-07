import React from 'react';
import type { OfflineTrack } from '../hooks/useOffline';

interface LibraryPageProps {
  offline: {
    offlineTracks: OfflineTrack[];
    loading: boolean;
    isOffline: (id: string) => boolean;
    download: (track: any) => Promise<boolean>;
    remove: (id: string) => Promise<void>;
  };
}

export default function LibraryPage({ offline }: LibraryPageProps) {
  const { offlineTracks, loading } = offline;

  if (loading) {
    return <div style={{ padding: '1rem' }}>Загрузка...</div>;
  }

  return (
    <div>
      <header className="header">
        <h1>Библиотека</h1>
      </header>

      <section style={{ padding: '1rem' }}>
        <h2 style={{ fontSize: '1.125rem', marginBottom: '0.75rem' }}>
          Оффлайн ({offlineTracks.length})
        </h2>
        {offlineTracks.length === 0 ? (
          <div style={{ color: 'var(--text-secondary)', padding: '1rem', background: 'var(--bg-secondary)', borderRadius: '0.5rem' }}>
            Нет оффлайн-треков. Скачивайте музыку для прослушивания без интернета.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {offlineTracks.map(track => (
              <div
                key={track.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.75rem',
                  background: 'var(--bg-secondary)',
                  borderRadius: '0.5rem',
                }}
              >
                {track.coverUrl && (
                  <img
                    src={track.coverUrl}
                    alt=""
                    style={{ width: '48px', height: '48px', borderRadius: '0.25rem', objectFit: 'cover' }}
                  />
                )}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {track.title}
                  </div>
                  <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    {track.artist}
                  </div>
                </div>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="var(--accent)">
                  <path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/>
                </svg>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
