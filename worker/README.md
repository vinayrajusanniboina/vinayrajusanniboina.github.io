# Download Worker (optional — you probably do not need this)

The recommended setup is **not** this Worker. It is a public R2 bucket bound to
`files.vinayrajusanniboina.com`, which is configured entirely in the Cloudflare
dashboard and involves no code. See step 8 of [DEPLOYMENT.md](../DEPLOYMENT.md).

Deploy this Worker only if you specifically want one of:

| You want | This gives you |
|---|---|
| To know which files reviewers actually download | A counter per object, in Workers KV, readable at `/_stats` |
| Links that stop working after a while | Time-limited signed URLs, default 7 days |
| Files that are not permanently public | Set `REQUIRE_SIGNATURE = "1"` and only signed links work |

The cost is real: this is code sitting between a hiring manager and your CAD
files. If it breaks, the downloads break. A public bucket cannot break.

## Deploying it

```bash
cd worker
npx wrangler kv namespace create COUNTERS
```

Paste the printed id into `wrangler.toml`, then:

```bash
npx wrangler secret put SIGNING_KEY
```

```bash
npx wrangler secret put STATS_TOKEN
```

```bash
npx wrangler deploy
```

`SIGNING_KEY` can be any long random string — generate one with
`openssl rand -hex 32`. `STATS_TOKEN` protects the stats endpoint; treat it as a
password.

If the Worker is on `files.vinayrajusanniboina.com`, **remove** the R2 public-bucket
custom domain binding first. Two things cannot serve the same hostname.

## Checking it

```bash
curl -I https://files.vinayrajusanniboina.com/formula-student-chassis/chassis-frame.step
```

Ask for a signed link:

```bash
curl "https://files.vinayrajusanniboina.com/formula-student-chassis/chassis-frame.step?sign=1"
```

Read the download counts:

```bash
curl -H "x-stats-token: YOUR_STATS_TOKEN" https://files.vinayrajusanniboina.com/_stats
```

## Free tier

Workers free tier is 100,000 requests/day and KV free tier is 100,000 reads and
1,000 writes per day. A portfolio site will not come close. One write per
download means you would need a thousand downloads in a day to hit the KV write
limit.
