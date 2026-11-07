const API_URL = 'https://zhilibyli.online';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_URL}${path}`;
  
  try {
    const res = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });

    if (!res.ok) {
      throw new ApiError(res.status, `API error: ${res.status}`);
    }

    return await res.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(0, 'Network error');
  }
}

export async function apiBlob(path: string): Promise<Blob> {
  const url = `${API_URL}${path}`;
  const res = await fetch(url);
  
  if (!res.ok) {
    throw new ApiError(res.status, `API error: ${res.status}`);
  }
  
  return await res.blob();
}
