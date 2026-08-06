/*
 * site.js — runs on every page.
 *   1. theme toggle, persisted in localStorage
 *   2. mobile navigation
 *   3. email links assembled at runtime so the address is not in the HTML source
 *   4. table-of-contents highlighting on project pages
 *
 * No dependencies, no build step. Loaded with `defer`, so the DOM is ready.
 */
(function () {
  'use strict';

  /* ---------------------------------------------------------------- theme */
  var root = document.documentElement;
  var toggle = document.getElementById('theme-toggle');

  function systemPrefersDark() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  function currentTheme() {
    var set = root.getAttribute('data-theme');
    if (set === 'dark' || set === 'light') return set;
    return systemPrefersDark() ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    try { localStorage.setItem('theme', theme); } catch (e) { /* private mode */ }
    if (toggle) {
      toggle.setAttribute('aria-label',
        theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme');
    }
    // Keep the browser chrome in step with the page.
    var meta = document.querySelector('meta[name="theme-color"]:not([media])');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', theme === 'dark' ? '#0f1114' : '#fbfaf8');
  }

  if (toggle) {
    applyTheme(currentTheme());
    toggle.addEventListener('click', function () {
      applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
    });
  }

  /* ------------------------------------------------------------ mobile nav */
  var navToggle = document.querySelector('.nav-toggle');
  var navList = document.getElementById('nav-list');

  if (navToggle && navList) {
    navToggle.addEventListener('click', function () {
      var open = navList.classList.toggle('is-open');
      navToggle.setAttribute('aria-expanded', String(open));
    });
    navList.addEventListener('click', function (e) {
      if (e.target.tagName === 'A') {
        navList.classList.remove('is-open');
        navToggle.setAttribute('aria-expanded', 'false');
      }
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && navList.classList.contains('is-open')) {
        navList.classList.remove('is-open');
        navToggle.setAttribute('aria-expanded', 'false');
        navToggle.focus();
      }
    });
  }

  /* ---------------------------------------------------------------- email */
  // The address is split across two data attributes in the HTML and only joined
  // here, at runtime. Scrapers that read the raw HTML find nothing to harvest,
  // while anyone with a browser sees a normal mailto link.
  var emailLinks = document.querySelectorAll('.js-email');
  Array.prototype.forEach.call(emailLinks, function (link) {
    var user = link.getAttribute('data-user');
    var domain = link.getAttribute('data-domain');
    if (!user || !domain) return;
    var address = user + '@' + domain;
    link.setAttribute('href', 'mailto:' + address);
    // Fill in the visible text only where the markup deliberately left the link
    // empty (the contact page). Links that already say "Email me" or "Email"
    // keep their label — a full address reads badly inside a button.
    if (link.textContent.trim() === '') link.textContent = address;
  });

  /* ------------------------------------------------------------------ toc */
  var tocLinks = document.querySelectorAll('.toc-list a');
  if (tocLinks.length && 'IntersectionObserver' in window) {
    var byId = {};
    var sections = [];
    Array.prototype.forEach.call(tocLinks, function (link) {
      var id = link.getAttribute('href').slice(1);
      var section = document.getElementById(id);
      if (section) { byId[id] = link; sections.push(section); }
    });

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        Array.prototype.forEach.call(tocLinks, function (l) { l.classList.remove('is-current'); });
        var link = byId[entry.target.id];
        if (link) link.classList.add('is-current');
      });
    }, { rootMargin: '-20% 0px -70% 0px', threshold: 0 });

    sections.forEach(function (s) { observer.observe(s); });
  }
})();
