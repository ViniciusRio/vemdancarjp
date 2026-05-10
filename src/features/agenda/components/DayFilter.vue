<script setup lang="ts">
import { useAgendaStore } from '@/stores/agendaStore'
import type { Day, DayFilter } from '@/features/agenda/types'

const store = useAgendaStore()

const props = defineProps<{
  days: Day[]
}>()

function handleFilter(filter: DayFilter) {
  store.setFilter(filter)
}

function isActive(filter: DayFilter) {
  return store.activeFilter === filter
}
</script>

<template>
  <div class="flex gap-2 px-6 overflow-x-auto scrollbar-hide">
    <button
      :class="[
        'flex-shrink-0 px-4 py-1.5 rounded-full border-2 text-xs font-bold whitespace-nowrap transition-all',
        isActive('all')
          ? 'bg-green-600 border-green-600 text-white'
          : 'bg-white border-gray-200 text-gray-500 hover:border-green-500 hover:text-green-600',
      ]"
      @click="handleFilter('all')"
    >
      Todos os dias
    </button>

    <button
      :class="[
        'flex-shrink-0 px-4 py-1.5 rounded-full border-2 text-xs font-bold whitespace-nowrap transition-all',
        isActive('today')
          ? 'bg-purple-600 border-purple-600 text-white'
          : 'bg-white border-purple-300 text-purple-600 hover:border-purple-500',
      ]"
      @click="handleFilter('today')"
    >
      Hoje
    </button>

    <button
      v-for="day in props.days"
      :key="day.id"
      :class="[
        'flex-shrink-0 px-4 py-1.5 rounded-full border-2 text-xs font-bold whitespace-nowrap transition-all',
        isActive(day.id)
          ? 'bg-green-600 border-green-600 text-white'
          : 'bg-white border-gray-200 text-gray-500 hover:border-green-500 hover:text-green-600',
      ]"
      @click="handleFilter(day.id)"
    >
      {{ day.label.split('-')[0] }}
    </button>
  </div>
</template>
