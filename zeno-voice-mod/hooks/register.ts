import type { Register } from 'claude-code'

/** Where the on/off choice lives, so it survives restarts. */
const MUTED = 'muted'
const VOICE = 'voice'

/** Longer than this and a reply is cut at a sentence instead of droning on. */
const MAX_CHARS = 700

/**
 * Turn a written answer into something worth hearing: code blocks announced
 * rather than spelled out, links and file paths shortened, markup dropped.
 */
export function forSpeech(markdown: string): string {
  let text = markdown

  text = text.replace(/```[\s\S]*?```/g, ' code block. ')
  text = text.replace(/`([^`\n]+)`/g, '$1')
  text = text.replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
  text = text.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
  text = text.replace(/https?:\/\/\S+/g, ' link ')
  text = text.replace(/^[ \t]*#{1,6}[ \t]*/gm, '')
  text = text.replace(/^[ \t]*([-*+]|\d+[.)])[ \t]+/gm, '')
  text = text.replace(/^[ \t]*\|.*\|[ \t]*$/gm, '')
  text = text.replace(/^[ \t]*[-*_][ \t]*([-*_][ \t]*){2,}$/gm, '')
  text = text.replace(/(\*{1,3}|_{2,3}|~~)(\S[\s\S]*?\S|\S)\1/g, '$2')
  text = text.replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{2B00}-\u{2BFF}]/gu, ' ')

  // say the file's name, not every folder above it
  text = text.replace(/(?<![\w:/])(?:~|\.{1,2})?\/?[\w.\-]+(?:\/[\w.\-]+)+\/?/g, match =>
    match.includes('.') || /^[~.]?\//.test(match)
      ? match.replace(/\/$/, '').split('/').pop() || ' '
      : match,
  )

  text = text.replace(/\s+/g, ' ').trim()

  if (text.length > MAX_CHARS) {
    const head = text.slice(0, MAX_CHARS)
    const lastStop = head.lastIndexOf('. ')
    text = (lastStop > MAX_CHARS / 3 ? head.slice(0, lastStop + 1) : head) + '...'
  }

  return text
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'voice',
      description: 'Claude Voice: on, off, or a voice name',
    })
    return next(e)
  })

  on('command.run', { command: 'voice' }, async ($, e) => {
    const wanted = e.args.trim()

    if (wanted === '' || wanted === 'status') {
      const muted = await $.store.get(MUTED)
      const voice = await $.store.get(VOICE)
      return {
        text: muted
          ? 'Claude Voice is off. Say /voice on to turn it back on.'
          : `Claude Voice is on${voice ? `, using ${String(voice)}` : ''}. Say /voice off to stop it.`,
      }
    }

    if (/^(off|stop|mute)$/i.test(wanted)) {
      await $.store.set(MUTED, true)
      return { text: 'Claude Voice off.' }
    }

    if (/^(on|start|unmute)$/i.test(wanted)) {
      await $.store.set(MUTED, false)
      return { text: 'Claude Voice on. I will read each reply out loud.' }
    }

    // anything else is taken as a system voice name
    await $.store.set(VOICE, wanted)
    await $.store.set(MUTED, false)
    try {
      await $.audio.speak('This is the voice I will use.', { voice: wanted })
      return { text: `Claude Voice will use ${wanted}.` }
    } catch (error) {
      await $.store.delete(VOICE)
      return { text: `There is no voice called ${wanted} on this computer, so I kept the old one.` }
    }
  }).catch(() => ({ text: 'Claude Voice could not read its settings just now.' }))

  on('turn.complete', async ($, e, next) => {
    const result = await next(e)

    // Only the answer the person is actually reading: not a subagent's report,
    // not an interrupted or failed turn.
    if (e.agentId !== undefined || e.reason !== 'answer') return result
    if (await $.store.get(MUTED)) return result

    const text = forSpeech(e.answer ?? '')
    if (text.length < 2) return result

    const voice = await $.store.get(VOICE)
    const options = typeof voice === 'string' && voice ? { voice } : undefined

    // Not awaited: the person gets their prompt back while Zeno is still
    // talking, and a machine with no synthesizer just stays quiet.
    void $.audio
      .speak(text, options)
      .catch(() => $.ui.status(undefined))

    return result
  })
}
