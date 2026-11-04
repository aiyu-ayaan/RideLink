"""Soundtrack for the RideLink brag video: 64.8 s, 100 BPM, A minor -> A major lift at the reveal.
Every part sits on one grid counted from frame 0 (beat = 0.5 s). Writes work/score.wav (48 kHz stereo)."""
import numpy as np, sys, wave

SR = 48000
DUR = 64.8
N = int(SR * DUR)
BEAT = 0.6
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


# ---------------- arrangement (100 BPM, beat 0.6 s, bar 2.4 s) ----------------
B = BEAT
# Act 1 (0-12): A minor, sparse and soft
for t0, ch, root, d in [(0.0, [57, 60, 64], 33, 4.8), (4.8, [53, 57, 60], 29, 2.4), (7.2, [50, 53, 57], 26, 2.4), (9.6, [52, 56, 59], 28, 2.4)]:
    put('pad', pad(ch, d, a=0.4, bright=0.15, r=0.5), t0, gain=0.6)
    for k in range(int(d / (2 * B))):
        put('bass', bass(root + 12, 0.9), t0 + k * 2 * B, gain=0.55)
for b in range(int(12.0 / B)):
    t0 = b * B
    put('drums', hat(), t0 + B / 2, pan=0.3, gain=0.35)
    if t0 >= 4.8 and b % 2 == 0: put('drums', kick(0.45), t0)
# rating drop: soft descending glide (0.9 -> 2.7)
n = int(1.8 * SR); tt = np.arange(n) / SR
f = hz(76) * 2 ** (-(7 / 12) * (tt / 1.8))
put('sfx', np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * .9) * 0.22, 0.9, pan=-0.1)
for i, t0 in enumerate([5.4, 6.6, 7.8, 9.0]):
    put('sfx', blip(76 - i, 72 - i), t0, pan=(-0.25 if i % 2 else 0.25), gain=0.28)
put('fx', riser(1.2), 10.8, gain=0.35)

# Turn (12-16.8): impact + A major, the splash spark
put('fx', impact(), 12.0, gain=0.7)
put('fx', crash(), 12.0, gain=0.6)
put('pad', pad([57, 61, 64, 69], 4.6, a=0.05, bright=0.5, r=0.8), 12.0, gain=0.65)
put('bass', bass(33, 4.6), 12.0, gain=0.7)
put('sfx', ding(88), 13.2, pan=0.2, gain=0.4)
for k, m in enumerate([69, 73, 76, 81, 76, 73, 76, 81, 69, 73, 76, 81]):
    put('arp', pluck(m, 0.3, 0.5), 13.8 + k * B / 2, pan=(-0.3 if k % 2 else 0.3), gain=0.28)
put('fx', riser(0.6), 16.2, gain=0.25)

# Groove (16.8-52.8): A - F#m - D - E, one bar each; relaxed, builds at 22.8
prog = [([57, 61, 64], 33), ([54, 57, 61], 30), ([50, 54, 57], 26), ([52, 56, 59], 28)]
for bar in range(15):
    t0 = 16.8 + bar * 4 * B
    ch, root = prog[bar % 4]
    full = t0 >= 22.8
    put('pad', pad(ch, 4 * B, a=0.08, bright=0.4, r=0.4), t0, gain=0.5)
    for k in range(8):  # eighth-note bass, softer before the build
        put('bass', bass(root + 12 + (12 if k == 7 else 0), 0.26), t0 + k * B / 2, gain=0.7 if full else 0.5)
    arp = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24]
    for k in range(16 if full else 8):
        step = B / 4 if full else B / 2
        put('arp', pluck(arp[k % 4], 0.22, 0.7), t0 + k * step, pan=(-0.35 if k % 2 else 0.35), gain=0.18 if full else 0.2)
    for k in range(4):
        tb = t0 + k * B
        put('drums', kick(0.9 if full else 0.6), tb)
        if full and k % 2 == 1: put('drums', clap(), tb, gain=0.6)
        put('drums', hat(), tb + B / 2, pan=0.25, gain=0.3)
        if full: put('drums', hat(), tb, pan=-0.2, gain=0.18)
# UI sounds, on the grid and low
for t0, m in [(18.6, 88), (23.4, 85), (27.6, 88), (34.8, 81), (36.0, 85), (41.4, 88), (48.6, 93), (49.2, 85)]:
    put('sfx', tick(m), t0, pan=0.15, gain=0.3)
# cluster boot: gauge sweep up and back (19.8-21.0), in key
n = int(1.2 * SR); tt = np.arange(n) / SR
semis = np.where(tt < 0.6, 24 * (tt / 0.6), 24 * (1 - (tt - 0.6) / 0.6))
sw = np.sin(2 * np.pi * np.cumsum(hz(57) * 2 ** (semis / 12)) / SR) * np.sin(np.pi * tt / 1.2) ** 1.5
put('sfx', sw * 0.2, 19.8, gain=0.6)
put('sfx', ding(81), 21.0, gain=0.3)
# right indicator relay: on/off every half beat (25.2-27.6)
for k in range(4):
    put('sfx', tick(100, 0.03) * 0.8, 25.2 + k * B, pan=0.45, gain=0.3)
    put('sfx', tick(95, 0.03) * 0.5, 25.5 + k * B, pan=0.45, gain=0.25)
