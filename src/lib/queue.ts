export interface Track {
  id: string
  title: string
  artist: string
  cover: string
  duration: number
  audioUrl?: string
  isPlaying?: boolean
}

export const DEMO_QUEUE: Track[] = [
  {
    id: '1',
    title: 'Как Стекла Плавится Любовь...',
    artist: 'Koss, ПЛУТТО',
    cover: 'https://images.unsplash.com/photo-1493225293514-58c7c77c4d83?w=100&h=100&fit=crop',
    duration: 214,
    isPlaying: true,
  },
  {
    id: '2',
    title: 'КИС Ю',
    artist: 'NEXTIME, Даник',
    cover: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&h=100&fit=crop',
    duration: 198,
  },
  {
    id: '3',
    title: 'Ты не бойся ночи',
    artist: 'Дима Билан, Тося Чайкина, ENZRO',
    cover: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=100&h=100&fit=crop',
    duration: 235,
  },
  {
    id: '4',
    title: 'Мне бы дую свою успокой...',
    artist: 'Брянская',
    cover: 'https://images.unsplash.com/photo-1511379938547-c1f694198686?w=100&h=100&fit=crop',
    duration: 187,
  },
  {
    id: '5',
    title: 'Ты горишь как огонь 2',
    artist: 'SLAVA MARLOW, Gazan, Кобяков, ...',
    cover: 'https://images.unsplash.com/photo-1514525984304-77e23562e081?w=100&h=100&fit=crop',
    duration: 201,
  },
  {
    id: '6',
    title: 'Ртуть',
    artist: 'BEARWOLF',
    cover: 'https://images.unsplash.com/photo-1469334031218-e382a71b716b?w=100&h=100&fit=crop',
    duration: 176,
  },
  {
    id: '7',
    title: 'Killer, Baby',
    artist: 'Alfie Castley',
    cover: 'https://images.unsplash.com/photo-1493225293514-58c7c77c4d83?w=100&h=100&fit=crop',
    duration: 192,
  },
  {
    id: '8',
    title: 'Отпускаю',
    artist: 'BRVZ',
    cover: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=100&h=100&fit=crop',
    duration: 223,
  },
]

export function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}
