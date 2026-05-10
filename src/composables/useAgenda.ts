import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAgendaStore } from '@/stores/agendaStore'

export function useAgenda() {
  const store = useAgendaStore()
  const { agenda, isLoading, error, filteredDays, currentDay, activeFilter } = storeToRefs(store)

  onMounted(() => {
    store.loadAgenda()
  })

  return {
    agenda,
    isLoading,
    error,
    filteredDays,
    currentDay,
    activeFilter,
    setFilter: store.setFilter,
  }
}
