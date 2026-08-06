/*
 * viewer.js — optional interactive 3D model viewer.
 *
 * Design constraints that shaped this file:
 *   1. Nothing loads until the visitor presses the button. A 600 kB 3D library and
 *      a multi-megabyte mesh must never be on the critical path of a portfolio page.
 *   2. If anything fails — CDN blocked, WebGL unavailable, corrupt file — the poster
 *      image stays on screen and the download link in the table still works.
 *   3. STL only. SOLIDWORKS and ANSYS both export STL directly, it is a single
 *      self-contained file, and it can be parsed here in 40 lines, which means the
 *      viewer needs the three.js core module and nothing else. Loading the official
 *      STLLoader/OrbitControls addons would drag in bare-specifier imports and an
 *      import map, which is a lot of fragility for no extra capability.
 *      Export from SOLIDWORKS: File > Save As > STL, coarse resolution.
 */
(function () {
  'use strict';

  var THREE_URL = 'https://cdn.jsdelivr.net/npm/three@0.169.0/build/three.module.js';

  var container = document.getElementById('model-viewer');
  var button = document.getElementById('viewer-load');
  if (!container || !button) return;

  var modelUrl = container.getAttribute('data-model');
  if (!modelUrl) return;

  button.addEventListener('click', function start() {
    button.removeEventListener('click', start);
    button.disabled = true;
    button.textContent = 'Loading…';

    if (!hasWebGL()) {
      fail('This browser cannot display 3D content. Download the file instead.');
      return;
    }

    Promise.all([
      import(THREE_URL),
      fetch(modelUrl).then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.arrayBuffer();
      })
    ]).then(function (results) {
      build(results[0], results[1]);
    }).catch(function (err) {
      fail('Could not load the 3D view (' + err.message + '). The download link still works.');
    });
  });

  function fail(message) {
    button.hidden = true;
    var note = document.createElement('p');
    note.className = 'viewer-note';
    note.setAttribute('role', 'status');
    note.textContent = message;
    container.appendChild(note);
  }

  function hasWebGL() {
    try {
      var canvas = document.createElement('canvas');
      return !!(window.WebGLRenderingContext &&
        (canvas.getContext('webgl2') || canvas.getContext('webgl')));
    } catch (e) {
      return false;
    }
  }

  /* --------------------------------------------------------------- scene */
  function build(THREE, buffer) {
    var geometry = parseStl(THREE, buffer);
    geometry.computeVertexNormals();
    geometry.computeBoundingSphere();
    geometry.center();

    var dark = document.documentElement.getAttribute('data-theme') === 'dark' ||
      (!document.documentElement.getAttribute('data-theme') &&
        window.matchMedia('(prefers-color-scheme: dark)').matches);

    var width = container.clientWidth;
    var height = Math.min(Math.round(width * 0.62), 520);

    var scene = new THREE.Scene();
    scene.background = new THREE.Color(dark ? 0x1d2126 : 0xf7f5f2);

    var radius = geometry.boundingSphere.radius || 1;
    var camera = new THREE.PerspectiveCamera(45, width / height, radius / 100, radius * 100);
    camera.position.set(radius * 1.8, radius * 1.2, radius * 2.0);

    scene.add(new THREE.HemisphereLight(0xffffff, dark ? 0x101418 : 0x9099a2, 1.6));
    var key = new THREE.DirectionalLight(0xffffff, 1.4);
    key.position.set(1, 1.5, 1).multiplyScalar(radius * 3);
    scene.add(key);
    var fill = new THREE.DirectionalLight(0xffffff, 0.5);
    fill.position.set(-1, -0.6, -0.8).multiplyScalar(radius * 3);
    scene.add(fill);

    var material = new THREE.MeshStandardMaterial({
      color: dark ? 0xc4cad1 : 0x9aa3ac,
      metalness: 0.15,
      roughness: 0.55,
      flatShading: false
    });
    var mesh = new THREE.Mesh(geometry, material);
    scene.add(mesh);

    var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(width, height);

    container.classList.add('is-live');
    container.insertBefore(renderer.domElement, container.firstChild);
    renderer.domElement.setAttribute('role', 'img');
    renderer.domElement.setAttribute('aria-label',
      'Interactive 3D model. Drag to rotate, scroll to zoom.');
    renderer.domElement.setAttribute('tabindex', '0');

    orbit(renderer.domElement, camera, radius, render);

    function render() {
      camera.lookAt(0, 0, 0);
      renderer.render(scene, camera);
    }
    render();

    window.addEventListener('resize', function () {
      var w = container.clientWidth;
      var h = Math.min(Math.round(w * 0.62), 520);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
      render();
    });
  }

  /* ------------------------------------------------------- orbit controls */
  // Minimal spherical-coordinate controls: drag to rotate, wheel to zoom,
  // arrow keys for keyboard users. Renders on demand rather than in a rAF loop,
  // so an idle viewer costs nothing.
  function orbit(dom, camera, radius, render) {
    var theta = Math.atan2(camera.position.x, camera.position.z);
    var phi = Math.acos(camera.position.y / camera.position.length());
    var dist = camera.position.length();
    var dragging = false;
    var lastX = 0;
    var lastY = 0;

    function update() {
      var sinPhi = Math.sin(phi);
      camera.position.set(
        dist * sinPhi * Math.sin(theta),
        dist * Math.cos(phi),
        dist * sinPhi * Math.cos(theta)
      );
      render();
    }

    dom.addEventListener('pointerdown', function (e) {
      dragging = true;
      lastX = e.clientX;
      lastY = e.clientY;
      dom.setPointerCapture(e.pointerId);
      dom.style.cursor = 'grabbing';
    });
    dom.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      theta -= (e.clientX - lastX) * 0.008;
      phi = clamp(phi - (e.clientY - lastY) * 0.008, 0.05, Math.PI - 0.05);
      lastX = e.clientX;
      lastY = e.clientY;
      update();
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (type) {
      dom.addEventListener(type, function () {
        dragging = false;
        dom.style.cursor = 'grab';
      });
    });
    dom.addEventListener('wheel', function (e) {
      e.preventDefault();
      dist = clamp(dist * (e.deltaY > 0 ? 1.1 : 0.9), radius * 1.1, radius * 20);
      update();
    }, { passive: false });
    dom.addEventListener('keydown', function (e) {
      var step = 0.12;
      if (e.key === 'ArrowLeft') theta += step;
      else if (e.key === 'ArrowRight') theta -= step;
      else if (e.key === 'ArrowUp') phi = clamp(phi - step, 0.05, Math.PI - 0.05);
      else if (e.key === 'ArrowDown') phi = clamp(phi + step, 0.05, Math.PI - 0.05);
      else if (e.key === '+' || e.key === '=') dist = clamp(dist * 0.9, radius * 1.1, radius * 20);
      else if (e.key === '-') dist = clamp(dist * 1.1, radius * 1.1, radius * 20);
      else return;
      e.preventDefault();
      update();
    });
    dom.style.cursor = 'grab';
    dom.style.touchAction = 'none';
  }

  function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)); }

  /* ------------------------------------------------------------ STL parse */
  function parseStl(THREE, buffer) {
    var geometry = new THREE.BufferGeometry();
    var positions = isBinary(buffer) ? parseBinary(buffer) : parseAscii(buffer);
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    return geometry;
  }

  // A binary STL is 84 bytes of header plus 50 bytes per triangle. If the file
  // length matches that arithmetic exactly, it is binary; otherwise it is ASCII.
  function isBinary(buffer) {
    if (buffer.byteLength < 84) return false;
    var view = new DataView(buffer);
    var triangles = view.getUint32(80, true);
    return 84 + triangles * 50 === buffer.byteLength;
  }

  function parseBinary(buffer) {
    var view = new DataView(buffer);
    var count = view.getUint32(80, true);
    var positions = new Float32Array(count * 9);
    var offset = 84;
    for (var i = 0; i < count; i++) {
      var base = offset + i * 50 + 12;      // skip the per-facet normal
      for (var v = 0; v < 3; v++) {
        var p = base + v * 12;
        positions[i * 9 + v * 3] = view.getFloat32(p, true);
        positions[i * 9 + v * 3 + 1] = view.getFloat32(p + 4, true);
        positions[i * 9 + v * 3 + 2] = view.getFloat32(p + 8, true);
      }
    }
    return positions;
  }

  function parseAscii(buffer) {
    var text = new TextDecoder().decode(buffer);
    var vertices = [];
    var re = /vertex\s+([-\d.eE+]+)\s+([-\d.eE+]+)\s+([-\d.eE+]+)/g;
    var m;
    while ((m = re.exec(text)) !== null) {
      vertices.push(parseFloat(m[1]), parseFloat(m[2]), parseFloat(m[3]));
    }
    return new Float32Array(vertices);
  }
})();
