"""Soundtrack for the RideLink brag video: 25 s, 120 BPM, A minor -> A major lift at the reveal.
Every part sits on one grid counted from frame 0 (beat = 0.5 s). Writes work/score.wav (48 kHz stereo)."""
import numpy as np, sys, wave

SR = 48000
DUR = 25.0
N = int(SR * DUR)
BEAT = 0.5
rng = np.random.default_rng(7)

def hz(m):  # midi -> Hz
    return 440.0 * 2 ** ((m - 69) / 12)

def env_adsr(n, a, d, s, r, sus_len):
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    sl = max(int(sus_len * SR) - a - d, 0)
    e = np.concatenate([np.linspace(0, 1, max(a, 1)), np.linspace(1, s, max(d, 1)), np.full(sl, s), np.linspace(s, 0, max(r, 1))])
    return e[:n] if len(e) >= n else np.pad(e, (0, n - len(e)))

L = np.zeros(N); R = np.zeros(N)
bus = {k: np.zeros((2, N)) for k in ['drums', 'bass', 'pad', 'arp', 'sfx', 'fx']}

def put(name, sig, t, pan=0.0, gain=1.0):
    i = int(round(t * SR))
    if i >= N: return
    sig = sig[: N - i] * gain
    l = np.cos((pan + 1) * np.pi / 4); r = np.sin((pan + 1) * np.pi / 4)
    bus[name][0, i:i + len(sig)] += sig * l * 1.414
    bus[name][1, i:i + len(sig)] += sig * r * 1.414

def lp(x, cutoff):  # one-pole low-pass, cutoff may be array
    c = np.broadcast_to(np.asarray(cutoff, float), x.shape)
    a = 1 - np.exp(-2 * np.pi * c / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc); y[i] = acc
    return y

def lp_fast(x, cutoff):  # fixed cutoff, vectorised via scipy-free IIR using cumulative trick (2x one-pole)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    from numpy.lib.stride_tricks import sliding_window_view  # noqa (keeps numpy-only)
    y = np.zeros_like(x); acc = 0.0
    # chunked python loop is fine for short signals
    for i in range(len(x)):
        acc += a * (x[i] - acc); y[i] = acc
    return y

# ---------------- instruments ----------------
def kick(gain=1.0):
    n = int(0.45 * SR); t = np.arange(n) / SR
    f = 48 + 110 * np.exp(-t * 32)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) * np.exp(-t * 7.5)
    s += 0.25 * rng.standard_normal(n) * np.exp(-t * 300)
    return np.tanh(s * 1.6) * gain

def clap():
    n = int(0.35 * SR); t = np.arange(n) / SR
    nz = rng.standard_normal(n)
    e = np.zeros(n)
    for o in (0, 0.011, 0.022):
        i = int(o * SR); e[i:] += np.exp(-(t[: n - i]) * 38)
    s = nz * e
    s = s - lp_fast(s, 900)  # high-pass-ish
    return s * 0.5

def hat(open_=False):
    n = int((0.22 if open_ else 0.05) * SR); t = np.arange(n) / SR
    s = rng.standard_normal(n)
    s = s - lp_fast(s, 7000)
    return s * np.exp(-t * (14 if open_ else 70)) * 0.35

def pluck(m, dur=0.22, bright=1.0):
    n = int(dur * SR); t = np.arange(n) / SR
    f = hz(m)
    s = (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 20 * bright) + 0.25 * np.sin(2 * np.pi * 3 * f * t) * np.exp(-t * 30))
    return s * np.exp(-t * 9) * np.minimum(1, t * 400)

def bass(m, dur):
    n = int(dur * SR); t = np.arange(n) / SR
    f = hz(m)
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t) + 0.12 * np.sign(np.sin(2 * np.pi * f * t))
    e = env_adsr(n, 0.005, 0.12, 0.7, 0.06, dur)
    return np.tanh(s * 1.2) * e * 0.55

def pad(notes, dur, a=0.4, r=0.6, bright=0.4):
    n = int((dur + r) * SR); t = np.arange(n) / SR
    s = np.zeros(n)
    for m in notes:
        for det in (-0.08, 0.0, 0.08):
            f = hz(m + det)
            ph = rng.uniform(0, 2 * np.pi)
            saw = 2 * ((f * t + ph / (2 * np.pi)) % 1) - 1
            s += 0.6 * np.sin(2 * np.pi * f * t + ph) + bright * 0.35 * saw
    s /= max(len(notes) * 3, 1)
    s = lp_fast(s, 1800 + 2600 * bright)
    return s * env_adsr(n, a, 0.3, 0.85, r, dur)

def blip(m1, m2):  # error blip: two short square notes, soft, in key
    out = []
    for m in (m1, m2):
        n = int(0.09 * SR); t = np.arange(n) / SR
        sq = np.sign(np.sin(2 * np.pi * hz(m) * t))
        sq = np.round(sq * 4) / 4
        out.append(lp_fast(sq, 2600) * np.exp(-t * 18) * np.minimum(1, t * 600))
    return np.concatenate(out) * 0.6

