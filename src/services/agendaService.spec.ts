import { afterEach, describe, expect, it, vi } from 'vitest'

const { from } = vi.hoisted(() => ({ from: vi.fn<(table: string) => unknown>() }))

vi.mock('@/services/supabase', () => ({ supabase: { from } }))

function supabaseTable(data: unknown[]) {
  return { select: () => ({ order: () => Promise.resolve({ data, error: null }) }) }
}

async function loadService() {
  vi.resetModules()
  const { agendaService } = await import('@/services/agendaService')
  return agendaService
}

describe('agendaService.fetchAgenda', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    from.mockReset()
  })

  it('reads from Supabase when VITE_AGENDA_SOURCE is not set', async () => {
    vi.stubEnv('VITE_AGENDA_SOURCE', '')
    const fetchSpy = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchSpy)
    from.mockImplementation((table: string) =>
      supabaseTable(
        table === 'events'
          ? [
              {
                day_id: 'monday',
                name: 'Forró',
                venue: 'Bar',
                neighborhood: 'Centro',
                instagram: '@forro',
                frequency: 'semanal',
              },
            ]
          : [],
      ),
    )

    const agenda = await (await loadService()).fetchAgenda()

    expect(fetchSpy).not.toHaveBeenCalled()
    expect(agenda.days.find((day) => day.id === 'monday')?.events).toHaveLength(1)
  })

  it('reads from the backend when VITE_AGENDA_SOURCE is api', async () => {
    vi.stubEnv('VITE_AGENDA_SOURCE', 'api')
    vi.stubEnv('VITE_API_URL', 'https://api.example.com')
    const fetchSpy = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, json: () => Promise.resolve({ days: [] }) } as Response)
    vi.stubGlobal('fetch', fetchSpy)

    await (await loadService()).fetchAgenda()

    expect(fetchSpy).toHaveBeenCalledWith('https://api.example.com/api/agenda')
    expect(from).not.toHaveBeenCalled()
  })
})
