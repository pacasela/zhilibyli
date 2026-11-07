import { useState, useEffect, useRef, useCallback } from 'react';
import { getOfflineAudioUrl } from '../lib/offline';
import type { Track } from '../lib/types';

export interface PlayerState {
  currentTrack: Track | null;
  isPlaying: boolean;
  progress: number;
  duration: number;
  volume: number;
}

export function usePlayer() {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [state, setState] = useState<PlayerState>({
    currentTrack: null,
    isPlaying: false,
    progress: 0,
    duration: 0,
    volume: 1,
  });

  useEffect(() => {
    const audio = new Audio();
    audioRef.current = audio;

    const updateProgress = () => {
      setState(prev => ({
        ...prev,
        progress: audio.currentTime,
        duration: audio.duration || 0,
      }));
    };

    const handleEnded = () => {
      setState(prev => ({ ...prev, isPlaying: false, progress: 0 }));
    };

    audio.addEventListener('timeupdate', updateProgress);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('loadedmetadata', updateProgress);

    return () => {
      audio.pause();
      audio.removeEventListener('timeupdate', updateProgress);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('loadedmetadata', updateProgress);
    };
  }, []);

  const play = useCallback(async (track: Track) => {
    if (!audioRef.current) return;

    const audio = audioRef.current;
    
    // Try offline first
    try {
      const offlineUrl = await getOfflineAudioUrl(track.id);
      if (offlineUrl) {
        audio.src = offlineUrl;
      } else {
        audio.src = track.audioUrl;
      }
    } catch {
      audio.src = track.audioUrl;
    }

    audio.play().catch(() => {
      // Autoplay may be blocked
    });

    setState(prev => ({
      ...prev,
      currentTrack: track,
      isPlaying: true,
    }));
  }, []);

  const pause = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
    }
    setState(prev => ({ ...prev, isPlaying: false }));
  }, []);

  const toggle = useCallback(() => {
    if (state.isPlaying) {
      pause();
    } else if (state.currentTrack) {
      play(state.currentTrack);
    }
  }, [state.isPlaying, state.currentTrack, play, pause]);

  const seek = useCallback((time: number) => {
    if (audioRef.current) {
      audioRef.current.currentTime = time;
    }
    setState(prev => ({ ...prev, progress: time }));
  }, []);

  const setVolume = useCallback((volume: number) => {
    if (audioRef.current) {
      audioRef.current.volume = volume;
    }
    setState(prev => ({ ...prev, volume }));
  }, []);

  return {
    ...state,
    play,
    pause,
    toggle,
    seek,
    setVolume,
  };
}
