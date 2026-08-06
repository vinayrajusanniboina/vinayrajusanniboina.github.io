/*
 * math.js — typesets the .math elements written by the project template.
 *
 * The content files hold plain TeX with no delimiters, and the template emits it
 * as element text. That means:
 *   - nothing has to be escaped through YAML, Java and HTML in turn;
 *   - if every script fails to load, the page still shows readable TeX in a
 *     monospace face rather than a blank space.
 *
 * KaTeX first, because it is much faster and does not reflow the page. MathJax is
 * loaded only if KaTeX fails to arrive (blocked CDN, offline, corporate proxy).
 */
(function () {
  'use strict';

  var nodes = Array.prototype.slice.call(document.querySelectorAll('.math'));
  if (!nodes.length) return;

  var KATEX_JS = 'https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js';
  var KATEX_SRI = 'sha384-7zkQWkzuo3B5mTepMUcHkMB5jZaolc2xDwL6VFqjFALcbeS9Ggm/Yr2r3Dy4lfFg';
  var MATHJAX_JS = 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js';

  function renderWithKatex() {
    nodes.forEach(function (el) {
      var tex = el.textContent;
      try {
        window.katex.render(tex, el, {
          displayMode: el.classList.contains('math-display'),
          throwOnError: false,
          output: 'html',
          strict: 'ignore'
        });
        el.classList.add('katex-done');
      } catch (e) {
        // Leave the raw TeX in place — still readable.
      }
    });
  }

  function renderWithMathJax() {
    // MathJax needs delimiters, so add them now that we know it is the renderer.
    nodes.forEach(function (el) {
      var display = el.classList.contains('math-display');
      el.textContent = (display ? '\\[' : '\\(') + el.textContent + (display ? '\\]' : '\\)');
    });
    window.MathJax = {
      tex: { inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]']] },
      options: { skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre'] }
    };
    var s = document.createElement('script');
    s.src = MATHJAX_JS;
    s.async = true;
    document.head.appendChild(s);
  }

  var katexScript = document.createElement('script');
  katexScript.src = KATEX_JS;
  katexScript.integrity = KATEX_SRI;
  katexScript.crossOrigin = 'anonymous';
  katexScript.async = true;
  katexScript.addEventListener('load', function () {
    if (window.katex) renderWithKatex();
    else renderWithMathJax();
  });
  katexScript.addEventListener('error', renderWithMathJax);
  document.head.appendChild(katexScript);

  // Belt and braces: if KaTeX has neither loaded nor errored within 4 seconds
  // (a hung CDN request rather than a failed one), switch to MathJax.
  setTimeout(function () {
    if (!window.katex && !window.MathJax) renderWithMathJax();
  }, 4000);
})();
