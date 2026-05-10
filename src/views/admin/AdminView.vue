<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useAdminAgenda } from '@/features/agenda/composables/useAgendaAdmin'
import EventModal from '@/features/agenda/components/EventModal.vue'
import type { AdminEvent } from '@/features/agenda/composables/useAgendaAdmin'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const { days, isLoading, error, fetchEvents, deleteEvent, saveEvent } = useAdminAgenda()

const isModalOpen = ref(false)
const editingEvent = ref<AdminEvent | null>(null)
const activeDayId = ref('')
const activeDayLabel = ref('')
const router = useRouter()

const totalEvents = computed(() => days.value.reduce((sum, day) => sum + day.events.length, 0))

const daysWithEvents = computed(() => days.value.filter((day) => day.events.length > 0).length)

async function handleLogout() {
  try {
    await authStore.logout()
    router.push({ name: 'admin-login' })
  } catch (err) {
    console.error('Erro ao fazer logout:', err)
  }
}

onMounted(() => {
  fetchEvents()
})

function openAddModal(dayId: string, dayLabel: string) {
  editingEvent.value = null
  activeDayId.value = dayId
  activeDayLabel.value = dayLabel
  isModalOpen.value = true
}

function openEditModal(event: AdminEvent, dayLabel: string) {
  editingEvent.value = event
  activeDayId.value = event.dayId
  activeDayLabel.value = dayLabel
  isModalOpen.value = true
}

function closeModal() {
  isModalOpen.value = false
  editingEvent.value = null
}

async function handleSave(eventData: Parameters<typeof saveEvent>[0]) {
  await saveEvent(eventData)
  closeModal()
}

async function handleDelete(id: string) {
  if (!confirm('Tem certeza que deseja remover este evento?')) return
  await deleteEvent(id)
}
</script>

<template>
  <div class="min-h-screen bg-green-50">
    <!-- Topbar -->
    <header class="bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <span class="font-bold text-green-900">Vem Dançar JP</span>
        <span class="text-xs font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700"
          >Admin</span
        >
      </div>
      <div class="flex items-center gap-3">
        <span class="text-sm text-gray-500">{{ authStore.user?.email }}</span>
        <button
          @click="handleLogout"
          class="cursor-pointer text-xs font-semibold px-3 py-1.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 transition-all"
        >
          Sair
        </button>
      </div>
    </header>

    <main class="max-w-3xl mx-auto px-6 py-8">
      <!-- Header -->
      <div class="mb-6">
        <h1 class="text-xl font-bold text-green-900">Agenda semanal</h1>
        <p class="text-sm text-gray-400 mt-0.5">Gerencie os eventos por dia da semana</p>
      </div>

      <!-- Stats -->
      <div class="grid grid-cols-2 gap-4 mb-8">
        <div class="bg-white rounded-xl border border-gray-100 px-5 py-4">
          <p class="text-xs text-gray-400 mb-1">Total de eventos</p>
          <p class="text-2xl font-bold text-green-900">{{ totalEvents }}</p>
        </div>
        <div class="bg-white rounded-xl border border-gray-100 px-5 py-4">
          <p class="text-xs text-gray-400 mb-1">Dias com agenda</p>
          <p class="text-2xl font-bold text-green-900">{{ daysWithEvents }}</p>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="isLoading" class="text-center py-12 text-gray-400 italic text-sm">
        carregando eventos...
      </div>

      <!-- Error -->
      <div v-else-if="error" class="text-center py-8 text-red-500 text-sm">Erro: {{ error }}</div>

      <!-- Days -->
      <div v-else class="space-y-4">
        <div
          v-for="day in days"
          :key="day.id"
          class="bg-white rounded-xl border border-gray-100 overflow-hidden"
        >
          <!-- Day header -->
          <div class="px-5 py-3 border-b border-gray-50 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-sm font-semibold text-green-900">{{ day.label }}</span>
              <span
                class="text-xs px-2 py-0.5 rounded-full bg-green-50 text-green-600 font-semibold"
              >
                {{ day.events.length }} {{ day.events.length === 1 ? 'evento' : 'eventos' }}
              </span>
            </div>
            <button
              @click="openAddModal(day.id, day.label)"
              class="text-xs font-semibold text-purple-600 hover:text-purple-800 transition-colors"
            >
              + Adicionar
            </button>
          </div>

          <!-- Empty day -->
          <div v-if="day.events.length === 0" class="px-5 py-4 text-sm italic text-gray-300">
            Nenhum evento cadastrado
          </div>

          <!-- Events -->
          <div
            v-for="event in day.events"
            :key="event.id"
            class="px-5 py-3 flex items-center gap-3 border-b border-gray-50 last:border-none"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-gray-800">{{ event.name }}</p>
              <p class="text-xs text-gray-400">{{ event.venue }} · {{ event.neighborhood }}</p>
            </div>
            <div class="flex items-center gap-2 flex-shrink-0">
              <span
                v-if="event.frequency"
                class="text-xs px-2 py-0.5 rounded-full bg-amber-50 border border-amber-200 text-amber-700 font-semibold"
              >
                {{ event.frequency }}
              </span>
              <button
                @click="openEditModal(event, day.label)"
                class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:bg-green-50 hover:text-green-600 transition-all"
              >
                ✏️
              </button>
              <button
                @click="handleDelete(event.id)"
                class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:bg-red-50 hover:text-red-500 transition-all"
              >
                ✕
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Modal -->
    <EventModal
      :is-open="isModalOpen"
      :editing-event="editingEvent"
      :day-id="activeDayId"
      :day-label="activeDayLabel"
      @close="closeModal"
      @save="handleSave"
    />
  </div>
</template>
