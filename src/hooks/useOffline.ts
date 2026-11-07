import { useState, useEffect } from 'react';
import { listOffline, saveOffline, deleteOffline } from '../lib/offline';
import type { Track } from '../lib/types';

export interface OfflineTrack extends Track {
  savedAt: number;
}

export function useOffline() {
  const [offlineTracks, setOfflineTracks] = useState<OfflineTrack[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    listOffline().then(tracks => {
      if (mounted) {
        setOfflineTracks(tracks.map(t => ({
          id: t.id,
          title: t.title,
          artist: t.artist,
          coverUrl: t.coverUrl,
          audioUrl: '',
          duration: t.duration,
          savedAt: t.savedAt,
        })));
        setLoading(false);
      }
    });
    return () => { mounted = false; };
  }, []);

  const download = async (track: Track): Promise<boolean> => {
    const success = await saveOffline(track);
    if (success) {
      setOfflineTracks(prev => [...prev, { ...track, savedAt: Date.now() }]);
    }
    return success;
  };

  const remove = async (trackId: string): Promise<void> => {
    await deleteOffline(trackId);
    setOfflineTracks(prev => prev.filter(t => t.id !== trackId));
  };

  const isOffline = (trackId: string): boolean => {
    return offlineTracks.some(t => t.id === trackId);
  };

  return { offlineTracks, loading, download, remove, isOffline };
}
