// Renders frames [a,b) of video.html at 1440x2560 and pipes them to ffmpeg as a near-lossless segment.
const {chromium}=require('playwright');const {spawn}=require('child_process');const path=require('path');
const FPS=30;const [a,b,out]=[+process.argv[2],+process.argv[3],process.argv[4]];
(async()=>{
  const ff=spawn('ffmpeg',['-v','error','-y','-f','image2pipe','-framerate',String(FPS),'-c:v','png','-i','-','-c:v','libx264','-preset','medium','-crf','8','-pix_fmt','yuv420p','-r',String(FPS),out],{stdio:['pipe','inherit','inherit']});
  const br=await chromium.launch();const p=await br.newPage({viewport:{width:720,height:1280},deviceScaleFactor:2});
  p.on('pageerror',e=>console.error('ERR',e.message));
  await p.goto('file://'+path.resolve(__dirname,'video.html'));await p.evaluate(()=>window.ready);
  for(let f=a;f<b;f++){await p.evaluate(t=>render(t),f/FPS);const buf=await p.screenshot({type:'png'});if(!ff.stdin.write(buf))await new Promise(r=>ff.stdin.once('drain',r));if(f%60==0)console.log(out,f)}
  await br.close();ff.stdin.end();await new Promise(r=>ff.on('close',r));console.log('done',out);
})();
