const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const assets = path.join(__dirname, '../app/src/main/assets/player');
function setup() {
  const queue = [], listeners = {}, elements = {};
  const stats = {created:0,loaded:0,removed:0,suspended:0};
  const api = {volume:0.7,load:async()=>{stats.loaded++},suspend:()=>{stats.suspended++},resume:()=>{stats.suspended--}};
  const player = {ruffle:()=>api,remove:()=>{stats.removed++}};
  for (const id of ['name','status','stage','open','pause','restart','full','screen','toggle','fullscreen-toggle','fullscreen-exit','controls','speed','fullscreen-speed']) elements[id] = {textContent:'',hidden:id==='controls',setAttribute(k,v){this[k]=v},replaceChildren(){}};
  const document = {getElementById:id=>elements[id],querySelectorAll:()=>[],addEventListener:(name,fn)=>{listeners[name]=fn}};
  const window = {requestAnimationFrame:fn=>{queue.push(fn);return queue.length},RufflePlayer:{newest:()=>({createPlayer:()=>{stats.created++;return player}})}};
  const context = vm.createContext({window,document});
  vm.runInContext(fs.readFileSync(path.join(assets,'speed-clock.js'),'utf8'),context);
  const frame = real => {let result;window.requestAnimationFrame(time=>{result=time});queue.shift()(real);return result};
  return {window,context,elements,stats,api,frame,listeners};
}
test('clock changes speed continuously and resumes without background catch-up',()=>{
 const s=setup(); assert.equal(s.frame(100),100);
 s.window.PlaybackClock.setRate(2); assert.equal(s.frame(110),120);
 s.window.PlaybackClock.setRate(4); assert.equal(s.frame(120),160);
 s.window.PlaybackClock.setRate(1); assert.equal(s.frame(130),170);
 s.listeners.visibilitychange();assert.equal(s.frame(10000),170);assert.equal(s.frame(10010),180);
 assert.throws(()=>s.window.PlaybackClock.setRate(3));
});
test('fullscreen and normal speed buttons keep the same loaded game and restore audio',async()=>{
 const s=setup();vm.runInContext(fs.readFileSync(path.join(assets,'app.js'),'utf8'),s.context);
 await s.window.openMovie('game.swf');assert.equal(s.stats.loaded,1);
 s.elements.speed.onclick();assert.equal(s.window.PlaybackClock.rate,2);assert.equal(s.api.volume,0);
 assert.equal(s.elements['fullscreen-speed'].textContent,'Speed: 2×');
 s.elements['fullscreen-speed'].onclick();assert.equal(s.window.PlaybackClock.rate,4);
 s.elements.pause.onclick();assert.equal(s.stats.suspended,1);
 s.elements['fullscreen-speed'].onclick();assert.equal(s.window.PlaybackClock.rate,1);assert.equal(s.api.volume,0.7);
 assert.equal(s.stats.suspended,1);s.elements.pause.onclick();assert.equal(s.stats.suspended,0);
 assert.equal(s.stats.created,1);assert.equal(s.stats.loaded,1);assert.equal(s.stats.removed,0);
});
