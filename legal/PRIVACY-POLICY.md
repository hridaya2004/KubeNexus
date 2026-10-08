# Privacy Policy — KubeNexus

**Last updated: 9 October 2026**

---

## 1. Who we are

KubeNexus ("the app") is published by **Hridaya Prajapati** ("we", "us"), an
independent developer. KubeNexus is **not** affiliated with, endorsed by, or
sponsored by The Linux Foundation, the Kubernetes® project, or the Cloud Native
Computing Foundation.

- **App name:** KubeNexus
- **Play package:** `dev.hridaya.kubenexus`
- **Source code:** <https://github.com/hridaya2004/kubenexus>
- **Privacy contact:** `info.hridayaprajapati@gmail.com`

We do not operate a server, an account system, or a telemetry service.

## 2. The short version

**Your cluster credentials and everything about your clusters stay on your
device.** The app talks directly from your phone to your Kubernetes API server.
Your data is never routed through us, because we do not exist as a service.

| | |
|---|---|
| Do we receive your data? | **No.** We operate no server, so there is nowhere for it to reach us. |
| Does anything leave your device? | **Only if you choose to**, when you upload pod logs to dpaste.org (section 6). Everything else stays on your device. |
| Is anything sent to a third party? | **Only that same upload**, and only after you confirm it. |
| Do we run a server? | **No.** |
| Do we show ads? | **No.** |
| Do we use third-party analytics or crash SDKs? | **No.** |
| Do we ask you to create an account? | **No.** |

The distinction between the first two rows matters, because it is the distinction Google Play
draws too. Play counts data as "collected" whenever it is transmitted off a device, including
to a third party — not only to the developer. So the one upload above is declared in our Play
Data safety listing as **App activity → Other user-generated content**: collected, not shared,
optional, used for app functionality, and not processed ephemerally because dpaste.org keeps the
paste for 7 days. Nothing else is declared.

## 3. What the app stores on your device

KubeNexus keeps the following **locally**, in the app's private storage on your
device only:

| Data | What it is | Why | Where it lives |
|---|---|---|---|
| **Cluster credentials** | The kubeconfig you paste or import: server URL, context, and authentication material such as client certificates or bearer tokens | To connect to your cluster | `kubenexus.db`, encrypted with AES-256-GCM using a key held in the Android Keystore |
| **Cluster details** | Cluster name, server URL, context, user and namespace, shown in the cluster list | To show and switch between your clusters | `kubenexus.db` |
| **Cluster cache** | Namespaces, pods, deployments, services and API schemas read from your cluster | To display your resources offline and quickly | `kubenexus.db` |
| **App logs** | Diagnostic lines the app itself writes while running | For you to inspect and share when troubleshooting | Android's log buffer for this app |
| **Exported log files** | Log files you share as a file | So the share sheet can hand them to the app you pick | App cache; files older than an hour are deleted the next time you share, and Android may clear the cache at any time |
| **Preferences** | Theme (light/dark/system) | To remember your choice | Android DataStore |

Cluster credentials are **authentication information**, which Google Play treats
as personal and sensitive user data. Because you supply it deliberately, for the
app's core purpose, we treat it as the most sensitive thing on the device.

### What we specifically do *not* do

- We do **not** collect, transmit, or upload your kubeconfig, credentials, or
  cluster data to us. There is no destination for it to be sent to.
- We do **not** read your device's IMEI, IMSI, SIM serial, Android Advertising
  ID, or any persistent device identifier.
- We do **not** collect your contacts, photos, files, location, microphone,
  camera, SMS, or call logs.
- We do **not** build a fingerprint of you.
- We do **not** embed advertising, analytics, or crash-reporting SDKs.

## 4. Where your data goes on the network

When you use the app, your device connects **directly to the Kubernetes API
server named in your kubeconfig**. We are not in that path. The app makes no
other network connections, except the optional log upload described in
section 6.

- **App updates.** When you open the app, it asks the Google Play Store app on
  your device whether a newer version of KubeNexus is available, using Google's
  Play In-App Updates library. To answer, Google Play uses your device's
  metadata, the installed app version and the list of installed app modules.
  Google states that this data is encrypted, is not shared with third parties
  and is deleted after a fixed retention period. If you agree to update, Google
  Play downloads and installs it. None of this reaches us. If the app was not
  installed from Google Play, no check happens.

