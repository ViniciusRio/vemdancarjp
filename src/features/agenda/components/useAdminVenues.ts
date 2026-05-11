import { ref } from 'vue'
import { supabase } from '@/services/supabase'

export interface AdminVariableVenue {
  id: string
  name: string
  neighborhood: string
  instagram: string | null
  days: string
  sortOrder: number
}

export interface AdminOtherVenue {
  id: string
  name: string
  neighborhood: string
  instagram: string | null
  sortOrder: number
}

export function useAdminVenues() {
  const variableVenues = ref<AdminVariableVenue[]>([])
  const otherVenues = ref<AdminOtherVenue[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchVariableVenues() {
    const { data, error: err } = await supabase
      .from('variable_venues')
      .select('*')
      .order('sort_order', { ascending: true })
    if (err) {
      error.value = err.message
      return
    }
    variableVenues.value = (data ?? []).map((row) => ({
      id: row.id,
      name: row.name,
      neighborhood: row.neighborhood,
      instagram: row.instagram,
      days: row.days,
      sortOrder: row.sort_order,
    }))
  }

  async function fetchOtherVenues() {
    const { data, error: err } = await supabase
      .from('other_venues')
      .select('*')
      .order('sort_order', { ascending: true })
    if (err) {
      error.value = err.message
      return
    }
    otherVenues.value = (data ?? []).map((row) => ({
      id: row.id,
      name: row.name,
      neighborhood: row.neighborhood,
      instagram: row.instagram,
      sortOrder: row.sort_order,
    }))
  }

  async function saveVariableVenue(data: Omit<AdminVariableVenue, 'id'> & { id?: string }) {
    const payload = {
      name: data.name,
      neighborhood: data.neighborhood,
      instagram: data.instagram || null,
      days: data.days,
      sort_order: data.sortOrder,
    }
    if (data.id) {
      const { error: err } = await supabase
        .from('variable_venues')
        .update(payload)
        .eq('id', data.id)
      if (err) {
        error.value = err.message
        return
      }
    } else {
      const { error: err } = await supabase.from('variable_venues').insert(payload)
      if (err) {
        error.value = err.message
        return
      }
    }
    await fetchVariableVenues()
  }

  async function deleteVariableVenue(id: string) {
    const { error: err } = await supabase.from('variable_venues').delete().eq('id', id)
    if (err) {
      error.value = err.message
      return
    }
    await fetchVariableVenues()
  }

  async function saveOtherVenue(data: Omit<AdminOtherVenue, 'id'> & { id?: string }) {
    const payload = {
      name: data.name,
      neighborhood: data.neighborhood,
      instagram: data.instagram || null,
      sort_order: data.sortOrder,
    }
    if (data.id) {
      const { error: err } = await supabase.from('other_venues').update(payload).eq('id', data.id)
      if (err) {
        error.value = err.message
        return
      }
    } else {
      const { error: err } = await supabase.from('other_venues').insert(payload)
      if (err) {
        error.value = err.message
        return
      }
    }
    await fetchOtherVenues()
  }

  async function deleteOtherVenue(id: string) {
    const { error: err } = await supabase.from('other_venues').delete().eq('id', id)
    if (err) {
      error.value = err.message
      return
    }
    await fetchOtherVenues()
  }

  return {
    variableVenues,
    otherVenues,
    isLoading,
    error,
    fetchVariableVenues,
    fetchOtherVenues,
    saveVariableVenue,
    deleteVariableVenue,
    saveOtherVenue,
    deleteOtherVenue,
  }
}
