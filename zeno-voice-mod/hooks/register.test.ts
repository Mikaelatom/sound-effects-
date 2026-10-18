import { expect, test } from 'claude-code/testing'
import { forSpeech } from './register'

test('code blocks are announced, not spelled out', () => {
  expect(forSpeech('Fixed it.\n\n```py\nprint(1)\n```')).toBe('Fixed it. code block.')
})

test('headings and emphasis are dropped', () => {
  expect(forSpeech('## Done\n\nI changed **main.py** and `parse_args`.'))
    .toBe('Done I changed main.py and parse_args.')
})

test('links are spoken as the word link, keeping the link text', () => {
  expect(forSpeech('See [the docs](https://x.com/a) and https://y.com/b'))
    .toBe('See the docs and link')
})

test('bullets and tables do not read as punctuation', () => {
  expect(forSpeech('- one\n- two\n\n| a | b |\n|---|---|')).toBe('one two')
})

test('a long path is read as just the file name', () => {
  expect(forSpeech('I edited src/app/main.py today')).toBe('I edited main.py today')
})

test('prose containing a slash is left alone', () => {
  expect(forSpeech('Use this and/or that')).toBe('Use this and/or that')
})

test('emoji are removed', () => {
  expect(forSpeech('Done ✅ and shipped \u{1F680}')).toBe('Done and shipped')
})

test('a long answer is cut at a sentence, not mid-word', () => {
  const spoken = forSpeech('This is a sentence. '.repeat(80))
  expect(spoken.length).toBeLessThan(720)
  expect(spoken.endsWith('sentence....')).toBe(true)
})

test('a thinking-only turn says nothing', () => {
  expect(forSpeech('')).toBe('')
})
