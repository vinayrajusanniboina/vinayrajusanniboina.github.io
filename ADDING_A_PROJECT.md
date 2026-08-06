# Adding a project

You do not need to know any web development to add a project to this site. You
write one text file, drop your images in a folder, and push. Everything else is
automatic.

This document is the complete reference. Read the first two sections and you can
add project number 20 as easily as project number 2.

---

## The five-minute version

```bash
./mvnw package -DskipTests
```

```bash
java -jar target/site-builder.jar new-project rear-upright-fea --title "Rear Upright FEA"
```

That creates three things:

| Path | What goes in it |
|---|---|
| `content/projects/rear-upright-fea.yaml` | The write-up. One file, plain text. |
| `assets/img/projects/rear-upright-fea/` | Screenshots, contour plots, photos. |
| `files/rear-upright-fea/` | CAD and simulation files. **Never** committed to git. |

Now edit the `.yaml` file. It arrives full of comments explaining every field,
with most sections commented out — **delete anything you have nothing to say
about**. A short honest page beats a long padded one.

Check it locally:

```bash
java -jar target/site-builder.jar serve
```

Open <http://localhost:8080/projects/> and your project is there.

Publish it:

```bash
git add -A && git commit -m "Add rear upright FEA" && git push
```

GitHub Actions builds and deploys. About ninety seconds later it is live.

---

## The content file

It is YAML. Three rules cover almost everything:

1. **Indentation is meaningful.** Two spaces per level. Never tabs.
2. **If a value contains a colon, a comma or a `#`, put quotes round it.**
   `context: "Meshing, turbulence model selection"` — without the quotes, YAML
   reads the comma as a separator and the build fails with a message telling you
   exactly which line.
3. **`|` means "keep my line breaks", `>` means "join this into one paragraph".**
   Use `|` for anything with paragraphs or bullet points, `>` for a single
   flowing sentence.

```yaml
overview: |
  This keeps the paragraph breaks.

  Like this one.

summary: >
  This gets joined into one
  continuous line.
```

### The three required fields

Everything else is optional.

```yaml
title: Rear upright FEA
summary: >
  Two or three sentences: the problem, what you did, the outcome.
```

The third is the slug, and you do not type it — it comes from the file name.
`rear-upright-fea.yaml` becomes `/projects/rear-upright-fea/`.

### Markdown in the prose fields

Fields that hold prose — `overview`, `methodology`, `rationale`, `results`,
`independence`, `approach` — accept Markdown:

```yaml
overview: |
  Plain paragraphs work as-is.

  **Bold** and *italic* work. So do bullet points:

  - first point
  - second point

  And links: [FSUK regulations](https://www.imeche.org/events/formula-student)
```

Short fields — `title`, `label`, `decision`, requirement `text` — are plain text.

---

## The ten sections of a project page

Each one renders only if you supply it. Delete the rest.

### 1. Overview and objective

```yaml
objective: >
  One sentence. What this work had to achieve.
overview: |
  A few paragraphs setting up the problem and why it mattered.
```

### 2. Design brief, requirements and constraints

```yaml
brief:
  context: |
    Where the requirement came from.
  requirements:
    - id: R1
      text: "Torsional stiffness at or above the target."
      rationale: "So suspension geometry behaves as designed."
      verification: "Twist test, dial gauges at four corners."
  constraints:
    - "Fixed competition deadline."
  out_of_scope:
    - "Suspension kinematics design."
```

Give requirements IDs. They render as a table, and being able to say "R2 was
verified by..." in an interview is worth the thirty seconds it costs.

### 3. Design decisions — the most important section

This is the section a hiring engineer reads most closely, because it is the one
that distinguishes engineering from CAD operation. The pattern is fixed:

**decision → alternatives considered → why chosen → what it cost you.**

```yaml
decisions:
  - title: Stiffen the mounting locally rather than redesign the rear frame
    decision: >
      Revise the rear engine mounting for local stiffness, leaving the frame
      architecture unchanged.
    alternatives:
      - option: Redesign the rear frame bay
        why_not: >
          Manufacturing time was not available in the remaining schedule.
      - option: Add a full bracing structure
        why_not: >
          Heavier, for stiffness that was not needed globally.
    rationale: |
      The compliance was local, so the fix should be local too.
    consequences: >
      Raises the stiffness discontinuity at the mounting.
    evidence: Driver feedback across subsequent runs.
```

