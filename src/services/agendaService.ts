import { supabase } from '@/services/supabase'
import type { Agenda, Day, Event, VariableVenue, OtherVenue } from '@/features/agenda/types'

const DAY_LABELS: Record<string, string> = {
  monday: 'Segunda-feira',
  tuesday: 'Terça-feira',
  wednesday: 'Quarta-feira',
  thursday: 'Quinta-feira',
  friday: 'Sexta-feira',
  saturday: 'Sábado',
  sunday: 'Domingo',
}

const DAY_ORDER = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

export const agendaService = {
  async fetchAgenda(): Promise<Agenda> {
    const [eventsResult, variableVenuesResult, otherVenuesResult] = await Promise.all([
      supabase.from('events').select('*').order('sort_order', { ascending: true }),
      supabase.from('variable_venues').select('*').order('sort_order', { ascending: true }),
      supabase.from('other_venues').select('*').order('sort_order', { ascending: true }),
    ])

    if (eventsResult.error) throw new Error(eventsResult.error.message)
    if (variableVenuesResult.error) throw new Error(variableVenuesResult.error.message)
    if (otherVenuesResult.error) throw new Error(otherVenuesResult.error.message)

    const days: Day[] = DAY_ORDER.map((dayId) => ({
      id: dayId,
      label: DAY_LABELS[dayId] ?? dayId,
      events: (eventsResult.data ?? [])
        .filter((row) => row.day_id === dayId)
        .map((row): Event => ({
          name: row.name,
          venue: row.venue,
          neighborhood: row.neighborhood,
          instagram: row.instagram,
          frequency: row.frequency,
        })),
    }))

    const variableVenues: VariableVenue[] = (variableVenuesResult.data ?? []).map((row) => ({
      name: row.name,
      neighborhood: row.neighborhood,
      instagram: row.instagram,
      days: row.days,
    }))

    const otherVenues: OtherVenue[] = (otherVenuesResult.data ?? []).map((row) => ({
      name: row.name,
      neighborhood: row.neighborhood,
      instagram: row.instagram,
    }))

    return {
      city: 'João Pessoa, PB',
      title: 'Vem Dançar JP',
      subtitle: 'Agenda fixa semanal de Forró Pé de Serra',
      lastUpdated: new Date().toISOString().split('T')[0] ?? '',
      days,
      variableVenues,
      otherVenues,
    }
  },
}
