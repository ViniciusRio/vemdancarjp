<script setup lang="ts">
import type { VariableVenue } from '@/features/agenda/types'

defineProps<{
  venues: VariableVenue[]
}>()
</script>

<template>
  <section class="px-6 mt-8">
    <div class="bg-white border-2 border-amber-200 rounded-xl overflow-hidden">
      <div class="px-4 py-3 bg-amber-50 border-b border-amber-200">
        <p class="text-xs font-bold uppercase tracking-widest text-amber-800">
          ⚠ Lugares variáveis — checar programação antes de ir
        </p>
      </div>

      <component
        :is="venue.instagram ? 'a' : 'div'"
        :href="venue.instagram ?? undefined"
        :target="venue.instagram ? '_blank' : undefined"
        :rel="venue.instagram ? 'noopener' : undefined"
        v-for="(venue, index) in venues"
        :key="venue.name"
        class="px-4 py-3 flex justify-between items-center gap-4 transition-all"
        :class="[
          index < venues.length - 1 ? 'border-b border-amber-100' : '',
          venue.instagram ? 'cursor-pointer hover:bg-amber-50' : '',
        ]"
      >
        <div>
          <p class="text-sm font-semibold text-gray-700">{{ venue.name }}</p>
          <p class="text-xs text-gray-400">{{ venue.neighborhood }}</p>
        </div>
        <div class="flex flex-col items-end gap-1 flex-shrink-0">
          <span
            class="text-xs font-bold px-2 py-1 rounded-full bg-amber-50 text-amber-800 whitespace-nowrap border border-amber-200"
          >
            {{ venue.days }}
          </span>
          <span
            v-if="venue.instagram"
            class="text-xs px-2 py-0.5 rounded-full bg-purple-50 border border-purple-200 text-purple-700 font-semibold"
          >
            Instagram
          </span>
        </div>
      </component>
    </div>
  </section>
</template>
