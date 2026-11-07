import { openDB, IDBPDatabase } from 'idb';

const DB_NAME = 'zhilibyli-offline';
const STORE_NAME = 'tracks';

interface OfflineTrack {
  id: string;
  title: string;
  artist: string;
  coverUrl?: string;
  audioBlob: Blob;
  duration?: number;
  savedAt: number;
}

let dbPromise: Promise<IDBPDatabase<{
  tracks: {
    key: string;
    value: OfflineTrack;
    indexes: { 'by-saved': number };
  };
}> | null> | null = null;

async function openDb(): Promise<IDBPDatabase<{
  tracks: {
    key: string;
    value: OfflineTrack;
    indexes: { 'by-saved': number };
  };
}> | null> {
  if (!dbPromise) {
    try {
      dbPromise = openDB(DB_NAME, 1, {
        upgrade(db) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
          store.createIndex('by-saved', 'savedAt');
        },
      });
    } catch {
      dbPromise = Promise.resolve(null);
    }
  }
  return dbPromise;
}

export async function saveOffline(track: {
  id: string;
  title: string;
  artist: string;
  coverUrl?: string;
  audioUrl: string;
  duration?: number;
}): Promise<boolean> {
  const db = await openDb();
  if (!db) return false;

  try {
    const audioBlob = await fetch(track.audioUrl).then(r => r.blob());
    await db.put(STORE_NAME, {
      id: track.id,
      title: track.title,
      artist: track.artist,
      coverUrl: track.coverUrl,
      audioBlob,
      duration: track.duration,
      savedAt: Date.now(),
    });
    return true;
  } catch {
    return false;
  }
}

export async function getOffline(trackId: string): Promise<OfflineTrack | null> {
  const db = await openDb();
  if (!db) return null;
  const track = await db.get(STORE_NAME, trackId);
  return track ?? null;
}

export async function listOffline(): Promise<OfflineTrack[]> {
  const db = await openDb();
  if (!db) return [];
  return await db.getAll(STORE_NAME);
}

export async function deleteOffline(trackId: string): Promise<void> {
  const db = await openDb();
  if (!db) return;
  await db.delete(STORE_NAME, trackId);
}

export async function offlineSize(): Promise<number> {
  const db = await openDb();
  if (!db) return 0;
  const tracks = await db.getAll(STORE_NAME);
  return tracks.reduce((sum, t) => sum + (t.audioBlob.size + (t.coverUrl ? 50000 : 0)), 0);
}

export async function getOfflineAudioUrl(trackId: string): Promise<string | null> {
  const track = await getOffline(trackId);
  if (!track) return null;
  return URL.createObjectURL(track.audioBlob);
}
