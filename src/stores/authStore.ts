import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { supabase } from '@/services/supabase'
import type { User } from '@supabase/supabase-js'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isAdmin = ref(false)
  const isLoading = ref(true)
  let authListenerUnsubscribe: (() => void) | null = null

  const isLoggedIn = computed(() => !!user.value)

async function checkAdmin(userId: string): Promise<boolean> {
  const { data } = await supabase
    .from('admins')
    .select('id')
    .eq('id', userId)  // ← faz parte da query
    .single()

  return !!data
}

   async function init() {
    if (user.value) return

    isLoading.value = true

    const { data: { session } } = await supabase.auth.getSession()
    user.value = session?.user ?? null

    if (user.value) {
      isAdmin.value = await checkAdmin(user.value.id)
    }

    if (!authListenerUnsubscribe) {
      const { data: { subscription } } = supabase.auth.onAuthStateChange(async (_, session) => {
        user.value = session?.user ?? null
        isAdmin.value = user.value
          ? await checkAdmin(user.value.id)
          : false
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
    supabase.auth.signOut()
    user.value = null
    isAdmin.value = false
  }


  return {
    user,
    isAdmin,
    isLoading,
    isLoggedIn,
    init,
    loginWithGoogle,
    logout
  }
})
