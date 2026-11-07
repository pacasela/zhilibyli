import React from 'react';

export default function ProfilePage() {
  return (
    <div>
      <header className="header">
        <h1>Профиль</h1>
      </header>

      <div style={{ padding: '1rem' }}>
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
            <div
              style={{
                width: '64px',
                height: '64px',
                borderRadius: '50%',
                background: 'var(--accent)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.5rem',
                fontWeight: 700,
              }}
            >
              Г
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: '1.125rem' }}>Гость</div>
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
                Бесплатный аккаунт
              </div>
            </div>
          </div>
        </div>

        <div className="card" style={{ marginBottom: '1rem' }}>
          <h3 style={{ fontSize: '1rem', marginBottom: '0.75rem' }}>Оффлайн</h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Скачивайте треки для прослушивания без интернета.
          </p>
        </div>

        <div className="card">
          <h3 style={{ fontSize: '1rem', marginBottom: '0.75rem' }}>О приложении</h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            ЖилиБыли — музыкальный сервис для детей и взрослых.
          </p>
        </div>
      </div>
    </div>
  );
}
