import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

interface DownloadTrack {
  id: string
  title: string
  artist: string
  progress: number
  status: 'downloading' | 'paused' | 'completed' | 'error'
  coverUrl?: string
}

export default function DownloadManagerPage() {
  const navigate = useNavigate()
  const [tracks, setTracks] = useState<DownloadTrack[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Fetch downloads from API
    async function fetchDownloads() {
      try {
        const res = await fetch('https://zhilibyli.online/api/catalog/downloads', {
          credentials: 'include',
        })
        const data = await res.json()
        setTracks(data.items || [])
      } catch (e) {
        console.error('Failed to fetch downloads', e)
      } finally {
        setLoading(false)
      }
    }

    fetchDownloads()
  }, [navigate])

  if (loading) {
    return <div className="p-8 text-center">Загрузка...</div>
  }

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold mb-4">Загрузки</h1>
      {tracks.length === 0 && <p className="text-gray-600">Нет активных загрузок</p>}
      <ul className="space-y-3">
        {tracks.map((track) => (
          <li key={track.id} className="p-3 border rounded bg-gray-50">
            <img
              src={track.coverUrl}
              alt={track.title}
              className="w-12 h-12 object-cover rounded mr-3"
            />
            <div className="flex-1">
              <div className="flex justify-between mb-1">
                <span className="font-medium">{track.title}</span>
                <span className="text-xs text-gray-500">{track.artist}</span>
              </div>
              <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-green-500 rounded-full"
                  style={{ width: `${track.progress}%` }}
                />
              </div>
              <div className="text-xs mt-1">
                {track.progress}% {track.status}
              </div>
            </div>
            <div className="mt-2 flex justify-between text-xs">
              <button>Пауза</button>
              <button>Отмена</button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}