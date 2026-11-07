'use strict';
// Ruffle advances its core using differences between animation-frame timestamps.
// Scale that clock continuously; never reload or replace the running player.
// Install before Ruffle so WASM binds to this scheduler.
(() => {
  const nativeRequest = window.requestAnimationFrame.bind(window);
  let rate = 1, lastReal = null, virtualTime = 0;
  window.requestAnimationFrame = callback => nativeRequest(realTime => {
    if (lastReal === null) { if (virtualTime === 0) virtualTime = realTime; }
    else virtualTime += Math.max(0, realTime - lastReal) * rate;
    lastReal = realTime;
    callback(virtualTime);
  });
  // Resume without fast-forwarding time spent outside the app.
  document.addEventListener('visibilitychange', () => { lastReal = null; });
  window.PlaybackClock = {
    get rate() { return rate; },
    setRate(value) {
      if (![1, 2, 4].includes(value)) throw new RangeError('Unsupported playback speed');
      rate = value;
    }
  };
})();