def tick(m=88, dur=0.08):
    n = int(dur * SR); t = np.arange(n) / SR
    return np.sin(2 * np.pi * hz(m) * t) * np.exp(-t * 60) * 0.7

def ding(m):
    n = int(1.2 * SR); t = np.arange(n) / SR
    f = hz(m)
    return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2.01 * f * t) * np.exp(-t * 6)) * np.exp(-t * 3.2) * np.minimum(1, t * 800) * 0.6

def riser(dur):
    n = int(dur * SR); t = np.arange(n) / SR
    x = t / dur
    nz = rng.standard_normal(n)
    nz = lp(nz, 400 + 9000 * x ** 2)
    f = hz(57) * 2 ** (2 * x)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.3
    return (nz * 0.8 + tone) * x ** 2.2

def impact():
    n = int(2.2 * SR); t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * np.cumsum(38 + 60 * np.exp(-t * 9)) / SR) * np.exp(-t * 2.2)
    nz = rng.standard_normal(n); nz = nz - lp_fast(nz, 3000)
    return np.tanh(sub * 1.4) * 0.9 + nz * np.exp(-t * 5) * 0.18

def crash():
    n = int(2.5 * SR); t = np.arange(n) / SR
    nz = rng.standard_normal(n); nz = nz - lp_fast(nz, 5000)
    return nz * np.exp(-t * 2.4) * 0.25

def ring_tone(m):  # phone ring blip (in key)
    n = int(0.12 * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * hz(m) * t) + 0.4 * np.sin(2 * np.pi * hz(m + 4) * t)
    return s * np.minimum(1, t * 300) * np.exp(-t * 14) * 0.5

# ---------------- arrangement ----------------
A2, C3, D3, E3, F3, G3 = 45, 48, 50, 52, 53, 55
# Act 1 (0-8): A minor, sparse
put('pad', pad([57, 60, 64], 3.0, a=0.15, bright=0.15), 0.0, gain=0.55)
put('pad', pad([53, 57, 60], 2.0, bright=0.2), 3.0, gain=0.55)       # F
put('pad', pad([50, 53, 57], 2.0, bright=0.2), 5.0, gain=0.55)       # Dm
put('pad', pad([52, 56, 59], 1.0, bright=0.3, r=0.2), 7.0, gain=0.55) # E (tension)
for b in range(16):
    t = b * BEAT
    put('drums', hat(), t + 0.25, pan=0.3, gain=0.5)
    if b >= 6 and b % 2 == 0: put('drums', kick(0.55), t)
for t, m in [(0.0, 33), (1.0, 33), (2.0, 33), (3.0, 29), (4.0, 29), (5.0, 26), (6.0, 26), (7.0, 28)]:
    put('bass', bass(m + 12, 0.45), t, gain=0.7)
# rating drop: descending glide while the number falls (0.5 -> 1.75)
n = int(1.25 * SR); tt = np.arange(n) / SR
f = hz(76) * 2 ** (-(7 / 12) * (tt / 1.25))
put('sfx', np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 1.2) * 0.25, 0.5, pan=-0.1)
# error blips on each pain card (3.5, 4.5, 5.5, 6.5)
for i, t in enumerate([3.5, 4.5, 5.5, 6.5]):
    put('sfx', blip(76 - i, 72 - i), t, pan=(-0.25 if i % 2 else 0.25), gain=0.35)
put('fx', riser(1.0), 7.0, gain=0.45)
for k in range(8): put('drums', clap() * (0.2 + 0.1 * k), 7.0 + k * 0.125, gain=0.6)

# Turn (8-10): impact + A major
put('fx', impact(), 8.0, gain=0.8)
put('fx', crash(), 8.0, gain=0.8)
put('pad', pad([57, 61, 64, 69], 2.0, a=0.02, bright=0.6), 8.0, gain=0.7)
put('bass', bass(33, 1.9), 8.0, gain=0.8)
put('sfx', ding(88), 8.75, pan=0.2, gain=0.45)   # spark pops in
for k, m in enumerate([69, 73, 76, 81, 76, 73, 76, 81]):
    put('arp', pluck(m, bright=0.6), 9.0 + k * 0.125, pan=(-0.3 if k % 2 else 0.3), gain=0.35)
put('fx', riser(0.5), 9.5, gain=0.3)

# Groove (10-22.5): A - F#m - D - E, 1 bar each, 120 BPM
prog = [(10, [57, 61, 64], 33), (12, [54, 57, 61], 30), (14, [50, 54, 57], 26), (16, [52, 56, 59], 28),
        (18, [57, 61, 64], 33), (20, [54, 57, 61], 30), (22, [50, 54, 57], 26)]
for t0, ch, root in prog:
    dur = min(2.0, 22.5 - t0)
    put('pad', pad(ch, dur, a=0.05, bright=0.45, r=0.3), t0, gain=0.5)
    for k in range(int(dur / 0.25)):
        t = t0 + k * 0.25
        m = root + 12 + (12 if k % 4 == 3 else 0)
        put('bass', bass(m, 0.22), t, gain=0.75)
    arp = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24]
    for k in range(int(dur / 0.125)):
        put('arp', pluck(arp[k % 4], 0.18, 0.8), t0 + k * 0.125, pan=(-0.35 if k % 2 else 0.35), gain=0.22)
