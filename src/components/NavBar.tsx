import React from 'react'
import { Link, useLocation } from 'react-router-dom'

export default function NavBar() {
  const location = useLocation()

  return (
    <nav className="border-b border-gray-200 bg-white/90 backdrop-blur-md p-2 fixed bottom-0 left-0 right-0 z-50">
      <div className="max-w-2xl mx-auto flex justify-around text-sm">
        <Link
          to="/"
          className={`flex flex-col items-center gap-1 px-3 py-2 text-xs font-medium transition-colors ${
            location.pathname === '/' ? 'text-[#4a2d96]' : 'text-gray-500'
          }`}
        >
          <span className={location.pathname === '/' ? 'text-[#4a2d96]' : 'text-gray-400'}>📂</span>
          <span>Каталог</span>
        </Link>
        <Link
          to="/player"
          className={`flex flex-col items-center gap-1 px-3 py-2 text-xs font-medium transition-colors ${
            location.pathname === '/player' ? 'text-[#4a2d96]' : 'text-gray-500'
          }`}
        >
          <span className={location.pathname === '/player' ? 'text-[#4a2d96]' : 'text-gray-400'}>🎧</span>
          <span>Плеер</span>
        </Link>
      </div>
    </nav>
  )
}