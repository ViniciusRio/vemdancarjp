<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useAdminAgenda } from '@/features/agenda/composables/useAgendaAdmin'
import { useAdminVenues } from '@/features/agenda/components/useAdminVenues'
import { usePendingAdmins } from '@/composables/usePendingAdmins'
import { useRouter } from 'vue-router'
import EventModal from '@/features/agenda/components/EventModal.vue'
import VenueModal from '@/features/agenda/components/VenueModal.vue'
import ConfirmModal from '@/features/agenda/components/ConfirmModal.vue'
import type { AdminEvent } from '@/features/agenda/composables/useAgendaAdmin'
import type {
  AdminVariableVenue,
  AdminOtherVenue,
} from '@/features/agenda/components/useAdminVenues'
import ToastNotification from '@/components/ui/ToastNotification.vue'

type VenueSaveData =
  | (Omit<AdminVariableVenue, 'id'> & { id?: string })
  | (Omit<AdminOtherVenue, 'id'> & { id?: string })

const authStore = useAuthStore()
const { days, isLoading, error, fetchEvents, deleteEvent, saveEvent } = useAdminAgenda()

const toastMessage = ref('')
const toastType = ref<'success' | 'error'>('success')
const showToast = ref(false)

function toast(message: string, type: 'success' | 'error' = 'success') {
  toastMessage.value = message
  toastType.value = type
  showToast.value = true
}
const isModalOpen = ref(false)
const editingEvent = ref<AdminEvent | null>(null)
const activeDayId = ref('')
const activeDayLabel = ref('')
const router = useRouter()

const totalEvents = computed(() => days.value.reduce((sum, day) => sum + day.events.length, 0))

const daysWithEvents = computed(() => days.value.filter((day) => day.events.length > 0).length)

const { pendingAdmins, fetchPending, approveAdmin, rejectAdmin } = usePendingAdmins()

// Venues
const {
  variableVenues,
  otherVenues,
  fetchVariableVenues,
  fetchOtherVenues,
  saveVariableVenue,
  deleteVariableVenue,
  saveOtherVenue,
  deleteOtherVenue,
} = useAdminVenues()

const activeTab = ref<'agenda' | 'users' | 'variable' | 'others'>('agenda')

const isVenueModalOpen = ref(false)
const venueModalType = ref<'variable' | 'other'>('variable')
const editingVenue = ref<AdminVariableVenue | AdminOtherVenue | null>(null)

function openVenueModal(type: 'variable' | 'other', venue?: AdminVariableVenue | AdminOtherVenue) {
  venueModalType.value = type
  editingVenue.value = venue ?? null
  isVenueModalOpen.value = true
}

function closeVenueModal() {
  isVenueModalOpen.value = false
  editingVenue.value = null
}
async function handleSaveVenue(data: VenueSaveData) {
  const wasEditing = !!editingVenue.value // ← salva antes de fechar
  if (venueModalType.value === 'variable') {
    await saveVariableVenue(data as Omit<AdminVariableVenue, 'id'> & { id?: string })
  } else {
    await saveOtherVenue(data as Omit<AdminOtherVenue, 'id'> & { id?: string })
  }
  closeVenueModal()
  toast(wasEditing ? 'Local atualizado!' : 'Local adicionado!')
}

async function handleLogout() {
  console.log('handleLogout called')
  try {
    await authStore.logout()
    router.push({ name: 'admin-login' })
  } catch (err) {
    console.error('Erro ao fazer logout:', err)
  }
}

onMounted(() => {
  fetchEvents()
  fetchPending()
  fetchVariableVenues()
  fetchOtherVenues()
})

const isConfirmModalOpen = ref(false)
const deletingEventId = ref<string | null>(null)

function openDeleteModal(id: string) {
  console.log('Abrindo modal de confirmação para deletar evento com ID:', id)
  deletingEventId.value = id
  isConfirmModalOpen.value = true
}

async function handleConfirmDelete() {
  if (!deletingEventId.value) return
  await deleteEvent(deletingEventId.value)
  isConfirmModalOpen.value = false
  deletingEventId.value = null
  toast('Evento removido!', 'error')
}

function handleCancelDelete() {
  isConfirmModalOpen.value = false
  deletingEventId.value = null
}

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
  const wasEditing = !!editingEvent.value // ← salva antes de fechar
  await saveEvent(eventData)
  closeModal()
  toast(wasEditing ? 'Evento atualizado!' : 'Evento adicionado!')
}
</script>

