<script setup lang="ts">
import { onMounted } from 'vue'

const props = defineProps<{
  message: string
  type?: 'success' | 'warning' | 'error'
  duration?: number
}>()

const emit = defineEmits<{
  close: []
}>()

onMounted(() => {
  setTimeout(() => {
    emit('close')
  }, props.duration ?? 4000)
})

const colors = {
  success: 'bg-green-50 border-green-200 text-green-800',
  warning: 'bg-amber-50 border-amber-200 text-amber-800',
  error: 'bg-red-50 border-red-200 text-red-800',
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 animate-fade-in">
      <div
        :class="[
          'flex items-center gap-3 px-4 py-3 rounded-xl border shadow-lg text-sm font-semibold',
          colors[type ?? 'success'],
        ]"
      >
        <span v-if="type === 'warning'">⏳</span>
        <span v-else-if="type === 'error'">❌</span>
        <span v-else>✅</span>
        {{ message }}
        <button @click="emit('close')" class="ml-2 opacity-50 hover:opacity-100">✕</button>
      </div>
    </div>
  </Teleport>
</template>
