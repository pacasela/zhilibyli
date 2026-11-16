import React from 'react'
import { Link } from 'react-router-dom'

export default function NavBar() {
  return (
    <nav className="border-b border-gray-200 bg-white p-2 fixed bottom-0 left-0 right-0 z-50">
      <div className="max-w-2xl mx-auto flex justify-around text-sm">
        <Link to="/">
          {/** Home */}

          <span>Каталог</span>
        </Link>
        <Link to="/player">
          {/** Player */}

          <span>Плеер</span>
        </Link>
        <Link to="/downloads">
          {/** Downloads - new */}

          <span>Загрузки</span>
        </Link>
      </div>
    </nav>
  )
}