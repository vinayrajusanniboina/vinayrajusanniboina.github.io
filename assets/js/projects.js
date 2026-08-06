/*
 * projects.js — tag filtering and client-side search on /projects/.
 *
 * The cards are already in the HTML, so the page works with JavaScript disabled;
 * this only adds filtering. Full-text search fetches /search-index.json once, on
 * the first keystroke, so the page load is unaffected.
 */
(function () {
  'use strict';

  var grid = document.getElementById('project-grid');
  if (!grid) return;

  var searchInput = document.getElementById('project-search');
  var statusEl = document.getElementById('filter-status');
  var emptyEl = document.getElementById('empty-state');
  var clearBtn = document.getElementById('clear-filters');
  var chips = document.querySelectorAll('.chip');
  var cards = Array.prototype.slice.call(grid.querySelectorAll('.card'));

  var activeTag = '';
  var query = '';
  var index = null;          // slug -> searchable text, loaded lazily
  var indexLoading = false;

  /* ------------------------------------------------------------- indexing */
  function loadIndex() {
    if (index || indexLoading) return Promise.resolve(index);
    indexLoading = true;
    return fetch('/search-index.json')
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (records) {
        index = {};
        records.forEach(function (r) {
          index[r.slug] = [
            r.title, r.subtitle, r.summary, r.body,
            (r.tags || []).join(' '), (r.tools || []).join(' '), r.year
          ].join(' ').toLowerCase();
        });
        indexLoading = false;
        return index;
      })
      .catch(function () {
        // Search index unavailable: fall back to matching the card text we have.
        index = {};
        cards.forEach(function (card) {
          index[card.getAttribute('data-slug')] = card.textContent.toLowerCase();
        });
        indexLoading = false;
        return index;
      });
  }

  /* ------------------------------------------------------------ filtering */
  function matches(card) {
    var tags = (card.getAttribute('data-tags') || '').split('|');
    if (activeTag && tags.indexOf(activeTag) === -1) return false;
    if (!query) return true;

    var slug = card.getAttribute('data-slug');
    var haystack = (index && index[slug]) || card.textContent.toLowerCase();

    // Every whitespace-separated term must appear somewhere. Simple, predictable,
    // and good enough for a portfolio-sized corpus.
    return query.split(/\s+/).every(function (term) {
      return term === '' || haystack.indexOf(term) !== -1;
    });
  }

  function apply() {
    var shown = 0;
    cards.forEach(function (card) {
      var ok = matches(card);
      card.hidden = !ok;
      if (ok) shown++;
    });

    if (statusEl) {
      var parts = [shown + (shown === 1 ? ' project' : ' projects')];
      if (activeTag) parts.push('tagged ' + activeTag);
      if (query) parts.push('matching "' + query + '"');
      statusEl.textContent = parts.join(' ');
    }
    if (emptyEl) emptyEl.hidden = shown !== 0;

    // Keep the URL shareable without adding a history entry per keystroke.
    var params = new URLSearchParams();
    if (activeTag) params.set('tag', activeTag);
    if (query) params.set('q', query);
    var qs = params.toString();
    history.replaceState(null, '', qs ? '?' + qs : location.pathname);
  }

  /* --------------------------------------------------------------- events */
  Array.prototype.forEach.call(chips, function (chip) {
    chip.addEventListener('click', function () {
      var tag = chip.getAttribute('data-tag') || '';
      activeTag = (tag === activeTag) ? '' : tag;
      Array.prototype.forEach.call(chips, function (c) {
        var on = (c.getAttribute('data-tag') || '') === activeTag;
        c.classList.toggle('is-active', on);
        c.setAttribute('aria-pressed', String(on));
      });
      apply();
    });
  });

  if (searchInput) {
    var timer = null;
    searchInput.addEventListener('input', function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        query = searchInput.value.trim().toLowerCase();
        if (query) {
          loadIndex().then(apply);
        } else {
          apply();
        }
      }, 120);
    });

    // "/" focuses search, the way documentation sites behave.
    document.addEventListener('keydown', function (e) {
      if (e.key === '/' && document.activeElement !== searchInput) {
        e.preventDefault();
        searchInput.focus();
      }
      if (e.key === 'Escape' && document.activeElement === searchInput) {
        searchInput.value = '';
        query = '';
        apply();
      }
    });
  }

  if (clearBtn) {
    clearBtn.addEventListener('click', function () {
      activeTag = '';
      query = '';
      if (searchInput) searchInput.value = '';
      Array.prototype.forEach.call(chips, function (c) {
        var on = (c.getAttribute('data-tag') || '') === '';
        c.classList.toggle('is-active', on);
        c.setAttribute('aria-pressed', String(on));
      });
      apply();
    });
  }

  /* ---------------------------------------------- restore state from a URL */
  var initial = new URLSearchParams(location.search);
  var initialTag = initial.get('tag');
  var initialQuery = initial.get('q');
  if (initialTag || initialQuery) {
    if (initialTag) {
      activeTag = initialTag;
      Array.prototype.forEach.call(chips, function (c) {
        var on = (c.getAttribute('data-tag') || '') === activeTag;
        c.classList.toggle('is-active', on);
        c.setAttribute('aria-pressed', String(on));
      });
    }
    if (initialQuery && searchInput) {
      searchInput.value = initialQuery;
      query = initialQuery.toLowerCase();
      loadIndex().then(apply);
      return;
    }
    apply();
  }
})();
