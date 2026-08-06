/*
 * OPTIONAL — Cloudflare Worker in front of the R2 bucket.
 *
 * You do not need this. The recommended setup is option (a) in DEPLOYMENT.md: a
 * public R2 bucket bound to files.vinayrajusanniboina.com, which needs no code at
 * all. Deploy this Worker only if you want one of the two things it adds:
 *
 *   1. A download counter, so you can see which files reviewers actually take.
 *   2. Time-limited links, so a URL shared with a recruiter stops working after
 *      a while instead of being permanently public.
 *
 * The cost is that this is now code that can break, sitting between a hiring
 * manager and your CAD files. Weigh that honestly before deploying it.
 *
 * Routes
 *   GET  /<key>              serve the object (counts the download)
 *   GET  /<key>?sign=1       return a time-limited URL for that object as JSON
 *   GET  /_stats             download counts, requires the STATS_TOKEN header
 *
 * Bindings required (see wrangler.toml):
 *   BUCKET      R2 bucket binding
 *   COUNTERS    Workers KV namespace binding
 *   SIGNING_KEY secret, any long random string
 *   STATS_TOKEN secret, protects the /_stats endpoint
 *   LINK_TTL    var, seconds a signed link stays valid (default 604800 = 7 days)
 */

const DEFAULT_TTL = 604800; // 7 days

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const key = decodeURIComponent(url.pathname.replace(/^\/+/, ''));

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return json({ error: 'Method not allowed' }, 405);
    }

    if (key === '_stats') {
      return stats(request, env);
    }
    if (key === '' || key === 'index.html') {
      return json({ service: 'file delivery', usage: 'GET /<object-key>' }, 200);
    }

    // --- issue a signed link instead of the file itself ---
    if (url.searchParams.get('sign') === '1') {
      const ttl = Number(env.LINK_TTL || DEFAULT_TTL);
      const expires = Math.floor(Date.now() / 1000) + ttl;
      const sig = await sign(key, expires, env.SIGNING_KEY);
      const link = `${url.origin}/${encodeURI(key)}?expires=${expires}&sig=${sig}`;
      return json({ url: link, expires, expires_iso: new Date(expires * 1000).toISOString() });
    }

    // --- verify a signed link, when one was used ---
    const expires = url.searchParams.get('expires');
    const sig = url.searchParams.get('sig');
    if (expires || sig) {
      if (!expires || !sig) return json({ error: 'Incomplete signature' }, 400);
      if (Number(expires) < Math.floor(Date.now() / 1000)) {
        return json({ error: 'This link has expired. Ask for a fresh one.' }, 410);
      }
      const expected = await sign(key, Number(expires), env.SIGNING_KEY);
      if (!timingSafeEqual(expected, sig)) return json({ error: 'Bad signature' }, 403);
    }
    // Unsigned requests are still served. Set REQUIRE_SIGNATURE = "1" in
    // wrangler.toml to make signatures mandatory instead.
    else if (env.REQUIRE_SIGNATURE === '1') {
      return json({ error: 'This file requires a signed link.' }, 403);
    }

    const object = await env.BUCKET.get(key, {
      range: request.headers.get('range') ? request.headers : undefined
    });
    if (object === null) {
      return json({ error: 'Not found', key }, 404);
    }

    // Count the download without making the visitor wait for the KV write.
    ctx.waitUntil(count(env, key));

    const headers = new Headers(corsHeaders());
    object.writeHttpMetadata(headers);
    headers.set('etag', object.httpEtag);
    headers.set('cache-control', 'public, max-age=31536000, immutable');
    headers.set('content-disposition',
      `attachment; filename="${key.split('/').pop().replace(/"/g, '')}"`);
    if (object.range) headers.set('content-range',
      `bytes ${object.range.offset}-${object.range.offset + object.range.length - 1}/${object.size}`);

    return new Response(request.method === 'HEAD' ? null : object.body, {
      status: object.range ? 206 : 200,
      headers
    });
  }
};

/* ------------------------------------------------------------------ signing */
// HMAC-SHA256 over "key:expires", hex encoded. WebCrypto is available in Workers.
async function sign(key, expires, secret) {
  if (!secret) throw new Error('SIGNING_KEY is not set');
  const enc = new TextEncoder();
  const cryptoKey = await crypto.subtle.importKey(
    'raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const mac = await crypto.subtle.sign('HMAC', cryptoKey, enc.encode(`${key}:${expires}`));
  return [...new Uint8Array(mac)].map(b => b.toString(16).padStart(2, '0')).join('');
}

// Constant-time comparison, so response timing cannot be used to guess a signature.
function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/* ----------------------------------------------------------------- counting */
async function count(env, key) {
  if (!env.COUNTERS) return;
  try {
    const current = Number((await env.COUNTERS.get(`count:${key}`)) || '0');
    await env.COUNTERS.put(`count:${key}`, String(current + 1));
    await env.COUNTERS.put(`last:${key}`, new Date().toISOString());
  } catch (e) {
    // A counter failure must never stop a download.
  }
}

async function stats(request, env) {
  const token = request.headers.get('x-stats-token');
  if (!env.STATS_TOKEN || token !== env.STATS_TOKEN) {
    return json({ error: 'Unauthorised' }, 401);
  }
  const listed = await env.COUNTERS.list({ prefix: 'count:' });
  const rows = [];
  for (const k of listed.keys) {
    const key = k.name.slice('count:'.length);
    rows.push({
      key,
      downloads: Number(await env.COUNTERS.get(k.name)),
      last: await env.COUNTERS.get(`last:${key}`)
    });
  }
  rows.sort((a, b) => b.downloads - a.downloads);
  return json({ files: rows, total: rows.reduce((s, r) => s + r.downloads, 0) });
}

/* ------------------------------------------------------------------ helpers */
function corsHeaders() {
  return {
    'access-control-allow-origin': 'https://www.vinayrajusanniboina.com',
    'access-control-allow-methods': 'GET, HEAD, OPTIONS',
    'access-control-allow-headers': 'range, x-stats-token'
  };
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body, null, 2), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...corsHeaders() }
  });
}