# call: ring pairs on the grid until answered (30.0-32.4), answered chime
for k in range(4):
    put('sfx', ring_tone(81), 30.0 + k * 2 * B, pan=-0.15, gain=0.3)
    put('sfx', ring_tone(81), 30.3 + k * 2 * B, pan=-0.15, gain=0.3)
put('sfx', ding(93), 32.4, pan=0.1, gain=0.3)

# Proof (52.8-57.6): groove thins to kick, bass and arps
for bar in range(2):
    t0 = 52.8 + bar * 4 * B
    ch, root = prog[bar % 4]
    put('pad', pad(ch, 4 * B, a=0.1, bright=0.25, r=0.4), t0, gain=0.45)
    for k in range(8): put('bass', bass(root + 12, 0.26), t0 + k * B / 2, gain=0.55)
    for k in range(4): put('drums', kick(0.6), t0 + k * B); put('drums', hat(), t0 + k * B + B / 2, gain=0.25)
    arp = [ch[0] + 24, ch[2] + 12, ch[1] + 24, ch[2] + 12]
    for k in range(16): put('arp', pluck(arp[k % 4], 0.15, 1.2), t0 + k * B / 4, pan=(-0.4 if k % 2 else 0.4), gain=0.12)
for i in range(3): put('sfx', tick(85 + 3 * i), 55.2 + i * B / 2, gain=0.3)
put('fx', riser(0.6), 57.0, gain=0.25)

# Outro (57.6-64.8): crash, long A major chord, slow arp, rings out
put('fx', crash(), 57.6, gain=0.55)
put('fx', impact() * 0.5, 57.6, gain=0.5)
put('pad', pad([57, 61, 64, 69, 73], 5.0, a=0.05, bright=0.45, r=2.2), 57.6, gain=0.6)
put('bass', bass(33, 4.8), 57.6, gain=0.6)
for k, m in enumerate([81, 76, 73, 69, 73, 76, 81, 85, 81, 76, 73, 69]):
    put('arp', pluck(m, 0.8, 0.35), 58.2 + k * B, pan=(-0.3 if k % 2 else 0.3), gain=0.22)
put('sfx', ding(88), 58.2, gain=0.3)

# ---------------- mix ----------------
t = np.arange(N) / SR
duck = np.ones(N)
for b in range(int((52.8 - 22.8) / B)):
    i0 = int((22.8 + b * B) * SR); i1 = min(N, i0 + int(0.45 * SR))
    tt = np.arange(i1 - i0) / SR
    duck[i0:i1] = np.minimum(duck[i0:i1], 1 - 0.35 * np.exp(-tt * 8))

def reverb(x, dur=2.2, mix=0.2):
    n = int(dur * SR); tt = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-tt * 2.8)
    ir = lp_fast(ir, 4500); ir /= np.sqrt((ir ** 2).sum())
    m = len(x) + n - 1; F = 1 << (m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, F) * np.fft.rfft(ir, F), F)[: len(x)]
    return x + mix * y

gains = {'drums': 0.8, 'bass': 0.85, 'pad': 0.75, 'arp': 0.75, 'sfx': 0.55, 'fx': 0.65}
mixL = np.zeros(N); mixR = np.zeros(N)
for k, g in gains.items():
    l, r = bus[k]
    if k in ('pad', 'arp', 'bass'): l, r = l * duck, r * duck
    if k in ('pad', 'arp', 'sfx', 'fx'): l, r = reverb(l, mix=0.25), reverb(r, mix=0.25)
    mixL += l * g; mixR += r * g
fade = np.ones(N); i0 = int(63.0 * SR); fade[i0:] = np.linspace(1, 0, N - i0) ** 1.6
fade[: int(0.01 * SR)] = np.linspace(0, 1, int(0.01 * SR))
sec = np.interp(t, [0, 11.9, 12.0, 16.8, 22.7, 22.8, 52.7, 52.8, 57.5, 57.6, 64.8], [1.5, 1.5, 1.35, 1.35, 1.3, 1.0, 1.0, 1.25, 1.25, 1.35, 1.35])
mixL *= fade * sec; mixR *= fade * sec
peak = max(np.abs(mixL).max(), np.abs(mixR).max())
mixL = np.tanh(mixL / peak * 1.3) / np.tanh(1.3) * 0.89
mixR = np.tanh(mixR / peak * 1.3) / np.tanh(1.3) * 0.89
out = np.stack([mixL, mixR], 1)
pcm = (np.clip(out, -1, 1) * 32767).astype('<i2')
with wave.open(sys.argv[1] if len(sys.argv) > 1 else 'score.wav', 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print('ok', out.shape)
