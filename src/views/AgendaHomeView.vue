<script setup lang="ts">
import { useAgenda } from '@/composables/useAgenda'
import DayFilter from '@/features/agenda/components/DayFilter.vue'
import AgendaList from '@/features/agenda/components/AgendaList.vue'
import VariableVenues from '@/features/agenda/components/VariableVenues.vue'
import OtherVenues from '@/features/agenda/components/OtherVenues.vue'
import AgendaFooter from '@/features/agenda/components/AgendaFooter.vue'

const { agenda, isLoading, error } = useAgenda()
</script>

<template>
  <div class="min-h-screen bg-green-50">
    <header class="bg-white border-b-2 border-green-200 px-6 py-10">
      <p class="text-xs font-bold tracking-widest uppercase text-purple-600 mb-1">
        João Pessoa · PB
      </p>
      <h1 class="text-4xl font-bold text-green-900 leading-tight mb-1">
        Vem Dançar <em class="italic text-green-500">JP</em>
      </h1>
      <p class="text-sm text-gray-500">Agenda fixa semanal de Forró Pé de Serra</p>
    </header>

    <div
      v-if="isLoading"
      class="flex items-center justify-center py-12 text-gray-400 italic text-sm"
    >
      carregando agenda...
    </div>

    <div v-else-if="error" class="flex items-center justify-center py-8 text-red-500 text-sm">
      Erro ao carregar: {{ error }}
    </div>

    <template v-else-if="agenda">
      <div class="sticky top-0 z-10 bg-white border-b border-green-200 py-2">
        <DayFilter :days="agenda.days" />
      </div>
      <AgendaList />
      <VariableVenues :venues="agenda.variableVenues" />
      <OtherVenues :venues="agenda.otherVenues" />
      <AgendaFooter :last-updated="agenda.lastUpdated" />
    </template>
  </div>
</template>
