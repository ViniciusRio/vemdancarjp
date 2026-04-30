<script setup lang="ts">
import { onMounted } from 'vue'
import { useAgendaStore } from '@/stores/agendaStore'
import DayFilter from '@/features/agenda/components/DayFilter.vue'

const store = useAgendaStore()

onMounted(() => {
  store.loadAgenda()
})
</script>

<template>
  <div class="app">
    <header class="hero">
      <p class="hero-tag">João Pessoa · PB</p>
      <h1 class="hero-title">Vem Dançar <em>JP</em></h1>
      <p class="hero-sub">Agenda fixa semanal de Pé de Serra</p>
    </header>

    <div v-if="store.isLoading">Carregando...</div>
    <div v-else-if="store.error">Erro: {{ store.error }}</div>
    <div v-else>
      <DayFilter v-if="store.agenda" :days="store.agenda.days" />
      <pre>{{ store.filteredDays }}</pre>
    </div>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
body {
  font-family: 'Nunito', sans-serif;
  background: #f7fdf4;
}
.hero {
  background: #fff;
  padding: 2rem 1.5rem;
  border-bottom: 2px solid #d4edda;
}
.hero-tag {
  font-size: 11px;
  color: #7c3aed;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  margin-bottom: 0.4rem;
}
.hero-title {
  font-size: 32px;
  font-weight: 700;
  color: #1a4d2e;
}
.hero-title em {
  font-style: italic;
  color: #16a34a;
}
.hero-sub {
  font-size: 14px;
  color: #6b7280;
  margin-top: 0.3rem;
}
</style>
