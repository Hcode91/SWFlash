'use strict';
let player, currentName, paused = false;
const $ = id => document.getElementById(id);
function status(message) { $('status').textContent = message; }
window.openMovie = async name => {
  setSpeed(1);
  currentName = name;
  $('name').textContent = name;
  status('Loading…');
  try {
    if (!window.RufflePlayer?.newest) throw new Error('Ruffle runtime is missing. Bundle the runtime before building.');
    if (player) player.remove();
    player = window.RufflePlayer.newest().createPlayer();
    $('stage').replaceChildren(player);
    await player.ruffle().load({url:'/movie.swf',allowScriptAccess:false,allowNetworking:'none'});
    applySpeedAudio();
    paused = false;
    $('pause').textContent = 'Pause';
    status('Loaded. Tap the movie to start playback and sound.');
  } catch (error) { status('Unable to play this SWF. ' + error.message); }
};
$('open').onclick = () => window.Android ? window.Android.openFile() : status('Open this player in the Android app.');
window.pauseMovie = () => { if (player) { const api = player.ruffle();
    if (api.suspend) api.suspend(); else api.pause(); paused = true; $('pause').textContent = 'Resume'; } };
$('pause').onclick = () => { if (!player) return; if (paused) { const api = player.ruffle(); if (api.resume) api.resume(); else api.play(); paused = false; $('pause').textContent = 'Pause'; } else window.pauseMovie(); };
$('restart').onclick = () => { if (currentName) window.openMovie(currentName); };
$('full').onclick = async () => { try { if (document.fullscreenElement) await document.exitFullscreen(); else await $('screen').requestFullscreen(); } catch (_) { status('Fullscreen is unavailable on this device.'); } };
function toggleControls() {
  const visible = $('controls').hidden;
  $('controls').hidden = !visible;
  $('toggle').setAttribute('aria-pressed', String(visible));
  $('fullscreen-toggle').setAttribute('aria-pressed', String(visible));
  $('fullscreen-toggle').textContent = visible ? 'Hide controls' : 'Show controls';
  if (!visible) for (const code of Object.keys(codes)) sendKey('keyup', code);
}
$('toggle').onclick = toggleControls;
$('fullscreen-toggle').onclick = toggleControls;
$('fullscreen-exit').onclick = () => document.exitFullscreen().catch(() => status('Unable to exit fullscreen.'));

let savedVolume = null;
function applySpeedAudio() {
  if (!player) return;
  const api = player.ruffle();
  if (window.PlaybackClock.rate > 1) {
    if (savedVolume === null) savedVolume = api.volume;
    api.volume = 0;
  } else if (savedVolume !== null) {
    api.volume = savedVolume;
    savedVolume = null;
  }
}
function setSpeed(rate) {
  window.PlaybackClock.setRate(rate);
  for (const id of ['speed', 'fullscreen-speed']) {
    $(id).textContent = 'Speed: ' + rate + '×';
    $(id).setAttribute('aria-label', 'Playback speed ' + rate + ' times. Tap to change.');
  }
  applySpeedAudio();
}
function cycleSpeed() {
  const rates = [1, 2, 4];
  const rate = rates[(rates.indexOf(window.PlaybackClock.rate) + 1) % rates.length];
  setSpeed(rate);
  status(rate === 1 ? 'Normal speed. Audio restored.' : rate + '× fast forward. Audio muted.');
}
$('speed').onclick = cycleSpeed;
$('fullscreen-speed').onclick = cycleSpeed;

const codes = {ArrowUp:38,ArrowDown:40,ArrowLeft:37,ArrowRight:39,Space:32,Enter:13,KeyZ:90,KeyX:88};
function sendKey(type, code) {
  if (!player) return;
  player.focus();
  // Ruffle receives keyboard events at the focused player and document.
  player.dispatchEvent(new KeyboardEvent(type,{key:code==='Space'?' ':code.startsWith('Key')?code.slice(3).toLowerCase():code,code,keyCode:codes[code],which:codes[code],bubbles:true,cancelable:true}));
}
for (const button of document.querySelectorAll('[data-key]')) {
  button.onpointerdown = e => { e.preventDefault(); button.setPointerCapture(e.pointerId); sendKey('keydown',button.dataset.key); };
  button.onpointerup = button.onpointercancel = () => sendKey('keyup',button.dataset.key);
}
