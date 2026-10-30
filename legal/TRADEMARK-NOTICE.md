# Trademark Notice and Usage Guidelines — KubeNexus

> **This is not legal advice.** It is a working compliance guide built from the
> primary sources cited below, prepared so you can avoid the most common and most
> expensive mistakes. The trademark owners have final authority. If you plan to
> commercialise this app aggressively, get a lawyer to review your store listing
> and marketing.

**Sources (verified 26 September 2026):**

- LF Projects, LLC — [Trademark Policy](https://lfprojects.org/policies/trademark-policy/) (updated 10 May 2024; page last modified 4 September 2026)
- Apache License, Version 2.0, §4 and §6 — [`kubernetes/kubernetes` LICENSE](https://raw.githubusercontent.com/kubernetes/kubernetes/master/LICENSE)
- Ghostty — [LICENSE](https://raw.githubusercontent.com/ghostty-org/ghostty/main/LICENSE), [README](https://github.com/ghostty-org/ghostty)
- Google Play [Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/17006354) and [Misrepresentation](https://support.google.com/googleplay/android-developer/answer/9888689) policies

---

## 1. Kubernetes® — the highest-risk item

### 1.1 You have no trademark rights from the licence

`k8s.io/client-go` and friends are licensed Apache-2.0, and §6 says:

> *"This License does not grant permission to use the trade names, trademarks,
> service marks, or product names of the Licensor, except as required for
> reasonable and customary use in describing the origin of the Work and
> reproducing the content of the NOTICE file."*

The LF trademark policy is blunter still:

> *"A copyright license, even an open source copyright license, does not include
> an implied right or license to use a trademark that may be related to the
> project developing the licensed software or other materials."*

**"Kubernetes" is a registered trademark of LF Projects, LLC** — it appears on
their list of registered marks alongside Ceph®, gRPC®, ONNX®, React®, and
Helm-adjacent names. Being an Apache-2.0 consumer, a CNCF-adjacent project, or
even a popular open-source app grants you nothing. What governs you is fair use
and the LF usage guidelines.

### 1.2 What you must do

| Rule | Practical meaning for KubeNexus |
|---|---|
| Use only as an **adjective followed by the generic noun** | "Kubernetes® clusters" ✅ · "Kubernetes®" alone as a product name ❌ |
| **Never in the plural or possessive** | "for Kubernetes® clusters" ✅ · "for Kubernetes®'s API" ❌ |
| **Never part of your product name** | "KubeNexus" ✅ · "Kubernetes Nexus", "KubeNexus Kubernetes Edition" ❌ |
| **Never part of a domain name** | `kubenexus.app` ✅ · `kubenexus.kubernetes.io`, `kubernetes-kubenexus.com` ❌ |
| **Never incorporated into your logo or design** | Your own logo only — no Kubernetes wheel/heptagon, ever, without written permission |
| `®` immediately after **first** use | "Manages your Kubernetes® clusters" |
| Distinguish typographically | Capitalisation, bold, or quotes |
| **Never more prominent than your own product name** | "KubeNexus for Kubernetes®" — not "Kubernetes® by KubeNexus" |
| Never alter or combine with other marks | No `K8s-Nexus`, no `Kubenexus®` |
| No disparagement | Don't call your fork "Kubernetes but better" |

### 1.3 Use LF Projects has blessed

These come verbatim from the LF policy's compatibility examples:

**Correct:**
- "KubeNexus for Kubernetes®"
- "KubeNexus, a Kubernetes® client"
- "KubeNexus compatible with Kubernetes®"
- "KubeNexus for use with Kubernetes®"
- "Quick Start for Kubernetes® by Hridaya Prajapati"

**Incorrect:**
- "Kubernetes® KubeNexus"
- "Kubernetes® by KubeNexus"
- "Kubernetes® – KubeNexus"
- "KubeNexus – Kubernetes®"
- "Kubernetes® Quick Start by Hridaya Prajapati"

The pattern is simple: **your product name comes first; the mark follows as a
descriptive adjective.**

### 1.4 The monetisation wrinkle — read this before you add IAP

The LF policy contains a clause that bites specifically at commercial use:

> *"Do not use logos or names of LF Projects in any commercial or marketing
> context other than as expressly permitted in this policy unless you have
> obtained explicit written permission from LF Projects to do so."*

Practically, that means:

- **Text is fine.** "KubeNexus for Kubernetes®" is expressly permitted fair use
  and remains fine when you start charging. It is a true factual statement of
  what the app does.
- **Logos are not fine without written permission.** The moment your Play listing
  becomes marketing for a paid product, do **not** put the Kubernetes wheel or
  heptagon in your icon, screenshots, feature graphic, or store description. Get
  written permission first, or use none.
- **Don't imply endorsement.** No "Official Kubernetes® app", no "Partner of the
  Kubernetes® project", no LF or CNCF logos. Fair use "does not permit you to
  state or imply that the owner of a mark produces, endorses, or supports your
  company, products, or services."

**Consent:** even for text, the policy says you may "acknowledge the owner of the
trademark with a trademark notice." Add a disclaimer in your listing and in-app
About screen:

> KubeNexus is an independent app and is not affiliated with, endorsed by, or
> sponsored by The Linux Foundation or the Kubernetes® project. Kubernetes® is a
> registered trademark of LF Projects, LLC.

### 1.5 If you need permission

Contact **trademarks@lfprojects.org**. Be specific about where the mark will
appear and say plainly that you are seeking a nominative/compatibility use.

## 2. Ghostty — a different situation, easier

### 2.1 What the licence gives you

MIT, full text in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) §1. The
copyright is **Mitchell Hashimoto and Ghostty contributors**.

### 2.2 What it does not give you

MIT grants **copyright** rights. It grants **no trademark rights** — the same
gap as Apache-2.0 §6. I found **no separate trademark policy published by the
Ghostty project** (no trademark file in the repository, no trademark page on
ghostty.org as of this writing). So:

- "Ghostty" is a **common-law trademark** for a fast native terminal emulator.
- You have no licence to it, and no explicit permission regime to ask about.
- The safe course is ordinary nominative use: refer to the project so a user
  knows what the code is, and nothing more.

### 2.3 What you may do

- State factually that the app is built on Ghostty: *"Terminal rendering powered
  by the Ghostty terminal core."*
- Include the MIT copyright and permission notice in your shipped binary and in
  your open-source-licenses screen. This is a **copyright** obligation,
  independent of trademarks, and it is not optional.
- Link to <https://github.com/ghostty-org/ghostty> as a plain external link.
- Note that upstream **explicitly invites embedding** — its README asks third
  parties to "embed a terminal into their own applications" and ships
  [Ghostling](https://github.com/ghostty-org/ghostling) as a reference
  implementation. You are a sanctioned use case, not a tolerated one.

### 2.4 What you must not do

- Do not put "Ghostty" in the app name, icon, package name, or logo.
- Do not use the Ghostty logo or wordmark as your own branding.
- Do not present KubeNexus as a Ghostty product, or as "Ghostty for Android".
- Do not ship the Ghostty "About" screen or its settings UI as though it were
  yours. This matters: you have a Ghostty-derived terminal UI in your app, so
  check that the About/Credits surface is your own and clearly attributes Ghostty
  as a third-party component.
- Do not modify the code and present the result as Ghostty. If you have patched
  `libghostty/zig-pkg/ghostty-*`, your fork is a derivative work, not
  Ghostty.

## 3. Your own name — "KubeNexus"

"KubeNexus" is a coined name and is **not** on LF Projects' registered or
pending-trademark lists. It is not confusingly similar to any registered mark.
It is, in principle, usable and registrable by you.

**One risk to be aware of:** the `Kube-` prefix is crowded in this ecosystem, and
LF Projects holds or has applied for many marks beginning with it — **Kubecon®**,
KubeClipper™, KubeEdge™, KubeVirt™, KubeFleet™, KubeStellar™, KubeElasti™,
Kubestronaut™, KubeClipper™, and others. "KubeNexus" is distinct enough from all
of them. But before you adopt or register **any** new name, screen it against
both the LF Projects trademark list and the USPTO TMEP search, because Play's
Misrepresentation policy prohibits apps that "impersonate any person or
organization."

## 4. The Google Play overlay

Play enforces trademark and identity rules independently of any upstream
policy. Two are directly relevant:

**Deceptive Behavior** — "Don't impersonate other apps, brands, or government
entities"; "Apps that falsely claim to be the official app of an established
entity. Titles like 'Justin Bieber Official' are not allowed without the
necessary permissions or rights."

**Misrepresentation** — Play does not allow apps "that impersonate any person or
organization, or that misrepresent or conceal their ownership or primary
purpose."

**Behavior Transparency** — "Ensure all code in your app, including third-party
SDKs, is directly related to the app's stated purpose." Bundled terminal
emulation and Kubernetes client code are both directly related, so you are fine —
but do not add features you do not declare in the listing.

Practically, a Play reviewer will look for: an "Official" or "Partner" claim, a
Kubernetes logo in the icon or screenshots, or a description implying
certification. None of those may be present.

**Not relevant here:** you are not claiming a "Certified Kubernetes®" mark. Those
Certification marks (Certified Kubernetes®, Certified Kubernetes Administrator®,
and similar) are Compliance Marks under the LF policy and may be used **only**
after completing the requisite compliance testing with explicit authorisation.
Never reference them.

## 5. Pre-ship checklist

**App name and identity**
- [ ] App name is exactly "KubeNexus" — no "Kubernetes" in it
- [x] Launcher icon is your own artwork; no Kubernetes, Ghostty, LF, or CNCF marks
      (the KubeNexus mark: a hub joined to three nodes, in teal)
- [ ] Package ID `dev.hridaya.kubenexus` — no trademarked terms
- [ ] No "Official", "Partner", "Certified", or "Powered by Kubernetes®" claims
      anywhere in the listing

**Store listing**
- [ ] Title and short description lead with "KubeNexus", not with "Kubernetes"
- [ ] Any mention of Kubernetes is adjectival and follows your product name
- [ ] `®` on first textual use of "Kubernetes" in the long description
- [ ] **No Kubernetes, LF, or CNCF logos in screenshots or the feature graphic**
- [ ] **No Ghostty logo in screenshots** — especially not in terminal screenshots
- [ ] Non-endorsement disclaimer present in the listing (see §1.4)
- [ ] `Kubernetes® is a registered trademark of LF Projects, LLC.` attribution
      present
- [x] Same disclaimer present in-app under Settings → About
- [ ] Pricing/payment disclosure accurate if IAP ships (Payments policy §6)

**Code and repo**
- [ ] No trademarked term in your domain name
- [x] Third-party notices reachable in-app (Settings → About → Open source licenses)
- [x] Ghostty MIT notice present in the shipped app
- [x] `client-go` consumed unmodified from upstream, so no per-file modification notices
      are owed (Apache-2.0 §4(b))
- [ ] No Ghostty fork content presented as upstream Ghostty

**If you need an exception**
- [ ] Written permission obtained from `trademarks@lfprojects.org` and retained
