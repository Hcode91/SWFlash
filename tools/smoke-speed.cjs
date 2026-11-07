// Real Ruffle/WASM check: count ActionScript traces from a generated 10 FPS SWF.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const assets = path.join(__dirname, '../app/src/main/assets/player');
function movie() {
  const u16 = n => {const b=Buffer.alloc(2);b.writeUInt16LE(n);return b};
  const tag = (id,data=Buffer.alloc(0)) => {
    if(data.length<63)return Buffer.concat([u16((id<<6)|data.length),data]);
    const size=Buffer.alloc(4);size.writeUInt32LE(data.length);return Buffer.concat([u16((id<<6)|63),size,data]);
  };
  const bits='01100'+[0,2000,0,2000].map(n=>n.toString(2).padStart(12,'0')).join('');
  const rect=Buffer.from(bits.padEnd(Math.ceil(bits.length/8)*8,'0').match(/.{8}/g).map(b=>parseInt(b,2)));
  const chunks=[rect,u16(10*256),u16(250)];
  for(let i=0;i<250;i++){
    const str=Buffer.from(String(i)+'\0');
    const actions=Buffer.concat([Buffer.from([0x96]),u16(str.length+1),Buffer.from([0]),str,Buffer.from([0x26,0])]);
    chunks.push(tag(12,actions),tag(1));
  }
  chunks.push(tag(0));const body=Buffer.concat(chunks);
  const header=Buffer.from([70,87,83,7,0,0,0,0]);header.writeUInt32LE(body.length+8,4);
  return Buffer.concat([header,body]);
}
(async()=>{
 const swf=movie();
 const server=http.createServer((req,res)=>{
  if(req.url==='/movie.swf'){res.setHeader('Content-Type','application/x-shockwave-flash');return res.end(swf)}
  const file=path.join(assets,req.url==='/'?'index.html':req.url.split('?')[0]);
  const type=file.endsWith('.wasm')?'application/wasm':file.endsWith('.js')?'application/javascript':'text/html';
  fs.readFile(file,(err,data)=>{res.writeHead(err?404:200,{'Content-Type':type});res.end(err?'Not found':data)});
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 let browser;
 try {
  browser=await chromium.launch({args:['--enable-unsafe-swiftshader']});
  const page=await browser.newPage({locale:'en-US'});
  page.on('pageerror',error=>console.error('Browser:',error.stack));
  await page.goto('http://127.0.0.1:'+server.address().port);
  await page.waitForFunction(()=>window.RufflePlayer?.newest);
  await page.evaluate(async()=>{
    await window.openMovie('speed-test.swf');
    window.testPlayer=document.querySelector('ruffle-player');window.traces=[];
    if (!window.testPlayer || typeof window.testPlayer.ruffle !== 'function') throw new Error(document.getElementById('status').textContent);
    window.testPlayer.ruffle().traceObserver=message=>window.traces.push(message);
  });
  await page.locator('ruffle-player').click();
  await page.waitForFunction(()=>window.traces.length>=5,{},{timeout:30000});
  const count=()=>page.evaluate(()=>window.traces.length);
  let before=await count();await page.waitForTimeout(1200);const normal=(await count())-before;
  await page.locator('#speed').click(); // 2x
  await page.locator('#speed').click(); // 4x
  before=await count();await page.waitForTimeout(1200);const fast=(await count())-before;
  assert(normal>=6,'Normal playback must advance');assert(fast>normal*2,'Fast forward must advance substantially faster');
  assert(await page.evaluate(()=>window.testPlayer===document.querySelector('ruffle-player')),'Player must not be replaced');
  const frames=await page.evaluate(()=>window.traces.map(Number));
  assert(frames.every((frame,i)=>i===0||frame>frames[i-1]),'Timeline must not restart');
  await page.locator('#speed').click();
  assert.equal(await page.evaluate(()=>window.PlaybackClock.rate),1);
  console.log({normalFrames:normal,fastFrames:fast,samePlayer:true,continuousTimeline:true});
 } finally {if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(error=>{console.error(error);process.exitCode=1});
