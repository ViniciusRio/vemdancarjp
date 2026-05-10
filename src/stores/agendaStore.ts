import type { Agenda, DayFilter } from '@/features/agenda/types'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { agendaService } from '@/services/agendaService'

export const useAgendaStore = defineStore('agenda', () => {
  // watch agenda with ref, then know the change. Start null
  const agenda = ref<Agenda | null>(null)
  const activeFilter = ref<DayFilter>('all')
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  function getTodayId(): string {
    const days = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday']
    return days[new Date().getDay()] ?? 'sunday'
  }
  const currentDay = computed<string>(() => getTodayId())

  const filteredDays = computed(() => {
    if (!agenda.value) return []
    if (activeFilter.value === 'all') return agenda.value.days

    const target = activeFilter.value === 'today' ? currentDay.value : activeFilter.value

    return agenda.value.days.filter((day) => day.id === target)
  })

  async function loadAgenda() {
    isLoading.value = true
    error.value = null
    try {
      agenda.value = await agendaService.fetchAgenda()
    } catch (e) {
      error.value = e instanceof Error ? e.message : `Unknown error`
    } finally {
      isLoading.value = false
    }
  }

  function setFilter(filter: DayFilter) {
    activeFilter.value = filter
  }

  return {
    agenda,
    activeFilter,
    isLoading,
    error,
    currentDay,
    filteredDays,
    loadAgenda,
    setFilter,
  }
})
