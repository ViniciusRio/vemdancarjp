<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AdminEvent } from '@/features/agenda/composables/useAgendaAdmin'

const props = defineProps<{
  isOpen: boolean
  editingEvent: AdminEvent | null
  dayId: string
  dayLabel: string
}>()

const emit = defineEmits<{
  close: []
  save: [eventData: {
    id?: string
    dayId: string
    name: string
    venue: string
    neighborhood: string
    instagram: string | null
    frequency: string | null
    sortOrder: number
  }]
}>()

const name = ref('')
const venue = ref('')
const neighborhood = ref('')
const instagram = ref('')
const frequency = ref('')

// quando o modal abre, preenche os campos se for edição
watch(() => props.isOpen, (open) => {
  if (open && props.editingEvent) {
    name.value = props.editingEvent.name
    venue.value = props.editingEvent.venue
    neighborhood.value = props.editingEvent.neighborhood
    instagram.value = props.editingEvent.instagram ?? ''
    frequency.value = props.editingEvent.frequency ?? ''
  } else if (open) {
    name.value = ''
    venue.value = ''
    neighborhood.value = ''
    instagram.value = ''
    frequency.value = ''
  }
})

function handleSave() {
  if (!name.value || !venue.value || !neighborhood.value) return

  emit('save', {
    id: props.editingEvent?.id,
    dayId: props.dayId,
    name: name.value,
    venue: venue.value,
    neighborhood: neighborhood.value,
    instagram: instagram.value || null,
    frequency: frequency.value || null,
    sortOrder: props.editingEvent?.sortOrder ?? 99
  })
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 flex items-center justify-center px-4"
    >
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-black/30 backdrop-blur-sm"
        @click="emit('close')"
      ></div>

      <!-- Modal -->
      <div class="relative bg-white rounded-2xl shadow-xl w-full max-w-md p-6 z-10">

        <!-- Header -->
        <div class="flex items-center justify-between mb-6">
          <div>
            <h2 class="text-base font-bold text-green-900">
              {{ editingEvent ? 'Editar evento' : 'Novo evento' }}
            </h2>
            <p class="text-xs text-gray-400 mt-0.5">{{ dayLabel }}</p>
          </div>
          <button
            @click="emit('close')"
            class="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 transition-all"
          >
            ✕
          </button>
        </div>

        <!-- Form -->
        <div class="space-y-4">
          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">
              Nome do evento *
            </label>
            <input
              v-model="name"
              type="text"
              placeholder="Ex: Forró do Turista"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm text-gray-800 focus:outline-none focus:border-green-400 transition-all"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">
              Local *
            </label>
            <input
              v-model="venue"
              type="text"
              placeholder="Ex: Bessa Grill"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm text-gray-800 focus:outline-none focus:border-green-400 transition-all"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">
              Bairro *
            </label>
            <input
              v-model="neighborhood"
              type="text"
              placeholder="Ex: Bessa"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm text-gray-800 focus:outline-none focus:border-green-400 transition-all"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">
              Instagram (opcional)
            </label>
            <input
              v-model="instagram"
              type="text"
              placeholder="https://www.instagram.com/..."
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm text-gray-800 focus:outline-none focus:border-green-400 transition-all"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">
              Frequência (opcional)
            </label>
            <input
              v-model="frequency"
              type="text"
              placeholder="Ex: 1ª sexta do mês"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm text-gray-800 focus:outline-none focus:border-green-400 transition-all"
            />
          </div>
        </div>

        <!-- Actions -->
        <div class="flex gap-3 mt-6">
          <button
            @click="emit('close')"
            class="flex-1 px-4 py-2 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 hover:bg-gray-50 transition-all"
          >
            Cancelar
          </button>
          <button
            @click="handleSave"
            :disabled="!name || !venue || !neighborhood"
            class="flex-1 px-4 py-2 rounded-xl bg-green-600 text-white text-sm font-semibold hover:bg-green-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ editingEvent ? 'Salvar alterações' : 'Adicionar evento' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
