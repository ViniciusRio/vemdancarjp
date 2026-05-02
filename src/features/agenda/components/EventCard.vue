<script setup lang="ts">
import type { Event } from '../types';

const props = defineProps<{
  event: Event
}>()

function getIcone(name: string): string {
  const nameEvent = name.toLocaleLowerCase()
  if (nameEvent.includes('prática') || nameEvent.includes('pratica')) return '🕺'
  if (nameEvent.includes('gravação') || nameEvent.includes('gravacao')) return '🎙️'
  if (nameEvent.includes('forró') || nameEvent.includes('forro')) return '🪗'
  if (nameEvent.includes('projeto')) return '🎶'

  return '🎵'
}
</script>
<template>
  <component
  :is="props.event.instagram ? 'a' : 'div'"
  :href="props.event.instagram ?? undefined"
  :target="props.event.instagram ? '_blank' : undefined"
  :rel="props.event.instagram ? 'noopener' : undefined"
  class="flex items-start gap-3 p-3 bg-white border-2 border-gray-100 rounded-xl mb-2 no-underline text-inherit transition-all"
  :class="props.event.instagram ? 'cursor-pointer hover:border-green-200 hover:-translate-y-px' : 'cursor-default'"
>
    <div class="w-10 h-10 rounded-xl bg-green-50 border-2 border-green-100 flex items-center justify-center flex-shrink-0 text-lg">
      {{ getIcone(props.event.name) }}
    </div>
    <div class="flex-1 min-w-0">
      <p class="text-sm font-bold text-gray-900 mb-0.5">{{ props.event.name }}</p>
      <p class="text-xs text-gray-500 flex items-center gap-1 flex-wrap">
        <span>{{ props.event.venue }}</span>
        <span v-if="props.event.neighborhood" class="text-gray-200">.</span>
        <span v-if="props.event.neighborhood"> {{ props.event.neighborhood }}</span>
      </p>
    </div>

    <div class="flex flex-col items-end gap-1 flex-shrink-0">
      <span v-if="props.event.frequency" class="text-xs px-2 py-0.5 rounded-full bg-amber-50 border border-amber-200 text-amber-800 font-bold whitespace-nowrap">
        {{ props.event.frequency }}
      </span>
      <span v-if="props.event.instagram" class="text-xs px-2 py-0.5 rounded-full bg-purple-50 border border-purple-200 text-purple-700 font-semibold">
        Instagram
      </span>
    </div>
  </component>

</template>
