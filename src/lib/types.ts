export interface Track {
  id: string;
  title: string;
  artist: string;
  coverUrl?: string;
  audioUrl: string;
  duration?: number;
}

export interface Playlist {
  id: string;
  title: string;
  description?: string;
  coverUrl?: string;
  trackCount: number;
}

export interface Collection {
  id: string;
  title: string;
  description?: string;
  coverUrl?: string;
  tracks: Track[];
}

export interface User {
  id: string;
  name: string;
  avatarUrl?: string;
  isPremium?: boolean;
}
