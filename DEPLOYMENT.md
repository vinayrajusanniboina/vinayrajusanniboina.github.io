# Deployment runbook

Start to finish: empty machine → live site on `www.vinayrajusanniboina.com` with
downloads on `files.vinayrajusanniboina.com`.

Follow it in order. Where a value depends on your account it is marked
**ACCOUNT-SPECIFIC** — everything else is exact and can be copied literally.

Total cost: the domain, about £8–12/year. Nothing else in this runbook costs
money.

---

## What you need before you start

- A GitHub account
- A Cloudflare account (free)
- A payment method for the domain
- JDK 21+ installed, with `JAVA_HOME` set — see [README.md](README.md)

Expect about 45 minutes, plus up to 24 hours of waiting for DNS at step 6.

---

## 1. Create the repository

The repository can be called anything. It does **not** need to be named
`vinaysanniboina.github.io` — that naming rule only applies if you want the site
at `username.github.io`, and you are using a custom domain instead.

```bash
gh repo create vinayrajusanniboina.com --public --source=. --remote=origin
```

Without the `gh` CLI: create it in the GitHub web UI, then:

```bash
git init -b main
```

```bash
git remote add origin https://github.com/YOUR_USERNAME/vinayrajusanniboina.com.git
```

**ACCOUNT-SPECIFIC:** `YOUR_USERNAME`.

Make it **public**. GitHub Actions minutes are unlimited on public repositories
and metered on private ones under the free plan, and Pages on a private repo
requires a paid plan.

```bash
git add -A && git commit -m "Initial site"
```

```bash
git push -u origin main
```

Before pushing, confirm `files/` is being ignored:

```bash
git status --porcelain files/
```

That must print nothing. If it prints file paths, stop and check `.gitignore` —
committing a SOLIDWORKS assembly is effectively permanent, because git keeps
every version of it forever.

---

## 2. Turn on GitHub Pages

**Settings → Pages → Build and deployment → Source: GitHub Actions.**

Not "Deploy from a branch". This repository builds with Java, so the workflow
publishes the artifact directly.

The push in step 1 will have already triggered the workflow. Check
**Actions** — it should be green in about 90 seconds. The site is live at
`https://YOUR_USERNAME.github.io/vinayrajusanniboina.com/` at this point, with the
custom domain still to come.

---

## 3. Buy the domain

Buy `vinayrajusanniboina.com` from any registrar — Cloudflare Registrar, Namecheap
and Porkbun are all around £8–12/year for a `.com`.

**GitHub does not sell domains.** Custom domains on Pages are free to *use*; the
domain itself is the one thing in this stack you pay for.

If you buy through **Cloudflare Registrar**, DNS is already set up and you can
skip step 4.

---

## 4. Move DNS to Cloudflare

Cloudflare's free plan gives you DNS, and it is the same account you need for R2,
so keeping both in one place is worth it.

1. Cloudflare dashboard → **Add a site** → `vinayrajusanniboina.com` → **Free** plan.
2. Cloudflare scans the existing records and gives you two nameservers, e.g.
   `aida.ns.cloudflare.com` and `rob.ns.cloudflare.com`. **ACCOUNT-SPECIFIC** —
   yours will be different names.
3. At your registrar, replace the nameservers with those two.
4. Wait. Usually under an hour, occasionally up to 24. Cloudflare emails you.

---

## 5. DNS records

In **Cloudflare → DNS → Records**, add these five. The IP addresses are GitHub's
and are the same for everyone — copy them exactly.

### Apex (`vinayrajusanniboina.com`) — four A records

| Type | Name | Content | Proxy status | TTL |
|---|---|---|---|---|
| A | `@` | `185.199.108.153` | **DNS only** | Auto |
| A | `@` | `185.199.109.153` | **DNS only** | Auto |
| A | `@` | `185.199.110.153` | **DNS only** | Auto |
| A | `@` | `185.199.111.153` | **DNS only** | Auto |

All four. They are separate servers, and using fewer removes redundancy.

If you prefer IPv6, GitHub also publishes four AAAA records
(`2606:50c0:8000::153`, `8001::153`, `8002::153`, `8003::153`). Optional.

### `www` — one CNAME

| Type | Name | Content | Proxy status | TTL |
|---|---|---|---|---|
| CNAME | `www` | `YOUR_USERNAME.github.io` | **DNS only** | Auto |

**ACCOUNT-SPECIFIC:** `YOUR_USERNAME`. Note the trailing `.github.io` — it is
your GitHub *user* domain, not the repository URL, and there is no path on it.

Cloudflare supports CNAME flattening at the apex, so a single
`CNAME @ → YOUR_USERNAME.github.io` would also work. The four A records are the
configuration GitHub documents and tests against, so use those.

### The proxy setting matters — read this