<template>
  <div class="min-h-screen bg-green-50">
    <!-- Topbar -->
    <header class="bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between">
      <RouterLink to="/" class="flex items-center gap-3">
        <span class="font-bold text-green-900">Vem Dançar JP</span>
        <span class="text-xs font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700"
          >Admin</span
        >
      </RouterLink>
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

    <!-- Tabs -->
    <div class="bg-white border-b border-gray-100 px-6">
      <div class="flex gap-6">
        <button
          @click="activeTab = 'agenda'"
          :class="[
            'py-3 text-sm font-semibold border-b-2  cursor-pointer transition-all',
            activeTab === 'agenda'
              ? 'border-green-500 text-green-700'
              : 'border-transparent text-gray-400 hover:text-gray-600',
          ]"
        >
          Agenda
        </button>
        <button
          @click="activeTab = 'users'"
          :class="[
            'py-3 text-sm font-semibold border-b-2 cursor-pointer transition-all flex items-center gap-2',
            activeTab === 'users'
              ? 'border-purple-500 text-purple-700'
              : 'border-transparent text-gray-400 hover:text-gray-600',
          ]"
        >
          Usuários
          <span
            v-if="pendingAdmins.length > 0"
            class="text-xs px-1.5 py-0.5 rounded-full bg-purple-100 text-purple-700 font-bold"
          >
            {{ pendingAdmins.length }}
          </span>
        </button>
        <button
          @click="activeTab = 'variable'"
          :class="[
            'py-3 text-sm font-semibold cursor-pointer border-b-2 transition-all',
            activeTab === 'variable'
              ? 'border-amber-500 text-amber-700'
              : 'border-transparent text-gray-400 hover:text-gray-600',
          ]"
        >
          Lugares variáveis
        </button>
        <button
          @click="activeTab = 'others'"
          :class="[
            'py-3 text-sm font-semibold cursor-pointer border-b-2 transition-all',
            activeTab === 'others'
              ? 'border-purple-500 text-purple-700'
              : 'border-transparent text-gray-400 hover:text-gray-600',
          ]"
        >
          Outros lugares
        </button>
      </div>
    </div>

    <main class="max-w-3xl mx-auto px-6 py-8">
      <!-- Aba Agenda -->
      <div v-if="activeTab === 'agenda'">
        <div class="mb-6">
          <h1 class="text-xl font-bold text-green-900">Agenda semanal</h1>
          <p class="text-sm text-gray-400 mt-0.5">Gerencie os eventos por dia da semana</p>
        </div>

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

        <div v-if="isLoading" class="text-center py-12 text-gray-400 italic text-sm">
          carregando eventos...
        </div>
        <div v-else-if="error" class="text-center py-8 text-red-500 text-sm">Erro: {{ error }}</div>
        <div v-else class="space-y-4">
          <div
            v-for="day in days"
            :key="day.id"
            class="bg-white rounded-xl border border-gray-100 overflow-hidden"
          >
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
                class="text-xs cursor-pointer font-semibold text-purple-600 hover:text-purple-800 transition-colors"
              >
                + Adicionar
              </button>
            </div>
            <div v-if="day.events.length === 0" class="px-5 py-4 text-sm italic text-gray-300">
              Nenhum evento cadastrado
            </div>
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
                  class="w-7 h-7 cursor-pointer rounded-lg flex items-center justify-center text-gray-400 hover:bg-green-50 hover:text-green-600 transition-all"
                >
                  ✏️
                </button>
                <button
                  @click="openDeleteModal(event.id)"
                  class="w-7 h-7 rounded-lg cursor-pointer flex items-center justify-center text-gray-400 hover:bg-red-50 hover:text-red-500 transition-all"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Aba Usuários -->
      <div v-else-if="activeTab === 'users'">
        <div class="mb-6">
          <h1 class="text-xl font-bold text-green-900">Usuários pendentes</h1>
          <p class="text-sm text-gray-400 mt-0.5">Gerencie as solicitações de acesso</p>
        </div>

        <div
          v-if="pendingAdmins.length === 0"
          class="bg-white rounded-xl border border-gray-100 px-5 py-8 text-center"
        >
          <p class="text-sm italic text-gray-300">Nenhuma solicitação pendente</p>
        </div>

        <div v-else class="bg-white rounded-xl border border-gray-100 overflow-hidden">
          <div
            v-for="(pending, index) in pendingAdmins"
            :key="pending.id"
            class="px-5 py-4 flex items-center gap-3"
            :class="index < pendingAdmins.length - 1 ? 'border-b border-gray-50' : ''"
          >
            <div
              class="w-9 h-9 rounded-full bg-purple-50 flex items-center justify-center flex-shrink-0 text-sm font-bold text-purple-600"
            >
              {{ pending.name?.charAt(0) ?? '?' }}
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-gray-800">{{ pending.name }}</p>
              <p class="text-xs text-gray-400">{{ pending.email }}</p>
            </div>
            <div class="flex gap-2 flex-shrink-0">
              <button
                @click="approveAdmin(pending.id, pending.email)"
                class="text-xs font-semibold px-3 py-1.5 rounded-lg bg-green-50 border border-green-200 text-green-700 hover:bg-green-100 transition-all"
              >
                Aprovar
              </button>
              <button
                @click="rejectAdmin(pending.id)"
                class="text-xs font-semibold px-3 py-1.5 rounded-lg bg-red-50 border border-red-200 text-red-600 hover:bg-red-100 transition-all"
              >
                Rejeitar
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Aba Lugares Variáveis -->
      <div v-else-if="activeTab === 'variable'">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-xl font-bold text-green-900">Lugares variáveis</h1>
            <p class="text-sm text-gray-400 mt-0.5">Locais com programação variável</p>
          </div>
          <button
            @click="openVenueModal('variable')"
            class="text-xs font-semibold px-3 py-1.5 cursor-pointer rounded-lg bg-amber-50 border border-amber-200 text-amber-700 hover:bg-amber-100 transition-all"
          >
            + Adicionar
          </button>
        </div>

        <div
          v-if="variableVenues.length === 0"
          class="bg-white rounded-xl border border-gray-100 px-5 py-8 text-center"
        >
          <p class="text-sm italic text-gray-300">Nenhum lugar cadastrado</p>
        </div>

        <div v-else class="bg-white rounded-xl border border-gray-100 overflow-hidden">
          <div
            v-for="(venue, index) in variableVenues"
            :key="venue.id"
            class="px-5 py-4 flex items-center gap-3"
            :class="index < variableVenues.length - 1 ? 'border-b border-gray-50' : ''"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-gray-800">{{ venue.name }}</p>
              <p class="text-xs text-gray-400">{{ venue.neighborhood }}</p>
              <p class="text-xs text-amber-600 font-medium mt-0.5">{{ venue.days }}</p>
            </div>
            <div class="flex gap-2 flex-shrink-0">
              <button
                @click="openVenueModal('variable', venue)"
                class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:bg-green-50 hover:text-green-600 transition-all"
              >
                ✏️
              </button>
              <button
                @click="deleteVariableVenue(venue.id)"
                class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:bg-red-50 hover:text-red-500 transition-all"
              >
                ✕
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Aba Outros Lugares -->
      <div v-else-if="activeTab === 'others'">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-xl font-bold text-green-900">Outros lugares</h1>
            <p class="text-sm text-gray-400 mt-0.5">Locais que tocam forró esporadicamente</p>
          </div>
          <button
            @click="openVenueModal('other')"
            class="text-xs font-semibold px-3 py-1.5 cursor-pointer rounded-lg bg-purple-50 border border-purple-200 text-purple-700 hover:bg-purple-100 transition-all"
          >
            + Adicionar
          </button>
        </div>

        <div
          v-if="otherVenues.length === 0"
          class="bg-white rounded-xl border border-gray-100 px-5 py-8 text-center"
        >
          <p class="text-sm italic text-gray-300">Nenhum lugar cadastrado</p>
        </div>

        <div v-else class="bg-white rounded-xl border border-gray-100 overflow-hidden">
          <div
            v-for="(venue, index) in otherVenues"
            :key="venue.id"
            class="px-5 py-4 flex items-center gap-3"
            :class="index < otherVenues.length - 1 ? 'border-b border-gray-50' : ''"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-gray-800">{{ venue.name }}</p>
              <p class="text-xs text-gray-400">{{ venue.neighborhood }}</p>
            </div>
            <div class="flex gap-2 flex-shrink-0">
              <button
                @click="openVenueModal('other', venue)"
                class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:bg-green-50 hover:text-green-600 transition-all"
              >
                ✏️
              </button>
              <button
                @click="deleteOtherVenue(venue.id)"
                class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:bg-red-50 hover:text-red-500 transition-all"
              >
                ✕
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>
    <VenueModal
      :is-open="isVenueModalOpen"
      :type="venueModalType"
      :editing-venue="editingVenue"
      @close="closeVenueModal"
      @save="handleSaveVenue"
    />
    <EventModal
      :is-open="isModalOpen"
      :editing-event="editingEvent"
      :day-id="activeDayId"
      :day-label="activeDayLabel"
      @close="closeModal"
      @save="handleSave"
    />
    <ConfirmModal
      :is-open="isConfirmModalOpen"
      title="Remover evento"
      message="Tem certeza que deseja remover este evento? Esta ação não pode ser desfeita."
      @confirm="handleConfirmDelete"
      @cancel="handleCancelDelete"
    />

    <ToastNotification
      v-if="showToast"
      :message="toastMessage"
      :type="toastType"
      @close="showToast = false"
    />
  </div>
</template>