for b in range(int((22.5 - 10.0) / BEAT)):
    t = 10.0 + b * BEAT
    put('drums', kick(1.0), t)
    if b % 2 == 1: put('drums', clap(), t, gain=0.8)
    put('drums', hat(), t, pan=-0.2, gain=0.3)
    put('drums', hat(open_=True), t + 0.25, pan=0.25, gain=0.35)
# UI ticks on the grid, blended low
for t, m in [(11.5, 88), (12.0, 93), (13.0, 85), (17.5, 88), (19.5, 81), (21.0, 85), (21.25, 88), (21.5, 93)]:
    put('sfx', tick(m), t, pan=0.15, gain=0.35)
for k in range(4):
    put('sfx', ring_tone(81), 15.5 + k * 0.25, pan=-0.15, gain=0.32)
put('sfx', ding(93), 16.75, pan=0.1, gain=0.3)   # answered from the scooter switch

# Cluster boot (12.0-12.8): gauge sweep tone up then down, in key (A3 -> A5 -> A3)
n = int(0.8 * SR); tt = np.arange(n) / SR
semis = np.where(tt < 0.4, 24 * (tt / 0.4), 24 * (1 - (tt - 0.4) / 0.4))
f = hz(57) * 2 ** (semis / 12)
sw = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * tt / 0.8) ** 1.5
put('sfx', sw * 0.22, 12.0, gain=0.6)
# Right indicator relay clicks while the display blinks (on the beat)
for k in range(4):
    put('sfx', tick(100, 0.03) * 0.8, 13.5 + k * 0.5, pan=0.45, gain=0.35)
    put('sfx', tick(95, 0.03) * 0.5, 13.75 + k * 0.5, pan=0.45, gain=0.3)

# Outro (22.5-25): crash + final chord rings out
put('fx', crash(), 22.5, gain=0.7)
put('fx', impact() * 0.6, 22.5, gain=0.6)
put('pad', pad([57, 61, 64, 69, 73], 2.0, a=0.01, bright=0.55, r=0.9), 22.5, gain=0.65)
put('bass', bass(33, 2.0), 22.5, gain=0.7)
for k, m in enumerate([81, 76, 73, 69]):
    put('arp', pluck(m, 0.6, 0.4), 22.5 + k * 0.25, pan=(-0.3 if k % 2 else 0.3), gain=0.3)
put('sfx', ding(88), 22.75, gain=0.35)

# ---------------- mix ----------------
# sidechain pump from the kick grid (10-22.5)
t = np.arange(N) / SR
duck = np.ones(N)
for b in range(int((22.5 - 10.0) / BEAT)):
    t0 = 10.0 + b * BEAT
    i0 = int(t0 * SR); i1 = min(N, i0 + int(0.4 * SR))
    tt = (np.arange(i1 - i0)) / SR
    duck[i0:i1] = np.minimum(duck[i0:i1], 1 - 0.45 * np.exp(-tt * 9))

def reverb(x, dur=1.6, mix=0.18):
    n = int(dur * SR); tt = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-tt * 3.5)
    ir = lp_fast(ir, 5000); ir /= np.sqrt((ir ** 2).sum())
    m = len(x) + n - 1; F = 1 << (m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, F) * np.fft.rfft(ir, F), F)[: len(x)]
    return x + mix * y

gains = {'drums': 0.9, 'bass': 0.85, 'pad': 0.7, 'arp': 0.75, 'sfx': 0.55, 'fx': 0.7}
mixL = np.zeros(N); mixR = np.zeros(N)
for k, g in gains.items():
    l, r = bus[k]
    if k in ('pad', 'arp', 'bass'):
        l, r = l * duck, r * duck
    if k in ('pad', 'arp', 'sfx', 'fx'):
        l, r = reverb(l, mix=0.22), reverb(r, mix=0.22)
    mixL += l * g; mixR += r * g
# proof section: gentle low-pass sweep on the music bed (19.5-21) then open up
fade = np.ones(N); i0 = int(24.4 * SR); fade[i0:] = np.linspace(1, 0, N - i0) ** 1.5
fade[: int(0.01 * SR)] = np.linspace(0, 1, int(0.01 * SR))
sec = np.interp(t, [0, 7.0, 7.9, 8.0, 10.0, 10.05, 22.4, 22.5, 25], [1.55, 1.55, 1.4, 1.5, 1.4, 1.0, 1.0, 1.45, 1.45])
mixL *= fade * sec; mixR *= fade * sec
peak = max(np.abs(mixL).max(), np.abs(mixR).max())
mixL = np.tanh(mixL / peak * 1.3) / np.tanh(1.3) * 0.89
mixR = np.tanh(mixR / peak * 1.3) / np.tanh(1.3) * 0.89
out = np.stack([mixL, mixR], 1)
pcm = (np.clip(out, -1, 1) * 32767).astype('<i2')
with wave.open(sys.argv[1] if len(sys.argv) > 1 else 'score.wav', 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print('ok', out.shape)
