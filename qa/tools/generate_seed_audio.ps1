# Generates spoken seed audio for the mock corridor clips using Windows TTS.
# Output: app/src/main/res/raw/*.wav (22.05 kHz, 16-bit, mono)
# Run with Windows PowerShell: powershell.exe -NoProfile -File qa\tools\generate_seed_audio.ps1

Add-Type -AssemblyName System.Speech

$outDir = Join-Path $PSScriptRoot "..\..\app\src\main\res\raw"
$outDir = (Resolve-Path $outDir).Path

$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    22050,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voices = $synth.GetInstalledVoices() | Where-Object { $_.Enabled } | ForEach-Object { $_.VoiceInfo.Name }
Write-Host "Voices: $($voices -join ', ')"

$clips = @(
    @{
        File  = "clip_asbury_boardwalk_01.wav"
        Voice = 1
        Rate  = 0
        Text  = "You're walking past the old diner on the Asbury Park boardwalk. See that corner booth by the window? That's where Springsteen used to sit after late sets at the Stone Pony. Musicians would pile in around two in the morning, trading rumors about who got signed and who got dropped. The booth is still there. Order the disco fries, sit where Bruce sat, and watch the boardwalk wake up."
    },
    @{
        File  = "clip_belmar_tacos_02.wav"
        Voice = 0
        Rate  = 1
        Text  = "Quick tip if you're heading south past the Belmar marina. The fish taco stand with the blue awning, don't get in the main line. Walk around to the takeout window on the dock side. Same kitchen, half the wait. Get the grilled mahi with extra lime crema, and eat it watching the party boats come in. You're welcome."
    },
    @{
        File  = "clip_neptune_porch_04.wav"
        Voice = 1
        Rate  = -1
        Text  = "There's a porch on Ridge Avenue in Neptune City where three generations learned to play chess. My grandfather set up a folding table there in 1974, and somehow it never came down. Kids from the block still knock on the door asking if the board is out. The man who lives there now never met my grandfather, but he keeps a chess set by the door, just in case."
    },
    @{
        File  = "clip_lake_como_05.wav"
        Voice = 0
        Rate  = 0
        Text  = "Here's a footnote for you. That little lake town you're passing? Until 2005 it was called South Belmar. The locals voted to rename it Lake Como, after the lake, which was itself named after the one in Italy. Some of the old-timers still refuse to say Lake Como. Ask anyone at the firehouse and they'll tell you, the zip code changed, the people didn't."
    }
)

foreach ($clip in $clips) {
    $path = Join-Path $outDir $clip.File
    if ($voices.Count -gt 1) {
        $synth.SelectVoice($voices[[Math]::Min($clip.Voice, $voices.Count - 1)])
    }
    $synth.Rate = $clip.Rate
    $synth.SetOutputToWaveFile($path, $format)
    $synth.Speak($clip.Text)
    $synth.SetOutputToNull()
    Write-Host "Wrote $path"
}
$synth.Dispose()
Write-Host "Done."
