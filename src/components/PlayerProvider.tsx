import React, { createContext, useContext, useCallback, useState } from 'react'
import { Track } from '../lib/queue'

interface PlayerContextType {
  queue: Track[]
  currentIndex: number | null
  isPlaying: boolean
  currentTrack: Track | null
  playTrack: (track: Track, index: number) => void
  playPause: () => void
  next: () => void
  prev: () => void
  removeFromQueue: (trackId: string) => void
}

const PlayerContext = createContext<PlayerContextType | null>(null)

export function PlayerProvider({ children }: { children: React.ReactNode }) {
  const [queue, setQueue] = useState<Track[]>([])
  const [currentIndex, setCurrentIndex] = useState<number | null>(null)
  const [isPlaying, setIsPlaying] = useState(false)

  const currentTrack = currentIndex !== null ? queue[currentIndex] : null

  const playTrack = useCallback((track: Track, index: number) => {
    setQueue((prev) => {
      if (prev.some((t) => t.id === track.id)) return prev
      return [...prev, track]
    })
    setCurrentIndex(index)
    setIsPlaying(true)
  }, [])

  const playPause = useCallback(() => {
    setIsPlaying((p) => !p)
  }, [])

  const next = useCallback(() => {
    setCurrentIndex((i) => {
      if (i === null || i >= queue.length - 1) return null
      return i + 1
    })
  }, [queue.length])

  const prev = useCallback(() => {
    setCurrentIndex((i) => {
      if (i === null || i <= 0) return null
      return i - 1
    })
  }, [queue.length])

  const removeFromQueue = useCallback((trackId: string) => {
    setQueue((prev) => {
      const next = prev.filter((t) => t.id !== trackId)
      if (next.length === 0) {
        setCurrentIndex(null)
        setIsPlaying(false)
      }
      return next
    })
  }, [])

  return (
    <PlayerContext.Provider value={{ queue, currentIndex, isPlaying, currentTrack, playTrack, playPause, next, prev, removeFromQueue }}>
      {children}
    </PlayerContext.Provider>
  )
}

export function usePlayer() {
  const ctx = useContext(PlayerContext)
  if (!ctx) throw new Error('usePlayer must be used within PlayerProvider')
  return ctx
}
