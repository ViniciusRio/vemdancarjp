import type { Agenda } from '@/features/agenda/types'

const API_URL = import.meta.env.VITE_API_URL ?? ''

export const agendaService = {
  async fetchAgenda(): Promise<Agenda> {
    const response = await fetch(`${API_URL}/api/agenda`)

    if (!response.ok) {
      throw new Error(`Failed to fetch agenda: ${response.status}`)
    }

    return response.json() as Promise<Agenda>
  },
}
