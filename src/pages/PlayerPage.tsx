import React, { useState } from 'react'
import { usePlayer } from '../components/PlayerProvider'
import { formatDuration } from '../lib/queue'
import { Track } from '../lib/queue'

export default function PlayerPage() {
  const { queue, currentIndex, isPlaying, currentTrack, playTrack, playPause, next, prev, removeFromQueue } = usePlayer()
  const [tab, setTab] = useState<'lyrics' | 'queue'>('queue')

  return (
    <div className="min-h-screen bg-[#f3f1fa] pb-24">
      {/* Мини-плеер */}
      <div className="bg-white px-4 pt-4 pb-3 shadow-sm">
        <div className="flex items-center gap-3">
          <img
            src={currentTrack?.cover || 'https://picsum.photos/seed/player/200/200'}
            alt=""
            className="w-14 h-14 rounded-xl object-cover flex-shrink-0"
          />
          <div className="flex-1 min-w-0">
            <div className="font-bold text-lg text-gray-900 truncate">{currentTrack?.title || 'КИС Ю'}</div>
            <div className="text-sm text-gray-500 truncate">{currentTrack?.artist || 'NEXTIME, Даник'}</div>
          </div>
          <button onClick={prev} className="p-2 text-gray-800" aria-label="Предыдущий">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
          </button>
          <button
            onClick={playPause}
            className="w-14 h-14 rounded-full bg-[#4a2d96] text-white flex items-center justify-center flex-shrink-0"
            aria-label={isPlaying ? 'Пауза' : 'Играть'}
          >
            {isPlaying ? (
              <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
            ) : (
              <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
            )}
          </button>
          <button onClick={next} className="p-2 text-gray-800" aria-label="Следующий">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
          </button>
          <button
            onClick={() => { if (currentTrack) removeFromQueue(currentTrack.id) }}
            className="p-2 text-gray-500"
            aria-label="Закрыть"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>

        {/* Табы Текст / Очередь */}
        <div className="flex gap-3 mt-4">
          <button
            onClick={() => setTab('lyrics')}
            className={`flex-1 py-3 rounded-full text-base font-medium border transition-colors ${
              tab === 'lyrics'
                ? 'bg-[#4a2d96] text-white border-[#4a2d96]'
                : 'bg-white text-gray-700 border-gray-300'
            }`}
          >
            Текст
          </button>
          <button
            onClick={() => setTab('queue')}
            className={`flex-1 py-3 rounded-full text-base font-medium border transition-colors ${
              tab === 'queue'
                ? 'bg-[#4a2d96] text-white border-[#4a2d96]'
                : 'bg-white text-gray-700 border-gray-300'
            }`}
          >
            Очередь
          </button>
        </div>
      </div>

      {/* Контент */}
      {tab === 'lyrics' ? (
        <div className="px-6 py-10 text-center text-gray-500">
          <p>Текст песни</p>
          <p className="mt-2 text-sm">{currentTrack?.title || 'Выберите трек'}</p>
        </div>
      ) : (
        <div className="px-4 pt-4">
          <h2 className="text-lg font-bold text-gray-900 mb-3">
            Очередь · {queue.length} {queue.length === 1 ? 'трек' : queue.length < 5 ? 'трека' : 'треков'}
          </h2>

          {queue.length === 0 ? (
            <div className="text-center text-gray-400 py-10">Очередь пуста</div>
          ) : (
            <div className="bg-white rounded-2xl overflow-hidden shadow-sm">
              {queue.map((track: Track, idx: number) => {
                const isCurrent = idx === currentIndex
                return (
                  <div
                    key={track.id}
                    className={`flex items-center gap-3 px-3 py-3 ${isCurrent ? 'bg-[#efeafb]' : 'bg-white'} ${idx !== queue.length - 1 ? 'border-b border-gray-100' : ''}`}
                  >
                    <img
                      src={track.cover}
                      alt=""
                      className="w-12 h-12 rounded-lg object-cover flex-shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      {isCurrent && (
                        <div className="text-[10px] font-bold tracking-widest text-[#4a2d96] mb-0.5">
                          СЕЙЧАС ИГРАЕТ
                        </div>
                      )}
                      <div className={`font-medium truncate ${isCurrent ? 'text-[#4a2d96]' : 'text-gray-900'}`}>
                        {track.title}
                      </div>
                      <div className="text-sm text-gray-500 truncate">{track.artist}</div>
                    </div>
                    <button
                      onClick={() => removeFromQueue(track.id)}
                      className="p-2 text-gray-400 hover:text-gray-700"
                      aria-label="Удалить из очереди"
                    >
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
                    </button>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
