export interface ThemeBrief {
  id: number
  name?: string
}

export function themeName(themes: ThemeBrief[], themeId?: number | null): string {
  if (themeId == null) return ''
  return themes.find(t => t.id === themeId)?.name ?? String(themeId)
}