- **Encryption.** The app's Kubernetes client only connects to API servers over
  HTTPS (TLS) and refuses plain `http://` server addresses. The server's
  certificate is checked against the certificate authority in your kubeconfig,
  or against the device's system certificate authorities if the kubeconfig does
  not include one. If your kubeconfig sets `insecure-skip-tls-verify: true`, the
  connection is still encrypted, but the server's identity is not verified; that
  choice is made by your kubeconfig.
- **Credential plugins.** Kubeconfigs that authenticate through an `exec`
  credential plugin or an `auth-provider` are not supported: the app does not run
  commands named in a kubeconfig.
- **Proxies.** If your kubeconfig names a `proxy-url`, connections to that
  cluster go through that proxy.

If you are on a managed or corporate network, your administrator may be able to
see connection metadata. That is between you and them.

## 5. Port forwarding and background operation

KubeNexus can keep Kubernetes **port-forward tunnels** open while the app is in
the background, using an Android foreground service. While a tunnel is open:

- it listens only on `127.0.0.1` on your device and carries traffic between that
  address and a pod in your cluster. Other apps on the same device can connect
  to it; other devices cannot;
- **we do not observe, log, proxy, or record** any traffic that passes through it;
- an ongoing notification lists open tunnels and offers **Stop All**, if you
  allow KubeNexus to post notifications. Either way, open tunnels are listed and
  can be stopped from the port-forward sessions sheet inside the app;
- Android limits this kind of background work to about 6 hours a day. When the
  limit is reached, KubeNexus closes its tunnels and, if notifications are
  allowed, tells you why.

Port forwarding is a first-party feature of the app for your own clusters. The
app is not a proxy for third parties, and we do not offer, sell, or broker
access to anyone else's networks.

## 6. Sharing and uploading logs — you control it

Nothing leaves the device unless you ask for it.

- **Share as a file.** Pod logs and the app's own logs can be shared as a text
  file through the standard Android share sheet, so you choose the destination.
  Files you share are then governed by whichever app or service you send them
  to.
- **Upload to dpaste.org.** From a pod's Logs tab you can choose "Upload to
  dpaste.org…". Before anything is sent, the app asks you to confirm and tells
  you that the logs shown will be sent to **dpaste.org**, a public paste service
  that we do not operate, and that **anyone with the link can read them for
  7 days**. Tokens, passwords and keys that KubeNexus recognises are redacted
  before upload, but redaction cannot catch every secret, and logs can contain
  personal or confidential data from your workload. Once uploaded, the paste is
  kept and deleted by dpaste.org under its own terms; the app cannot delete it
  sooner. We never receive the logs or the link.

  This is the only user data KubeNexus declares in its Google Play Data safety
  section (see section 2). We cannot delete a paste on request: dpaste.org expires
  it after 7 days, and the app has no way to reach it sooner.

## 7. Purchases, if we add them

*KubeNexus is currently free and does not sell anything. This section describes
what will be true if in-app purchases are added, and must be updated to match
reality at the moment you add them.*

- Any paid feature sold **inside the app on Google Play** will be processed
  entirely by **Google Play Billing**.
- We never see, receive, or store your card number or full payment credentials.
  Google acts as the merchant of record and handles the transaction data.
- We receive only the minimum Google provides to confirm a purchase (such as a
  purchase token, package name, and an acknowledgement that an item was
  purchased or not).
- Your purchase is linked to your **Google Play account**, not to a KubeNexus
  account — we do not operate accounts.
- Pricing shown in the app matches Google Play's billing interface exactly.
- You can restore a purchase on a new device using your Google Play account, and
  you can request a refund through Google Play.
- We do not use alternative payment methods inside the app, and we do not link
  you to any external checkout.

## 8. Data security

- **Encryption in transit:** cluster connections use TLS, as described in
  section 4; the optional dpaste.org upload uses HTTPS.
- **Encryption at rest:** stored kubeconfigs are encrypted with a key kept in the
  Android Keystore (hardware-backed where the device supports it). The rest of
  the database is protected by the Android application sandbox, which the
  operating system isolates from other apps.
- **Logs:** the app redacts tokens, passwords, keys and certificates it
  recognises from the error details it writes to its own diagnostic log.
