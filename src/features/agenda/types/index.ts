export interface Event {
  name: string
  venue: string
  neighborhood: string
  instagram: string | null
  frequency: string | null
}

export interface Day {
  id: string
  label: string
  events: Event[]
}

export interface VariableVenue {
  name: string
  neighborhood: string
  days: string
  instagram: string | null
}

export interface OtherVenue {
  name: string
  neighborhood: string
  instagram: string | null
}

export interface Agenda {
  city: string
  title: string
  subtitle: string
  lastUpdated: string
  days: Day[]
  variableVenues: VariableVenue[]
  otherVenues: OtherVenue[]
}

export type DayFilter = string | 'all' | 'today'
