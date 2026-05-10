<script setup lang="ts">
const props = defineProps<{
  lastUpdated: string
}>()

function formatDate(iso: string): string {
  if (!iso) return '-'

  const [year, month, day] = iso.split('-')

  return `${day}/${month}/${year}`
}
</script>

<template>
  <footer class="mt-8 px-6 border-t border-green-100 text-center">
    <p class="text-xs text-gray-400 leading-relaxed">
      Agenda mantida pela comuniadde de João Pessoa. <br />
      Atualizado em
      <span class="font-semibold text-gray-500">
        {{ formatDate(props.lastUpdated) }}
      </span>
    </p>
  </footer>
</template>