The validator warns if a decision has no alternatives and no rationale, because
a decision with nothing rejected reads as an assertion rather than a choice.

Write down the alternatives you genuinely considered, and be honest about
`why_not`. "Ran out of time" is a real engineering constraint and reads far
better than a rationalisation.

### 4. Methodology

```yaml
methodology: |
  1. **Geometry.** Built in CAD, cleaned in SpaceClaim.
  2. **Meshing.** Refinement at the surfaces and in the wake.
  3. **Mesh independence.** Refined until the coefficients stopped moving.
```

### 5. Calculations

Maths is **TeX without dollar signs**. The template adds the delimiters.

```yaml
calculations:
  - title: Aerodynamic downforce at 60 km/h
    intro: Why this calculation matters.
    assumptions:
      - "Sea-level air, 15 °C."
      - "Steady state; transient corner-entry effects excluded."
    symbols:
      - { symbol: "\\rho", meaning: "Air density", value: "1.225", unit: "\\mathrm{kg/m^3}", source: "ISA sea level" }
      - { symbol: "V", meaning: "Freestream velocity", unit: "\\mathrm{m/s}" }
    display_math: "F_L = \\tfrac{1}{2}\\rho V^2 A C_L"
    steps:
      - description: Substitute the values.
        math: "F_L = 0.5 \\times 1.225 \\times 16.7^2 \\times 0.9 \\times 1.4"
        result: "215 N"
    result: Roughly 215 N of downforce at 60 km/h.
    note: Order of magnitude only — validate against CFD.
```

**Backslashes must be doubled** inside quotes: write `\\rho` to get `\rho`.
That is a YAML rule, not a rule of this site.

Useful TeX:

| You want | Write |
|---|---|
| Greek | `\\rho` `\\theta` `\\delta` `\\omega` |
| Fraction | `\\frac{a}{b}` or inline `\\tfrac{1}{2}` |
| Subscript / superscript | `C_D` `V^2` `C_{D,0}` |
| Times | `\\times` |
| Units in a symbol table | `\\mathrm{kg/m^3}` |
| Approximately | `\\approx` |

Always state units and assumptions. The validator warns if a symbol has a value
but no unit, and if a calculation lists no assumptions — because a number with
no stated assumptions cannot be checked by anyone.

### 6. Simulation setup and results

```yaml
simulation:
  software: ANSYS Fluent 2023 R2
  solver: Pressure-based, steady, coupled
  turbulence_model: k-omega SST
  mesh:
    type: Poly-hexcore
    elements: "8.4 million"
    y_plus: "< 1"
    inflation_layers: "15, growth ratio 1.2"
    independence: |
      How you established the answer stopped moving.
    independence_table:
      caption: Mesh independence study
      columns: ["Mesh", "Cells", "C_D", "Change"]
      rows:
        - ["Coarse", "2.1 M", "0.412", "—"]
        - ["Medium", "4.8 M", "0.389", "-5.6%"]
        - ["Fine",   "8.4 M", "0.385", "-1.0%"]
  boundary_conditions:
    - { name: Inlet, value: "16.7 m/s velocity inlet, 1% turbulence intensity" }
    - { name: Outlet, value: "Pressure outlet, 0 Pa gauge" }
  convergence: Residuals below 1e-5; drag monitor flat over 400 iterations.
  results: |
    What the simulation showed, and why.
  figures:
    - src: assets/img/projects/rear-upright-fea/stress.png
      alt: "Von Mises stress contour on the upright, load case 2."
      caption: "Peak stress at the lower ball joint mounting."
```

**Every table row must have the same number of cells as there are columns.** The
build fails with the row number if it does not.

### 7. Validation, testing and what went wrong

Do not skip the honest part. `issues` and `lessons` are the fields that make a
portfolio credible.

