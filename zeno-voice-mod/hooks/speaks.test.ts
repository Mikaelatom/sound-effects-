import { expect, mock, test } from 'claude-code/testing'
import type { On } from 'claude-code'

const turn = {
  answer: 'All done. I fixed **main.py**.',
  durationMs: 10,
  isAborted: false,
  turnId: 't1',
  reason: 'answer' as const,
}

/**
 * Stand in for the engine beneath the mod: answer the turn, and catch whatever
 * the mod hands to the synthesizer.
 */
function bottom(on: On, heard: string[], stored: Record<string, unknown> = {}) {
  mock.store(on, stored)
  on('audio.speak', async ($, e) => {
    heard.push(e.text)
    return { value: { via: 'system' as const } }
  })
  on('turn.complete', async () => ({ text: '' }))
}

test('reads the reply out loud when a turn ends', async ($, on) => {
  const heard: string[] = []
  bottom(on, heard)

  await $.turn.complete(turn)

  expect(heard).toEqual(['All done. I fixed main.py.'])
})

test('says nothing for a subagent turn', async ($, on) => {
  const heard: string[] = []
  bottom(on, heard)

  await $.turn.complete({ ...turn, agentId: 'sub-1' })

  expect(heard).toEqual([])
})

test('says nothing when the turn was interrupted', async ($, on) => {
  const heard: string[] = []
  bottom(on, heard)

  await $.turn.complete({ ...turn, reason: 'aborted', isAborted: true })

  expect(heard).toEqual([])
})

test('says nothing for a thinking-only turn', async ($, on) => {
  const heard: string[] = []
  bottom(on, heard)

  await $.turn.complete({ ...turn, answer: '' })

  expect(heard).toEqual([])
})

test('stays silent while muted', async ($, on) => {
  const heard: string[] = []
  bottom(on, heard, { muted: true })

  await $.turn.complete(turn)

  expect(heard).toEqual([])
})

test('uses the voice the person chose', async ($, on) => {
  const heard: string[] = []
  const voices: (string | undefined)[] = []
  mock.store(on, { voice: 'Samantha' })
  on('audio.speak', async ($$, e) => {
    heard.push(e.text)
    voices.push(e.voice)
    return { value: { via: 'system' as const } }
  })
  on('turn.complete', async () => ({ text: '' }))

  await $.turn.complete(turn)

  expect(voices).toEqual(['Samantha'])
})