Set every record above to **DNS only** (grey cloud), not **Proxied** (orange
cloud), at least until HTTPS is working.

Here is why. GitHub Pages issues its own Let's Encrypt certificate, and to do
that it must reach your domain directly. With Cloudflare's proxy on, requests
terminate at Cloudflare, GitHub's certificate validation fails, and **Enforce
HTTPS stays greyed out** in your Pages settings. This is the single most common
way this setup goes wrong.

Once HTTPS is working end to end you *can* switch the proxy on, but you must
first set **SSL/TLS → Overview → Full (strict)** in Cloudflare. Leaving it on
"Flexible" with the proxy enabled creates a redirect loop, because Cloudflare
requests `http://` from GitHub, GitHub redirects to `https://`, and round it
goes.

Honest recommendation: leave it on **DNS only**. GitHub Pages is already
CDN-backed and fast. The proxy buys you very little here and adds a whole class
of failure.

---

## 6. Point the site at the domain

The `CNAME` file is generated from `url:` in `content/site.yaml` on every build,
so the domain is configured in exactly one place. It is already set to
`https://www.vinayrajusanniboina.com`.

In **Settings → Pages → Custom domain**, enter:

```
www.vinayrajusanniboina.com
```

GitHub checks DNS. A green tick means the CNAME resolved. A red error usually
means DNS has not propagated yet — wait and press **Check again**.

Check it yourself:

```bash
dig +short www.vinayrajusanniboina.com
```

On Windows:

```powershell
Resolve-DnsName www.vinayrajusanniboina.com -Type CNAME
```

---

## 7. Enforce HTTPS

Once the custom domain shows a tick, GitHub provisions a certificate. This takes
anywhere from a few minutes to an hour.

Then **Settings → Pages → Enforce HTTPS** → tick it.

If the checkbox is greyed out:

1. Confirm every DNS record is **DNS only**, not proxied.
2. Remove the custom domain, save, re-add it, save. This retriggers certificate
   issuance and fixes it most of the time.
3. Wait an hour and try again. Certificate provisioning is not instant.

Verify:

```bash
curl -sI https://www.vinayrajusanniboina.com | head -n 1
```

```bash
curl -sI http://vinayrajusanniboina.com | grep -i location
```

The apex should 301 to `https://www.vinayrajusanniboina.com`. GitHub does that
redirect automatically once the apex A records are in place.

---

## 8. Cloudflare R2 for the downloads

### Create the bucket

Cloudflare dashboard → **R2** → **Create bucket**.

- Name: `vinayrajusanniboina-files` — **ACCOUNT-SPECIFIC** if you pick another name;
  it must match `R2_BUCKET` and `worker/wrangler.toml`.
- Location: **Automatic**, or **EU** if you want data kept in Europe.

### Bind it to a subdomain

This is the recommended option — a public bucket on a custom domain, no code.

Bucket → **Settings** → **Public access** → **Custom Domains** → **Connect
Domain** → enter:

```
files.vinayrajusanniboina.com
```

Cloudflare adds the DNS record itself and issues the certificate. Nothing to
configure, nothing that can break, and it is what `downloads.public_base_url` in
`content/site.yaml` already points at.

> **The runner-up:** the Cloudflare Worker in [`worker/`](worker/README.md),
> which adds a download counter and time-limited signed links. Use it only if you
> specifically want those — it puts code between a hiring manager and your CAD
> files, and a public bucket cannot break.

Do **not** enable the `r2.dev` public development URL. It is rate-limited and
Cloudflare explicitly says not to use it in production.

### Create an API token

R2 → **Manage R2 API Tokens** → **Create API token**.

- Permissions: **Object Read & Write**
- Scope: **Apply to specific buckets** → `vinayrajusanniboina-files`
- TTL: leave as forever, or set a reminder to rotate it

Scope it to the one bucket. A token that can only write to your files bucket is
worth the extra ten seconds.

You get three values, and the secret is shown **once**:

- Account ID — **ACCOUNT-SPECIFIC**, 32 hex characters
- Access Key ID — **ACCOUNT-SPECIFIC**
- Secret Access Key — **ACCOUNT-SPECIFIC**

Put them in a password manager. They never go in the repository.

### Set the environment variables

PowerShell, current session:

```powershell
$env:R2_ACCOUNT_ID = "your-32-char-account-id"; $env:R2_ACCESS_KEY_ID = "your-access-key"; $env:R2_SECRET_ACCESS_KEY = "your-secret-key"; $env:R2_BUCKET = "vinayrajusanniboina-files"
```

To persist across reboots:

```powershell
[Environment]::SetEnvironmentVariable("R2_ACCOUNT_ID", "your-32-char-account-id", "User")
```

bash / zsh, in `~/.bashrc` or `~/.zshrc`:

```bash
export R2_ACCOUNT_ID=your-32-char-account-id
```

---

## 9. First upload

