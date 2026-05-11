<script setup lang="ts">
import { ref, watch } from 'vue'
import type {
  AdminVariableVenue,
  AdminOtherVenue,
} from '@/features/agenda/components/useAdminVenues'

type VenueType = 'variable' | 'other'

const props = defineProps<{
  isOpen: boolean
  type: VenueType
  editingVenue: AdminVariableVenue | AdminOtherVenue | null
}>()

type SaveData =
  | (Omit<AdminVariableVenue, 'id'> & { id?: string })
  | (Omit<AdminOtherVenue, 'id'> & { id?: string })

const emit = defineEmits<{
  close: []
  save: [data: SaveData]
}>()

const name = ref('')
const neighborhood = ref('')
const instagram = ref('')
const days = ref('')

watch(
  () => props.isOpen,
  (open) => {
    if (open && props.editingVenue) {
      name.value = props.editingVenue.name
      neighborhood.value = props.editingVenue.neighborhood
      instagram.value = props.editingVenue.instagram ?? ''
      days.value = (props.editingVenue as AdminVariableVenue).days ?? ''
    } else if (open) {
      name.value = ''
      neighborhood.value = ''
      instagram.value = ''
      days.value = ''
    }
  },
)

function formatInstagram(value: string): string | null {
  if (!value) return null
  const clean = value.trim()
  if (!clean) return null
  if (clean.startsWith('http')) return clean
  return `https://www.instagram.com/${clean.replace('@', '')}`
}

function handleSave() {
  if (!name.value || !neighborhood.value) return
  const base = {
    id: props.editingVenue?.id,
    name: name.value,
    neighborhood: neighborhood.value,
    instagram: formatInstagram(instagram.value),
    sortOrder: props.editingVenue?.sortOrder ?? 99,
  }
  if (props.type === 'variable') {
    emit('save', { ...base, days: days.value })
  } else {
    emit('save', base)
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div class="absolute inset-0 bg-black/30 backdrop-blur-sm" @click="emit('close')"></div>
      <div class="relative bg-white rounded-2xl shadow-xl w-full max-w-md p-6 z-10">
        <div class="flex items-center justify-between mb-6">
          <h2 class="text-base font-bold text-green-900">
            {{ editingVenue ? 'Editar local' : 'Novo local' }}
          </h2>
          <button
            @click="emit('close')"
            class="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100"
          >
            ✕
          </button>
        </div>

        <div class="space-y-4">
          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">Nome *</label>
            <input
              v-model="name"
              type="text"
              placeholder="Ex: Arpoador"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm focus:outline-none focus:border-green-400 transition-all"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block">Bairro *</label>
            <input
              v-model="neighborhood"
              type="text"
              placeholder="Ex: Tambaú"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm focus:outline-none focus:border-green-400 transition-all"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-gray-500 mb-1 block"
              >Instagram (opcional)</label
            >
            <input
              v-model="instagram"
              type="text"
              placeholder="Ex: arpoadorjp"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm focus:outline-none focus:border-green-400 transition-all"
            />
            <p v-if="instagram" class="text-xs text-gray-400 mt-1">
              Será salvo como:
              <span class="text-green-600 font-medium">{{ formatInstagram(instagram) }}</span>
            </p>
          </div>

          <div v-if="type === 'variable'">
            <label class="text-xs font-semibold text-gray-500 mb-1 block">Dias (opcional)</label>
            <input
              v-model="days"
              type="text"
              placeholder="Ex: Quinta a domingo"
              class="w-full px-3 py-2 rounded-xl border border-gray-200 text-sm focus:outline-none focus:border-green-400 transition-all"
            />
          </div>
        </div>

        <div class="flex gap-3 mt-6">
          <button
            @click="emit('close')"
            class="flex-1 px-4 py-2 rounded-xl border border-gray-200 text-sm cursor-pointer font-semibold text-gray-600 hover:bg-gray-50 transition-all"
          >
            Cancelar
          </button>
          <button
            @click="handleSave"
            :disabled="!name || !neighborhood"
            class="flex-1 px-4 py-2 rounded-xl bg-green-600 text-white text-sm font-semibold cursor-pointer hover:bg-green-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ editingVenue ? 'Salvar alterações' : 'Adicionar' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
