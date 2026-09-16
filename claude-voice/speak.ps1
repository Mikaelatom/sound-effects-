# Claude Voice - reads Claude Cowork's replies out loud.
#
# Uses the voice already built into Windows. Nothing to install.
# Close this window to stop.

$ErrorActionPreference = 'Stop'

# Where Claude Cowork / Claude Code keep their conversations.
function Get-WatchDirs {
    $roots = @($env:USERPROFILE, $HOME, $env:APPDATA) | Where-Object { $_ }
    $dirs = @()
    foreach ($root in $roots) {
        foreach ($sub in @('.claude\projects', '.config\claude\projects')) {
            $path = Join-Path $root $sub
            if ((Test-Path -LiteralPath $path) -and ($dirs -notcontains $path)) { $dirs += $path }
        }
    }
    return $dirs
}

function Get-ConversationFiles {
    $files = @()
    foreach ($dir in Get-WatchDirs) {
        $files += Get-ChildItem -LiteralPath $dir -Filter *.jsonl -Recurse -File -ErrorAction SilentlyContinue
    }
    return $files
}

function New-SpeechVoice {
    Add-Type -AssemblyName System.Speech
    $voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
    $voice.Rate = 1          # -10 (slow) to 10 (fast)
    $voice.Volume = 100
    return $voice
}

# ------------------------------------------------ making the text sound like speech

function ConvertTo-SpeechText {
    param([string]$Text)
    if (-not $Text) { return '' }
    $t = $Text
    $t = [regex]::Replace($t, '(?s)```.*?```', ' code block. ')       # code
    $t = [regex]::Replace($t, '`([^`\r\n]+)`', '$1')                  # `inline code`
    $t = [regex]::Replace($t, '\[([^\]]+)\]\([^)]*\)', '$1')          # [links](url)
    $t = [regex]::Replace($t, 'https?://\S+', ' link ')               # bare urls
    $t = [regex]::Replace($t, '(?m)^\s*#{1,6}\s*', '')                # # headings
    $t = [regex]::Replace($t, '(?m)^\s*([-*+]|\d+[.)])\s+', '')       # bullets
    $t = [regex]::Replace($t, '(?m)^\s*\|.*\|\s*$', '')               # table rows
    $t = [regex]::Replace($t, '(\*{1,3}|_{2,3}|~~)(\S.*?\S|\S)\1', '$2')  # **bold** etc
    $t = [regex]::Replace($t, '[\uD83C-\uDBFF][\uDC00-\uDFFF]|[☀-➿]|️', ' ')  # emoji
    $t = [regex]::Replace($t, '\s+', ' ').Trim()

    if ($t.Length -gt 600) {                 # don't monologue - stop at a sentence
        $cut = $t.Substring(0, 600)
        $stop = $cut.LastIndexOf('. ')
        if ($stop -gt 200) { $cut = $cut.Substring(0, $stop + 1) }
        $t = $cut + '...'
    }
    return $t
}

# ------------------------------------------------------- reading the conversation files

function Get-ReplyFromLine {
    param([string]$Line)
    try { $entry = $Line | ConvertFrom-Json } catch { return '' }
    if ($entry.type -ne 'assistant') { return '' }
    if ($entry.isSidechain) { return '' }

    $content = $entry.message.content
    if ($null -eq $content) { return '' }

    if ($content -is [string]) {
        $blocks = @($content)
    } else {
        $blocks = @($content | Where-Object { $_.type -eq 'text' } | ForEach-Object { $_.text })
    }
    $joined = ($blocks | Where-Object { $_ }) -join "`n"
    return ConvertTo-SpeechText $joined
}

function Get-NewLines {
    param([string]$Path, [long]$Offset)
    $result = @{ Lines = @(); Offset = $Offset }

    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
              [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        if ($Offset -ge $stream.Length) { return $result }
        $stream.Seek($Offset, [System.IO.SeekOrigin]::Begin) | Out-Null
        $count = [int]($stream.Length - $Offset)
        $buffer = New-Object byte[] $count
        $read = $stream.Read($buffer, 0, $count)
    } finally {
        $stream.Dispose()
    }

    $text = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $read)

    # Only take whole lines - Claude may still be writing the last one.
    $end = $text.LastIndexOf("`n")
    if ($end -lt 0) { return $result }
    $complete = $text.Substring(0, $end + 1)

    $result.Offset = $Offset + [System.Text.Encoding]::UTF8.GetByteCount($complete)
    $result.Lines = @($complete -split "`r?`n" | Where-Object { $_.Trim() })
    return $result
}

# --------------------------------------------------------------------------- the loop

function Start-ClaudeVoice {
    Write-Host ''
    Write-Host '  Claude Voice' -ForegroundColor Cyan
    Write-Host '  Listening to Claude Cowork. Anything Claude says, I read out loud.'
    Write-Host '  Close this window to stop.'
    Write-Host ''

    try {
        $voice = New-SpeechVoice
    } catch {
        Write-Host "  Couldn't start the Windows voice: $_" -ForegroundColor Red
        return 1
    }

    if (-not (Get-WatchDirs)) {
        Write-Host '  No Claude conversations folder found yet.' -ForegroundColor Yellow
        Write-Host '  Open Claude Cowork on this computer, then start me again.'
        Write-Host ''
    }

    # Start from now, so it doesn't read your whole history back to you.
    $seen = @{}
    foreach ($file in Get-ConversationFiles) { $seen[$file.FullName] = $file.Length }

    while ($true) {
        foreach ($file in Get-ConversationFiles) {
            $path = $file.FullName
            if (-not $seen.ContainsKey($path)) { $seen[$path] = 0 }      # new conversation
            if ($file.Length -lt $seen[$path]) { $seen[$path] = 0 }      # file was reset
            if ($file.Length -le $seen[$path]) { continue }

            try { $new = Get-NewLines $path $seen[$path] } catch { continue }
            $seen[$path] = $new.Offset

            foreach ($line in $new.Lines) {
                $reply = Get-ReplyFromLine $line
                if ($reply.Length -lt 2) { continue }
                $preview = $reply
                if ($preview.Length -gt 90) { $preview = $preview.Substring(0, 90) + '...' }
                Write-Host "  > $preview" -ForegroundColor Green
                $voice.Speak($reply)
            }
        }
        Start-Sleep -Milliseconds 700
    }
}

Start-ClaudeVoice
