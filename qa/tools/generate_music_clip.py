"""Generates the synthetic 'garage demo' music seed clip as a WAV file.

A simple chord-arpeggio progression with a soft envelope -- placeholder
original music until real creator uploads exist.
Output: app/src/main/res/raw/clip_route35_demo_03.wav (22.05 kHz, 16-bit, mono)
"""
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22050
OUT = Path(__file__).resolve().parents[2] / "app/src/main/res/raw/clip_route35_demo_03.wav"

# i-VI-III-VII progression in A minor, two passes of arpeggios + pad
PROGRESSION = [
    ("Am", [220.00, 261.63, 329.63]),
    ("F", [174.61, 220.00, 261.63]),
    ("C", [130.81, 164.81, 261.63]),
    ("G", [196.00, 246.94, 293.66]),
]
BAR_SECONDS = 2.4
PASSES = 4


def envelope(t: float, dur: float) -> float:
    attack = 0.02
    release = 0.25
    if t < attack:
        return t / attack
    if t > dur - release:
        return max(0.0, (dur - t) / release)
    return 1.0


samples: list[float] = []
for _ in range(PASSES):
    for _, chord in PROGRESSION:
        bar_len = int(BAR_SECONDS * SAMPLE_RATE)
        note_len = bar_len // 6
        for i in range(bar_len):
            t = i / SAMPLE_RATE
            # pad: quiet sustained chord
            pad = sum(math.sin(2 * math.pi * f * t) for f in chord) * 0.06
            # arpeggio: cycle chord tones (up-down), one octave up
            step = min(i // note_len, 5)
            tone = chord[step if step < 3 else 5 - step] * 2
            nt = (i % note_len) / SAMPLE_RATE
            arp = math.sin(2 * math.pi * tone * t) * 0.22 * envelope(nt, note_len / SAMPLE_RATE)
            # gentle bass on the root
            bass = math.sin(2 * math.pi * (chord[0] / 2) * t) * 0.12
            samples.append(pad + arp + bass)

# fade out the tail
fade = int(1.2 * SAMPLE_RATE)
for i in range(fade):
    samples[-fade + i] *= 1.0 - i / fade

OUT.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(OUT), "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SAMPLE_RATE)
    frames = b"".join(
        struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples
    )
    w.writeframes(frames)

print(f"Wrote {OUT} ({len(samples) / SAMPLE_RATE:.1f}s)")
