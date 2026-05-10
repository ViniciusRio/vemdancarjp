<script setup lang="ts">
defineProps<{
  isOpen: boolean
  title: string
  message: string
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div class="absolute inset-0 bg-black/30 backdrop-blur-sm" @click="emit('cancel')"></div>

      <div class="relative bg-white rounded-2xl shadow-xl w-full max-w-sm p-6 z-10 text-center">
        <div class="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-4">
          <svg class="w-6 h-6 text-red-500" fill="currentColor" viewBox="0 0 20 20">
            <path
              fill-rule="evenodd"
              d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
              clip-rule="evenodd"
            />
          </svg>
        </div>

        <h3 class="text-base font-bold text-gray-900 mb-1">{{ title }}</h3>
        <p class="text-sm text-gray-400 mb-6">{{ message }}</p>

        <div class="flex gap-3">
          <button
            @click="emit('cancel')"
            class="flex-1 px-4 py-2 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 hover:bg-gray-50 transition-all"
          >
            Cancelar
          </button>
          <button
            @click="emit('confirm')"
            class="flex-1 px-4 py-2 rounded-xl bg-red-500 text-white text-sm font-semibold hover:bg-red-600 transition-all"
          >
            Sim, remover
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
