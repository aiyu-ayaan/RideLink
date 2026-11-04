# RideLink launch video — source

`video.html` draws every frame as a pure function of time (`render(t)`), using the app's real dark colour
scheme, brand mark, splash choreography, BLE frames and an NTORQ-style TFT cluster mock (no TVS logos or assets).
`score.py` synthesises the 120 BPM soundtrack (numpy only).

Rebuild (Node 22, Playwright Chromium, ffmpeg, Python 3 + numpy):

```bash
cd brag-output/work
npm i @fontsource-variable/roboto-flex material-symbols@0.27.0
mkdir -p fonts seg && cp node_modules/@fontsource-variable/roboto-flex/files/roboto-flex-latin-full-normal.woff2 fonts/roboto-flex.woff2 && cp node_modules/material-symbols/material-symbols-rounded.woff2 fonts/ms-rounded.woff2
python3 score.py score.wav
node render.js 0 188 seg/s0.mp4 & node render.js 188 376 seg/s1.mp4 & node render.js 376 564 seg/s2.mp4 & node render.js 564 750 seg/s3.mp4 & wait
# poster.png: render(14.5) at 1440x2560, then:
./finish.sh
```
