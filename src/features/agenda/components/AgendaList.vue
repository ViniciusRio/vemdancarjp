<script setup lang="ts">
import { useAgendaStore } from '@/stores/agendaStore'
import EventCard from '@/features/agenda/components/EventCard.vue' // ← faltou essa linha

const store = useAgendaStore()
</script>

<template>
  <main class="px-6 pb-8 mx-auto">
    <div v-for="day in store.filteredDays" :key="day.id" class="mt-7">
      <div class="flex items-center gap-2 mb-3">
        <div class="w-2 h-2 rounded-full bg-green-500 flex-shrink-0"></div>
        <span class="text-lg italic text-green-900"> {{ day.label }}</span>
        <div class="flex-1 h-px bg-green-100"></div>
        <span class="text-xs font-bold text-gray-300">{{
          day.events.length > 0
            ? `${day.events.length} ${day.events.length === 1 ? 'lugar' : 'lugares'}`
            : ''
        }}</span>
      </div>
      <p v-if="day.events.length === 0" class="text-sm italic text-gray-300 px-1">
        Nehuma agenda fixa disponível
      </p>

      <EventCard v-for="event in day.events" :key="event.name" :event="event" />
    </div>
  </main>
</template>

<style></style>
