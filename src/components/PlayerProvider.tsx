import React from 'react';
import { usePlayer } from '../hooks/usePlayer';
import { useOffline } from '../hooks/useOffline';
import type { Track } from '../lib/types';
import { MediaSession } from '@capgo/capacitor-media-session';

interface PlayerContextType extends ReturnType<typeof usePlayer> {
  offline: ReturnType<typeof useOffline>;
  childMode: boolean;
  setChildMode: (value: boolean) => void;
}

const PlayerContext = React.createContext<PlayerContextType | null>(null);

export function PlayerProvider({ children }: { children: React.ReactNode }) {
  const player = usePlayer();
  const offline = useOffline();
  const [childMode, setChildMode] = React.useState(false);

  React.useEffect(() => {
    document.body.classList.toggle('child-mode', childMode);
  }, [childMode]);

  React.useEffect(() => {
    let interval: NodeJS.Timeout | null = null;

    const initMediaSession = async () => {
      try {
        const metadata: any = {
          title: player.currentTrack?.title || '',
          artist: player.currentTrack?.artist || '',
        };
        if (player.currentTrack) {
          metadata.album = (player.currentTrack as any).album || '';
          metadata.artwork = (player.currentTrack as any).coverUrl || '';
          metadata.artworkUri = (player.currentTrack as any).coverUrl
            ? (player.currentTrack as any).coverUrl
            : undefined;
        }
        await MediaSession.setMetadata(metadata);
        await MediaSession.setPlaybackState({
          playbackState: player.isPlaying ? 'playing' : 'paused',
        });
        await MediaSession.setPositionState({
          position: player.progress,
          duration: player.duration,
        });

        await MediaSession.setActionHandler({ action: 'play' }, async () => {
          if (player.currentTrack) await player.play(player.currentTrack);
        });
        await MediaSession.setActionHandler({ action: 'pause' }, () => player.pause());
        await MediaSession.setActionHandler({ action: 'stop' }, () => player.pause());
        await MediaSession.setActionHandler({ action: 'nexttrack' }, () => console.log('Next track requested'));
        await MediaSession.setActionHandler({ action: 'previoustrack' }, () => console.log('Previous track requested'));
        await MediaSession.setActionHandler({ action: 'seekbackward' }, () => player.seek(Math.max(0, player.progress - 15)));
        await MediaSession.setActionHandler({ action: 'seekforward' }, () => player.seek(Math.min(player.duration, player.progress + 15)));
        await MediaSession.setActionHandler({ action: 'seekto' }, (data: any) => {
          if (data && data.seekTime !== undefined) player.seek(data.seekTime);
        });

        const updateMediaState = async () => {
          await MediaSession.setPlaybackState({
            playbackState: player.isPlaying ? 'playing' : 'paused',
          });
          await MediaSession.setPositionState({
            position: player.progress,
            duration: player.duration,
          });
        };
        interval = setInterval(updateMediaState, 500);
      } catch (error) {
        console.warn('Media session not available:', error);
      }
    };

    initMediaSession();
    return () => { if (interval) clearInterval(interval); };
  }, [player.currentTrack, player.isPlaying, player.progress, player.duration, player.play, player.pause, player.seek]);

  return (
    <PlayerContext.Provider value={{ ...player, offline, childMode, setChildMode }}>
      {children}
    </PlayerContext.Provider>
  );
}

export function usePlayerContext() {
  const ctx = React.useContext(PlayerContext);
  if (!ctx) throw new Error('usePlayerContext must be used within PlayerProvider');
  return ctx;
}