```yaml
validation:
  approach: |
    How you checked the result was real.
  results: |
    What it showed.
  issues:
    - "The instability was found in testing, not in design."
    - "The test was qualitative — no before-and-after number."
  lessons:
    - "Design the instrumentation into the test plan."
  future_work: Instrument the mounting and repeat the test.
```

### 8. Gallery

```yaml
gallery:
  - src: assets/img/projects/rear-upright-fea/render.jpg
    alt: "Rendered view of the machined upright."
    caption: "Final geometry after the third iteration."
```

**Alt text is required and the build fails without it.** Describe what an
engineer should see, not what the file is: "Von Mises stress contour showing
peak stress at the lower ball joint mounting" — not "FEA screenshot".

Images are lazy-loaded and open in a lightbox automatically. Save them at about
1600 px wide; anything larger just slows the page down.

### 9. Downloads

Two steps. First put the files in `files/<your-project>/` and list them:

```yaml
downloads:
  - label: Upright geometry (STEP)
    file: files/rear-upright-fea/upright.step
    requires: Any CAD package, or a free STEP viewer
    neutral_format: true
  - label: SOLIDWORKS part
    file: files/rear-upright-fea/upright.SLDPRT
    requires: SOLIDWORKS 2023 or newer
```

Then push them to Cloudflare:

```bash
java -jar target/site-builder.jar upload
```

Size, SHA-256 checksum and download URL are filled in automatically and recorded
in `content/uploads.yaml`. **Commit that file.** You do not type any of those
values by hand, and unchanged files are skipped on re-runs.

Two rules worth following:

- **Always include a neutral format** — STEP, STL or a PDF drawing. A reviewer
  without a SOLIDWORKS licence should still be able to look at your work. The
  validator warns when every download for a project is proprietary.
- **Say which version opens it.** `requires: SOLIDWORKS 2023 or newer` saves
  someone downloading 40 MB to find out their 2021 licence cannot open it.

### 10. Optional 3D viewer

```yaml
viewer:
  model: files/rear-upright-fea/upright-preview.stl
  poster: assets/img/projects/rear-upright-fea/render.jpg
  poster_alt: "Rendered view of the upright."
  note: Coarse STL export, about 4 MB.
```

STL only. Export from SOLIDWORKS with **File → Save As → STL**, coarse
resolution, and keep it under about 10 MB — it downloads over a phone connection.
Nothing loads until the visitor presses the button, and if anything fails the
poster image stays put.

---

## Tags

```yaml
tags: [CFD, CAD, Formula Student]
```

Tags become filter buttons on the projects index. The known set is CAD, CFD, FEA,
EV Systems, Formula Student, Vehicle Dynamics, Aerodynamics, Manufacturing,
Testing and Control Systems. A new tag is allowed — you get a warning telling you
a new filter button will appear, which is there to catch `CDF` for `CFD`.

## The home page

A project shows on the home page if it is listed in `home.featured` in
`content/site.yaml`. Order in that list is the order on the page.

---

## When the build complains

The validator distinguishes two things:

**`ERROR`** stops the build. The live site is untouched. Common ones:

| Message | Fix |
|---|---|
| `unknown key 'tubulence_model'` | Typo in a field name. The message lists the valid keys for that spot. |
| `image ... has no alt text` | Add `alt:`. Not optional. |
| `points at ... which does not exist` | Wrong path, or you have not saved the file yet. Paths start from the repo root: `assets/img/projects/...`. |
| `row 3 has 4 cells but there are 5 columns` | A table row is short. |
| `has not been uploaded` | Run `upload`. |
| `slug must be lowercase letters, digits and single hyphens` | Rename the file. |

**`warn`** does not stop anything. It is a note that something will read worse
than it could: a summary that is too long for a card, a decision with no
alternatives, a mesh block with no independence study, a project where every
download needs a licence.

To see the problems without building anything:

```bash
java -jar target/site-builder.jar validate
```

---

## Two habits worth having

**Write the decisions section first.** If you cannot articulate what you chose
and what you rejected, the rest of the page is a description of software you
used rather than engineering you did.

**Put the number you are least comfortable with in the page.** The mesh count,
the y+, the assumption you are not sure about. An engineer reading it will trust
the page more, not less — and if it is wrong, you would rather find out in a
conversation than have it quietly not come up.
