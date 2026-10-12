export function parseProductImages(raw?: string | null): string[] {
  if (!raw) return []
  const value = raw.trim()
  if (!value) return []
  if (value.startsWith('[')) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed)
        ? parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
        : []
    } catch {
      return []
    }
  }
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

export function serializeProductImages(urls: string[]): string {
  return JSON.stringify(urls.map((url) => url.trim()).filter(Boolean))
}

export function firstProductImage(raw?: string | null): string {
  return parseProductImages(raw)[0] ?? ''
}