Put a file where a project expects it, uncomment that project's `downloads:`
block, then:

```bash
java -jar target/site-builder.jar upload --dry-run
```

That lists what would be sent and touches the network only to nothing — it is
safe to run any time. When it looks right:

```bash
java -jar target/site-builder.jar upload
```

It computes a SHA-256 for each file, skips anything already on R2 with a matching
checksum, uploads the rest, and writes `content/uploads.yaml`.

**Commit `content/uploads.yaml`.** It is how the build knows the size, checksum
and URL of each download. The files themselves stay out of git.

Check one landed:

```bash
curl -sI https://files.vinayrajusanniboina.com/formula-student-chassis/chassis-frame.step | head -n 3
```

---

## 10. First real deploy

```bash
java -jar target/site-builder.jar validate
```

Fix anything reported as `ERROR`. Then:

```bash
git add -A && git commit -m "Add downloads" && git push
```

Watch **Actions**. Green, then check the live site.

### Post-deploy checklist

```bash
curl -sI https://www.vinayrajusanniboina.com | head -n 1
```

- [ ] Home page loads over HTTPS
- [ ] `http://vinayrajusanniboina.com` redirects to `https://www.vinayrajusanniboina.com`
- [ ] Project pages load and the maths renders
- [ ] Projects index: tag filter and search both work
- [ ] A download link actually downloads
- [ ] Dark/light toggle works and survives a reload
- [ ] `https://www.vinayrajusanniboina.com/sitemap.xml` loads
- [ ] A made-up URL shows the 404 page
- [ ] Lighthouse (Chrome DevTools) on a project page

Then submit the sitemap to
[Google Search Console](https://search.google.com/search-console) so the site is
findable by name.

---

## Free-tier reality check

Plainly, what is free and where the limits actually are.

| Service | Free allowance | Where it bites |
|---|---|---|
| **GitHub Pages** | 1 GB repo (soft), 100 GB/month bandwidth (soft), 10 builds/hour | Bandwidth is a soft limit — GitHub emails you rather than cutting you off. A portfolio will not approach it. |
| **GitHub Pages file size** | **100 MB per file, hard** | This is why CAD files go to R2. A push containing a 120 MB assembly is rejected outright. |
| **GitHub Actions** | Unlimited on public repos | Free only while the repo is public. Private repos get 2,000 minutes/month. |
| **Cloudflare R2 storage** | 10 GB/month | Roughly 200–500 CAD files. Beyond that it is $0.015/GB/month — 50 GB would be about $0.60/month. |
| **Cloudflare R2 operations** | 1M Class A (writes) and 10M Class B (reads) per month | You would need to re-upload the entire portfolio hundreds of times a day to notice. |
| **Cloudflare R2 egress** | **Unlimited, £0** | The reason for choosing R2 over S3. On S3, 100 GB of CAD downloads would cost about $9. Here it is zero at any volume. |
| **Cloudflare DNS** | Unlimited | — |
| **Cloudflare Workers** (optional) | 100,000 requests/day | Only relevant if you deploy the optional Worker. |
| **Workers KV** (optional) | 100k reads, 1k writes/day | One write per download. A thousand downloads in one day would hit it. |
| **HTTPS certificates** | Free, auto-renewed | — |
| **Domain** | **Not free** | ~£8–12/year. The only real cost. |

**Where this design would exceed a free tier:** only R2 storage, and only past
10 GB of CAD and simulation files. At that point you would be paying pennies per
month, and the fix is to stop uploading raw ANSYS project archives and upload the
case and data files instead.

Two things to keep an eye on, neither of them a cost:

- **`files/` must never enter git.** Not a cost limit but a permanent one — git
  keeps deleted binaries in history forever, and the only real fix is rewriting
  history.
- **The `r2.dev` development URL is rate-limited.** Use the custom domain
  binding from step 8, not `r2.dev`.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "Enforce HTTPS" is greyed out | Cloudflare proxy is on. Set the records to DNS only, then remove and re-add the custom domain. |
| Redirect loop after enabling the proxy | Cloudflare SSL/TLS mode is Flexible. Set it to **Full (strict)**. |
| Site 404s after a deploy | The `CNAME` file was lost. It is generated from `url:` in `site.yaml`; the workflow fails the build if it is missing. |
| Pages shows unstyled HTML | `.nojekyll` missing, so Jekyll ate the assets. Also generated automatically. |
| Actions fails on validate | A content error. The log names the file, the line and the key. |
| Download link 404s | `downloads.public_base_url` does not match the R2 custom domain, or `upload` was never run. |
| `upload` fails with SignatureDoesNotMatch | Wrong account ID in the endpoint, or the token is scoped to a different bucket. |
| Everything works but the domain does not resolve | Nameservers not switched at the registrar. `dig NS vinayrajusanniboina.com` should show Cloudflare. |
