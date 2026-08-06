# What to do next

Ordered by how much each one is worth relative to the effort. The first four are
worth doing before you send the link to anyone.

## Before you share the link

1. **Confirm the phone number.** `content/site.yaml` has `+44 790042005`, which
   is one digit short of a standard UK mobile. Fix it, or delete the line — every
   page that shows it is guarded and will simply omit it.

2. **Add the CV PDF.** Save it as
   `assets/cv/vinay-raju-sanniboina-cv.pdf`. Until you do, the build warns and
   the download button on `/cv/` 404s.

3. **Replace the placeholder images.** Both hero images are grey
   "IMAGE TO BE ADDED" graphics. The highest-value replacements, in order:
   - a pressure or velocity contour plot from Fluent (front wing)
   - the three chassis iterations side by side, or the revised rear mounting
   - a photograph of the car during testing

   Save at about 1600 px wide. Alt text is required — the build fails without it.

4. **Fill in the `TO CONFIRM` markers.** Search both project files for
   `TO CONFIRM`. These are the numbers only you have: mesh cell count, y+, the
   turbulence model, inlet velocity, reference area, the baseline and final drag
   coefficients, the torsional stiffness target. Each one you fill in makes the
   page materially more credible; each one left in makes it less.

## Soon after

5. **Upload the CAD and simulation files.** Put them under `files/`, uncomment
   the `downloads:` blocks, run `upload`. A reviewer being able to open your
   geometry is the single biggest differentiator against other graduate
   portfolios. Include a STEP export every time.

6. **Add a portrait.** Uncomment `person.image` in `site.yaml`. This also fixes
   the social share preview — right now only project pages have an `og:image`, so
   a link to the home page shared on LinkedIn renders as plain text.

7. **Run Lighthouse** on a project page in Chrome DevTools. The site is built to
   score well, but images are the usual culprit: export as WebP or AVIF rather
   than PNG for contour plots, and add `width` and `height` to the image entries
   in the content files so the browser can reserve the space and avoid layout
   shift.

8. **Submit the sitemap** to [Google Search Console](https://search.google.com/search-console)
   so searching your name finds the site. It will not otherwise, for months.

## Worth adding once it is live

9. **A third project.** Two is thin for a portfolio. Anything with a real
   decision in it works — a coursework FEA study, a battery pack thermal
   calculation, an EV powertrain sizing exercise from the MSc. The template makes
   a short honest write-up cheap: title, summary, one decision, one calculation.

10. **A `srcset` for the hero images.** The template already lazy-loads and sets
    dimensions; responsive sources would cut mobile payload further. Worth doing
    once you have real photographs rather than SVG placeholders.

11. **Instrument what recruiters actually look at.** Either a privacy-friendly
    analytics script (`analytics` is already a field in `site.yaml`) or the
    optional Cloudflare Worker's download counter. Knowing which project page
    people read tells you which one to expand.

12. **A short project video.** A 20-second screen capture of the CFD post-
    processing, or the car running, embedded on the project page. Nothing on the
    site currently supports video — it would need a small template addition —
    but it is the thing most likely to hold a reviewer's attention.

## Do not bother with

- **A contact form.** The email address works, does not depend on a third party,
  and cannot silently break. The form is already supported if you want it —
  set `contact.form_endpoint` — but a silently broken contact form on a job-hunt
  site is an expensive failure for no real gain.
- **A blog.** Unless you will actually write it. An abandoned blog with two posts
  from 2026 reads worse than no blog.
- **Moving to a framework.** There is nothing here that React or Astro would do
  better, and both would add a dependency tree that needs maintaining every few
  months to keep working.
