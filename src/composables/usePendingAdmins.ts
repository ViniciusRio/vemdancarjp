import { ref } from 'vue'
import { supabase } from '@/services/supabase'

export interface PendingAdmin {
  id: string
  email: string
  name: string
  requestedAt: string
}

export function usePendingAdmins() {
  const pendingAdmins = ref<PendingAdmin[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchPending() {
    isLoading.value = true
    error.value = null

    const { data, error: err } = await supabase
      .from('pending_admins')
      .select('*')
      .order('requested_at', { ascending: true })

    if (err) {
      error.value = err.message
      isLoading.value = false
      return
    }

    pendingAdmins.value = (data ?? []).map(row => ({
      id: row.id,
      email: row.email,
      name: row.name,
      requestedAt: row.requested_at
    }))

    isLoading.value = false
  }

  async function approveAdmin(id: string, email: string) {
    const { error: insertErr } = await supabase
      .from('admins')
      .insert({ id, email })

    if (insertErr) {
      error.value = insertErr.message
      return
    }

    const { error: deleteErr } = await supabase
      .from('pending_admins')
      .delete()
      .eq('id', id)

    if (deleteErr) {
      error.value = deleteErr.message
      return
    }

    await fetchPending()
  }

  async function rejectAdmin(id: string) {
    const { error: err } = await supabase
      .from('pending_admins')
      .delete()
      .eq('id', id)

    if (err) {
      error.value = err.message
      return
    }

    await fetchPending()
  }

  return {
    pendingAdmins,
    isLoading,
    error,
    fetchPending,
    approveAdmin,
    rejectAdmin
  }
}
