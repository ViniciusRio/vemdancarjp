import { ref } from 'vue'
import { supabase } from '@/services/supabase'
import type { Event } from '@/features/agenda/types'

const DAY_ORDER = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

const DAY_LABELS: Record<string, string> = {
  monday: 'Segunda-feira',
  tuesday: 'Terça-feira',
  wednesday: 'Quarta-feira',
  thursday: 'Quinta-feira',
  friday: 'Sexta-feira',
  saturday: 'Sábado',
  sunday: 'Domingo',
}

export interface AdminEvent extends Event {
  id: string
  dayId: string
  sortOrder: number
}

export interface AdminDay {
  id: string
  label: string
  events: AdminEvent[]
}

export function useAdminAgenda() {
  const days = ref<AdminDay[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchEvents() {
    isLoading.value = true
    error.value = null

    const { data, error: err } = await supabase
      .from('events')
      .select('*')
      .order('sort_order', { ascending: true })

    if (err) {
      error.value = err.message
      isLoading.value = false
      return
    }

    days.value = DAY_ORDER.map((dayId) => ({
      id: dayId,
      label: DAY_LABELS[dayId] ?? dayId,
      events: (data ?? [])
        .filter((row) => row.day_id === dayId)
        .map((row) => ({
          id: row.id,
          dayId: row.day_id,
          name: row.name,
          venue: row.venue,
          neighborhood: row.neighborhood,
          instagram: row.instagram,
          frequency: row.frequency,
          sortOrder: row.sort_order,
        })),
    }))

    isLoading.value = false
  }

  async function saveEvent(eventData: {
    id?: string
    dayId: string
    name: string
    venue: string
    neighborhood: string
    instagram: string | null
    frequency: string | null
    sortOrder: number
  }) {
    const payload = {
      day_id: eventData.dayId,
      name: eventData.name,
      venue: eventData.venue,
      neighborhood: eventData.neighborhood,
      instagram: eventData.instagram || null,
      frequency: eventData.frequency || null,
      sort_order: eventData.sortOrder,
    }

    if (eventData.id) {
      const { error: err } = await supabase.from('events').update(payload).eq('id', eventData.id)
      if (err) {
        error.value = err.message
        return
      }
    } else {
      const { error: err } = await supabase.from('events').insert(payload)
      if (err) {
        error.value = err.message
        return
      }
    }

    await fetchEvents()
  }

  async function deleteEvent(id: string) {
    const { error: err } = await supabase.from('events').delete().eq('id', id)

    if (err) {
      error.value = err.message
      return
    }

    await fetchEvents()
  }

  return {
    days,
    isLoading,
    error,
    fetchEvents,
    deleteEvent,
    saveEvent,
  }
}