- **Backups:** the database is **explicitly excluded** from Android cloud backup
  and from device-to-device transfer. Your kubeconfig will not be copied into
  Google's backup service or migrated to a new phone by the system.
- **Clipboard:** when you copy logs, terminal output or error details, the app
  marks them as sensitive so Android does not show them in its clipboard preview.
- **Screenshots:** the app does not set `FLAG_SECURE`, so you can take
  screenshots. If you would prefer it not be possible to capture cluster
  credentials on screen, tell us and we will consider enabling it.
- **No processors on our side.** We have no servers, sub-processors or vendors
  that receive your data. The only third party the app can send data to is
  dpaste.org, and only when you choose to upload logs there (section 6).

## 9. Data retention and deletion

We retain nothing, because we receive nothing.

**Data on your device** is retained until you remove it. You can delete it at any
time:

- **Delete a single cluster** (Manage Clusters): removes its stored credentials
  and all data cached from it.
- **Clear cached cluster data** (Settings → Data): removes everything cached
  from every cluster while keeping your clusters and their credentials.
- **Clear app logs** (Settings → Diagnostics → Logcat, then the clear button):
  clears the app's own log buffer.
- **Uninstall KubeNexus:** removes all app data, including `kubenexus.db`.
- **Revoke access:** the fastest way to invalidate a leaked credential is to
  revoke the associated token or certificate in your cluster, from any client.

Because the backup exclusions above keep the database out of Android backup,
uninstalling also leaves nothing behind in a backup. Logs you uploaded to
dpaste.org are deleted by dpaste.org after 7 days (section 6).

## 10. Account deletion

KubeNexus **does not create accounts** and holds no server-side user data, so
there is no account for us to delete. The equivalent user control is the local
deletion described in section 9, which is available at all times from within the
app. This satisfies the intent of Google Play's account-deletion requirement;
because no account exists, there is no associated server data to remove.

If a future version introduces a hosted account or sync feature, this section
**must** be rewritten before that feature ships, to provide in-app deletion plus
a public web deletion request form.

## 11. Children

KubeNexus is intended for adults operating infrastructure they are responsible
for. It is **not** directed at children, and we do not knowingly collect data
from children. If you believe a child has provided us with personal data,
contact `info.hridayaprajapati@gmail.com` and we will help.

## 12. Your rights

Depending on where you live, privacy law may give you rights to access, correct,
delete, or export your personal data, or to object to its processing.

In practice, because your data stays on your device unless you choose to share
it, **you already hold complete control**: it is in an app sandbox only you can
read, and you can erase it at any time using the controls in section 9. For logs
you uploaded to dpaste.org, contact dpaste.org.

If you still have a question or a formal request, email
`info.hridayaprajapati@gmail.com`. We will respond within 30 days.

Because we do not operate a server and hold no records, we cannot retrieve data
"about you" from anywhere — there is nowhere it is held by us.

## 13. International transfers

We do not transfer personal data internationally, because we receive none of it — there is no
server here for data to reach. Your device talks to your own cluster, wherever that cluster
happens to be. The one exception is the upload you choose in section 6: those logs are stored
wherever dpaste.org operates, under dpaste.org's terms, not ours.

If you are in the EEA, UK, or Switzerland, note that the app's handling of data
on your device remains under your control and is not a transfer by us.

## 14. Third-party components

KubeNexus includes open-source software, including the Kubernetes Go client and
the Ghostty terminal emulator core. These components run **on your device** and
none of them transmit your data to their authors or to anyone else. The app's
**Settings → About → Open source licenses** screen lists every component with its
licence; see also [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## 15. Changes to this policy

If we change this policy — for example because a new version of the app collects
something it does not collect today, or because in-app purchases are added — we
will:

1. update the "Last updated" date,
2. change the app version's description or release notes to note the change, and
3. if the change is material, notify you in the app before it takes effect.

Material changes will not be applied retroactively without notice.

## 16. Contact

- **Privacy questions:** `info.hridayaprajapati@gmail.com`
- **Security reports:** `info.hridayaprajapati@gmail.com`
- **Source code:** <https://github.com/hridaya2004/kubenexus>
- **Published at:** <https://github.com/hridaya2004/kubenexus/blob/main/legal/PRIVACY-POLICY.md>
  (must be a public, non-geofenced, non-PDF URL; the app links to this address
  from Settings → About → Privacy policy)
