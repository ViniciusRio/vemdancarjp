import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { supabase } from '@/services/supabase'
import type { User } from '@supabase/supabase-js'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isAdmin = ref(false)
  const isLoading = ref(true)
  const isPending = ref(false)
  let authListenerUnsubscribe: (() => void) | null = null

  const isLoggedIn = computed(() => !!user.value)

  async function checkAdmin(userId: string): Promise<boolean> {
    const { data } = await supabase
      .from('admins')
      .select('id')
      .eq('id', userId)
      .maybeSingle()
    return !!data
  }

  async function checkPending(userId: string): Promise<boolean> {
    const { data } = await supabase
      .from('pending_admins')
      .select('id')
      .eq('id', userId)
      .maybeSingle()
    return !!data
  }

  async function requestAdminAccess(userId: string, email: string, name: string) {
    await supabase.from('pending_admins').insert({ id: userId, email, name })
    isPending.value = true
  }

  async function init() {
    if (user.value) return

    isLoading.value = true

    const { data: { session } } = await supabase.auth.getSession()
    user.value = session?.user ?? null

    console.log('init - user:', user.value?.email)
    console.log('init - session:', !!session)

    if (user.value) {
      isAdmin.value = await checkAdmin(user.value.id)
      console.log('init - isAdmin:', isAdmin.value)

      if (!isAdmin.value) {
        isPending.value = await checkPending(user.value.id)
        console.log('init - isPending:', isPending.value)

        if (!isPending.value) {
          console.log('init - creating pending request...')
          await requestAdminAccess(
            user.value.id,
            user.value.email ?? '',
            user.value.user_metadata?.full_name ?? ''
          )
        }
      }
    }

    if (!authListenerUnsubscribe) {
      const { data: { subscription } } = supabase.auth.onAuthStateChange(async (_, session) => {
        user.value = session?.user ?? null
        if (user.value) {
          isAdmin.value = await checkAdmin(user.value.id)
          if (!isAdmin.value) {
            isPending.value = await checkPending(user.value.id)
          }
        } else {
          isAdmin.value = false
          isPending.value = false
        }
      })
      authListenerUnsubscribe = () => subscription.unsubscribe()
    }

    isLoading.value = false
  }

  async function loginWithGoogle() {
    await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: `${window.location.origin}/admin`
      }
    })
  }

  async function logout() {
    await supabase.auth.signOut()
    user.value = null
    isAdmin.value = false
    isPending.value = false
  }

  return {
    user,
    isAdmin,
    isLoading,
    isLoggedIn,
    isPending,
    init,
    loginWithGoogle,
    logout
  }
})
