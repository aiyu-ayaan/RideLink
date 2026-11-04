#!/bin/bash
# Concat the 4 segments, bake the poster in as frame 0 (replacing it, so duration and sync stay), mux the score.
set -e
cd "$(dirname "$0")"
printf "file 's%d.mp4'\n" 0 1 2 3 > seg/list.txt
ffmpeg -v error -y -f concat -safe 0 -i seg/list.txt -loop 1 -framerate 30 -i poster.png -i score.wav \
  -filter_complex "[1:v]scale=1440:2560,format=yuv420p[p];[0:v][p]overlay=0:0:enable='eq(n,0)':shortest=1,format=yuv420p,setsar=1[v]" \
  -map "[v]" -map 2:a -c:v libx264 -preset slow -crf 16 -profile:v high -level:v 5.1 -pix_fmt yuv420p \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 -r 30 \
  -c:a aac -b:a 256k -ar 48000 -shortest -movflags +faststart ../brag.mp4
ffprobe -v error -show_entries stream=codec_name,width,height,r_frame_rate,nb_frames,duration -show_entries format=duration,size,bit_rate -of default=nw=1 ../brag.mp4
