/*
 * lightbox.js — full-size image viewer for project figures and galleries.
 *
 * Uses the native <dialog> element, so focus trapping, Escape-to-close and the
 * backdrop come from the browser rather than from code. Arrow keys move between
 * images. Falls back to opening the image in a new tab if <dialog> is unsupported.
 */
(function () {
  'use strict';

  var dialog = document.getElementById('lightbox');
  var triggers = Array.prototype.slice.call(document.querySelectorAll('.lightbox-trigger'));
  if (!triggers.length) return;

  var supported = dialog && typeof dialog.showModal === 'function';
  if (!supported) {
    triggers.forEach(function (t) {
      t.addEventListener('click', function () {
        window.open(t.getAttribute('data-src'), '_blank', 'noopener');
      });
    });
    return;
  }

  var img = document.getElementById('lightbox-image');
  var caption = document.getElementById('lightbox-caption');
  var closeBtn = document.getElementById('lightbox-close');
  var prevBtn = document.getElementById('lightbox-prev');
  var nextBtn = document.getElementById('lightbox-next');
  var current = 0;
  var lastFocused = null;

  function show(i) {
    current = (i + triggers.length) % triggers.length;
    var t = triggers[current];
    img.setAttribute('src', t.getAttribute('data-src'));
    img.setAttribute('alt', t.getAttribute('data-alt') || '');
    var text = t.getAttribute('data-caption') || '';
    caption.textContent = text;
    caption.hidden = text === '';

    var many = triggers.length > 1;
    prevBtn.hidden = !many;
    nextBtn.hidden = !many;
  }

  function open(i) {
    lastFocused = document.activeElement;
    show(i);
    dialog.showModal();
  }

  triggers.forEach(function (t, i) {
    t.addEventListener('click', function () { open(i); });
  });

  closeBtn.addEventListener('click', function () { dialog.close(); });
  prevBtn.addEventListener('click', function () { show(current - 1); });
  nextBtn.addEventListener('click', function () { show(current + 1); });

  dialog.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowRight') { e.preventDefault(); show(current + 1); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); show(current - 1); }
  });

  // Clicking the backdrop closes. The <figure> covers the image itself, so a
  // click that lands on the dialog and not on the figure is a backdrop click.
  dialog.addEventListener('click', function (e) {
    if (e.target === dialog) dialog.close();
  });

  dialog.addEventListener('close', function () {
    // Release the image so a large file is not held in memory.
    img.setAttribute('src', '');
    if (lastFocused && typeof lastFocused.focus === 'function') lastFocused.focus();
  });
})();
