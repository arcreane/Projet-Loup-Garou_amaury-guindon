# -*- coding: utf-8 -*-
"""
Génère les 5 effets sonores du jeu en WAV (synthèse pure, zéro dépendance).
Usage :  py tools/generate_sounds.py
Sortie :  frontend/src/main/resources/sounds/*.wav

Les sons sont libres de droits par construction (synthétisés).
Remplaçables par de vrais enregistrements (freesound.org) en gardant les mêmes noms.
"""
import math
import os
import random
import struct
import wave

SR = 44100  # Hz
OUT = os.path.join(os.path.dirname(__file__), '..', 'frontend', 'src', 'main', 'resources', 'sounds')

random.seed(42)  # rendu reproductible


def write_wav(name, samples, peak=0.8):
    """Normalise et écrit un fichier WAV mono 16-bit."""
    m = max(1e-9, max(abs(s) for s in samples))
    scale = peak * 32767 / m
    path = os.path.join(OUT, name)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(s * scale)) for s in samples))
    print(f'  {name:12s} {len(samples)/SR:.1f}s')


def env(i, n, attack, release):
    """Enveloppe attaque/relâche linéaire (en secondes)."""
    t, total = i / SR, n / SR
    if t < attack:
        return t / attack
    if t > total - release:
        return max(0.0, (total - t) / release)
    return 1.0


def silence(dur):
    return [0.0] * int(SR * dur)


def mix(base, add, at):
    """Mixe `add` dans `base` à partir de `at` secondes (étend si besoin)."""
    off = int(at * SR)
    while len(base) < off + len(add):
        base.append(0.0)
    for i, s in enumerate(add):
        base[off + i] += s
    return base


# ---------------------------------------------------------------- night : hibou + grillons
def hoot(freq, dur, vol=1.0):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        f = freq * (1.0 - 0.08 * t / dur)          # léger glissando descendant
        s = math.sin(2 * math.pi * f * t) + 0.35 * math.sin(4 * math.pi * f * t)
        out.append(vol * s * env(i, n, 0.05, dur * 0.5))
    return out


def crickets(dur, vol=0.05):
    n = int(SR * dur)
    out = [0.0] * n
    t0 = 0.15
    while t0 < dur - 0.1:
        chirp_n = int(SR * 0.045)
        off = int(t0 * SR)
        for i in range(min(chirp_n, n - off)):
            t = i / SR
            out[off + i] += vol * math.sin(2 * math.pi * 4300 * t) * math.sin(math.pi * i / chirp_n)
        t0 += random.uniform(0.10, 0.28)
    return out


def gen_night():
    out = silence(3.2)
    mix(out, hoot(340, 0.55, 0.9), 0.30)
    mix(out, hoot(310, 0.80, 0.8), 1.05)
    mix(out, hoot(335, 0.50, 0.5), 2.20)
    mix(out, crickets(3.2), 0.0)
    write_wav('night.wav', out, peak=0.55)


# ---------------------------------------------------------------- day : cloche du village
def bell_strike(f0, dur, vol=1.0):
    # Partiels inharmoniques typiques d'une cloche (hum, prime, tierce, quinte, nominale)
    partials = [(0.5, 0.6), (1.0, 1.0), (1.2, 0.55), (1.5, 0.35), (2.0, 0.5), (2.66, 0.2)]
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        s = sum(a * math.sin(2 * math.pi * f0 * r * t) * math.exp(-t * (2.2 + r)) for r, a in partials)
        out.append(vol * s)
    return out


def gen_day():
    out = silence(3.0)
    mix(out, bell_strike(392, 2.6, 1.0), 0.05)   # sol
    mix(out, bell_strike(392, 2.0, 0.8), 1.15)
    write_wav('day.wav', out, peak=0.65)


# ---------------------------------------------------------------- howl : hurlement de loup
def howl_voice(base, dur, vol=1.0):
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        p = t / dur
        # Contour de fréquence : monte, plateau, retombe (comme un vrai hurlement)
        if p < 0.35:
            f = base * (0.75 + 0.65 * (p / 0.35))
        elif p < 0.70:
            f = base * 1.40
        else:
            f = base * (1.40 - 0.55 * ((p - 0.70) / 0.30))
        f *= 1.0 + 0.018 * math.sin(2 * math.pi * 5.2 * t) * min(1.0, t * 2)  # vibrato
        phase += 2 * math.pi * f / SR
        s = (math.sin(phase) + 0.45 * math.sin(2 * phase) + 0.18 * math.sin(3 * phase))
        s += 0.02 * (random.random() * 2 - 1)     # souffle
        out.append(vol * s * env(i, n, 0.45, dur * 0.35))
    return out


def gen_howl():
    out = silence(4.2)
    mix(out, howl_voice(300, 3.6, 1.0), 0.0)
    mix(out, howl_voice(255, 2.8, 0.45), 1.1)     # 2e loup au loin
    write_wav('howl.wav', out, peak=0.7)


# ---------------------------------------------------------------- death : coup sourd + glas
def gen_death():
    dur = 1.6
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        # Percussion grave qui tombe (90 → 38 Hz)
        f = 90 * math.exp(-t * 2.2) + 38
        s = math.sin(2 * math.pi * f * t) * math.exp(-t * 4.5)
        # Bruit d'impact bref
        if t < 0.05:
            s += 0.7 * (random.random() * 2 - 1) * (1 - t / 0.05)
        # Nappe sombre
        s += 0.25 * math.sin(2 * math.pi * 110 * t) * math.exp(-t * 3.0)
        out.append(s)
    write_wav('death.wav', out, peak=0.8)


# ---------------------------------------------------------------- victory : fanfare
def tone(freq, dur, vol=1.0, bright=0.5):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        s = math.sin(2 * math.pi * freq * t) \
            + bright * 0.5 * math.sin(2 * math.pi * freq * 2 * t) \
            + bright * 0.25 * math.sin(2 * math.pi * freq * 3 * t)
        out.append(vol * s * env(i, n, 0.015, dur * 0.45))
    return out


def gen_victory():
    C5, E5, G5, C6 = 523.25, 659.25, 783.99, 1046.5
    out = silence(3.0)
    for k, f in enumerate([C5, E5, G5]):
        mix(out, tone(f, 0.22, 0.8), 0.05 + k * 0.17)
    # Accord final tenu
    for f, v in [(C5, 0.5), (E5, 0.45), (G5, 0.45), (C6, 0.65)]:
        mix(out, tone(f, 1.9, v, bright=0.7), 0.62)
    write_wav('victory.wav', out, peak=0.7)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    print('Génération des sons →', os.path.abspath(OUT))
    gen_night()
    gen_day()
    gen_howl()
    gen_death()
    gen_victory()
    print('OK.')
