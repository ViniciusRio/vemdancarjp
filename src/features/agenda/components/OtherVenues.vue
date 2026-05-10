<script setup lang="ts">
import type { OtherVenue } from '@/features/agenda/types'

defineProps<{
  venues: OtherVenue[]
}>()
</script>

<template>
  <section class="px-6 mt-6">
    <p class="text-xs font-bold uppercase tracking-widest text-gray-400 mb-3">
      Outros lugares que tocam forró
    </p>
    <div class="flex flex-wrap gap-2">
      <component
        :is="venue.instagram ? 'a' : 'span'"
        :href="venue.instagram ?? undefined"
        :target="venue.instagram ? '_blank' : undefined"
        :rel="venue.instagram ? 'noopener' : undefined"
        v-for="venue in venues"
        :key="venue.name"
        class="px-3 py-1.5 rounded-full bg-white border-2 border-purple-200 text-purple-700 text-xs font-semibold no-underline transition-all"
        :class="venue.instagram ? 'cursor-pointer hover:bg-purple-50 hover:border-purple-400' : ''"
      >
        {{ venue.name }}
        <span class="text-purple-300">· {{ venue.neighborhood }}</span>
      </component>
    </div>
  </section>
</template>
