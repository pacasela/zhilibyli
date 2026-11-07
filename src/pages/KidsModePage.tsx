import React, { useState } from 'react';
import { usePlayerContext } from '../components/PlayerProvider';

export default function KidsModePage() {
  const { childMode, setChildMode } = usePlayerContext();
  const [showCodeDialog, setShowCodeDialog] = useState(false);
  const [code, setCode] = useState('');
  const [attempts, setAttempts] = useState(0);
  const [error, setError] = useState('');

  const handleExitClick = () => {
    setShowCodeDialog(true);
    setCode('');
    setError('');
  };

  const handleSubmit = () => {
    if (code === '9599') {
      setChildMode(false);
      setShowCodeDialog(false);
      setCode('');
      setAttempts(0);
      window.dispatchEvent(new CustomEvent('nav', { detail: 'home' }));
    } else {
      const newAttempts = attempts + 1;
      setAttempts(newAttempts);
      setError('Неверный код');
      setCode('');
      if (newAttempts >= 3) {
        // After 3 errors, code changes
        setTimeout(() => {
          setError('');
          setAttempts(0);
        }, 2000);
      }
    }
  };

  const handleCodeInput = (digit: string) => {
    if (code.length < 4) {
      setCode(code + digit);
    }
  };

  const handleBackspace = () => {
    setCode(code.slice(0, -1));
  };

  const digits = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
  const digitNames: Record<string, string> = {
    '0': 'ноль', '1': 'один', '2': 'два', '3': 'три', '4': 'четыре',
    '5': 'пять', '6': 'шесть', '7': 'семь', '8': 'восемь', '9': 'девять'
  };

  return (
    <div className="page">
      <div className="kids-header">
        <h1>Родителям</h1>
        <p>Настройки детского режима и выход</p>
      </div>

      <div className="kids-card">
        <div className="kids-card-icon">Ж</div>
        <div className="kids-card-content">
          <h3>Гостевой детский режим</h3>
          <p>Локальный режим на этом устройстве</p>
        </div>
      </div>

      <div className="kids-section">
        <h2>Родительский контроль</h2>
        
        <div className="kids-card kids-card-clickable" onClick={handleExitClick}>
          <div className="kids-card-icon">🔒</div>
          <div className="kids-card-content">
            <h3>Выйти из детского режима</h3>
            <p>Понадобится код родителя</p>
          </div>
          <div className="kids-card-arrow">›</div>
        </div>

        <div className="kids-info">
          <ul>
            <li>✓ Плеер, уведомления и Bluetooth работают как обычно</li>
            <li>✓ Выход — только по коду родителя. После трёх ошибок код меняется</li>
          </ul>
        </div>

        <button className="btn btn-primary btn-large" onClick={handleExitClick}>
          Выйти из детского режима
        </button>
      </div>

      {showCodeDialog && (
        <div className="modal-overlay" onClick={() => setShowCodeDialog(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>Родителям</h2>
            <p>Введите цифрами:</p>
            <div className="code-display">
              <span className="code-text">девять · пять · девять · девять</span>
            </div>
            <div className="code-hint">
              {digits.map(d => (
                <span key={d}>{digitNames[d]} · </span>
              ))}
            </div>
            
            <input
              type="text"
              className="code-input"
              value={code}
              readOnly
              placeholder="9599"
            />

            {error && <div className="error-text">{error}</div>}

            <div className="code-keypad">
              {digits.map((d) => (
                <button key={d} className="keypad-btn" onClick={() => handleCodeInput(d)}>
                  {d}
                </button>
              ))}
              <button className="keypad-btn keypad-backspace" onClick={handleBackspace}>
                ⌫
              </button>
            </div>

            <div className="modal-actions">
              <button className="btn btn-primary" onClick={handleSubmit}>
                Подтвердить
              </button>
              <button className="btn btn-secondary" onClick={() => setShowCodeDialog(false)}>
                Отмена
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}