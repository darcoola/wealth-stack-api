# Deploying to Oracle Cloud Free Tier

One Ampere A1 VM running the whole stack under Docker Compose: Caddy (TLS) → the Spring app +
Keycloak, backed by Postgres and Elasticsearch. Everything here fits in the **Always Free** tier —
no cost, no time limit.

| File | Role |
| --- | --- |
| `Dockerfile` | Multi-stage build (Gradle + Angular → fat jar → JRE image) |
| `compose.prod.yml` | The production stack. Separate from `compose.yaml`, which is dev-only |
| `Caddyfile` | TLS termination + routing: `/auth/*` → Keycloak, everything else → the app |
| `.env.prod.example` | Template for the secrets/config you fill in on the server |
| `src/main/resources/application-prod.yml` | `prod` Spring profile; all values come from env vars |

---

## 1. Create the VM

In the OCI console: **Compute → Instances → Create instance**.

- **Image:** Ubuntu 24.04 (Minimal is fine)
- **Shape:** `VM.Standard.A1.Flex` — set it to **4 OCPU / 24 GB RAM** (that is the *entire* Always
  Free Ampere allocation; use it all, since a spare 1 GB x86 instance cannot run this stack).
- **SSH key:** upload your public key.
- Note the **public IP** it gets assigned.

> **"Out of host capacity"** is the normal experience when creating A1 instances — the free Ampere
> pool is frequently exhausted in popular regions. Just retry (a different availability domain, or
> the same one a few hours later). It is not a configuration error on your side.

## 2. Open the firewall — *both* of them

This is the classic Oracle gotcha. There are two independent firewalls and you must open both.

**a. OCI security list** (the cloud-side one): VCN → your subnet → Security List → *Add Ingress
Rules*. Source `0.0.0.0/0`, IP protocol TCP, destination ports **80** and **443**.

**b. The VM's own iptables.** Oracle's Ubuntu images ship with a default `iptables` policy that
drops everything except SSH — so ports 80/443 stay closed even after step (a). SSH in and run:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Install Docker

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER
newgrp docker   # or just log out and back in
```

## 4. Get the code onto the VM

```bash
git clone <your-repo-url> wealth-stack
cd wealth-stack
```

## 5. Configure

```bash
cp .env.prod.example .env.prod
nano .env.prod
```

Fill in:

- **`APP_HOST`** — your public hostname. Using **sslip.io** you need no domain and no DNS setup at
  all: the hostname literally encodes the IP. For public IP `130.61.42.7`, that is
  **`130-61-42-7.sslip.io`** (dashes, not dots). Let's Encrypt issues a real certificate for it, so
  HTTPS is genuine — which the app *requires*, because Keycloak's PKCE (Web Crypto), the Google
  login redirect, and the PWA service worker all refuse to run outside a secure context.
- **`POSTGRES_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`** — generate each with
  `openssl rand -base64 24`.
- **`WEALTHSTACK_OWNER_EMAIL`** — your Google address. Get this right *before* your first sign-in
  (see step 8).

## 6. Launch

```bash
docker compose -f compose.prod.yml --env-file .env.prod up -d --build
```

The first build compiles Kotlin and the Angular UI on the VM — expect **10–20 minutes** on 4
Ampere cores. Watch it come up:

```bash
docker compose -f compose.prod.yml --env-file .env.prod logs -f
```

Caddy fetches the certificate on first request. Then open `https://<APP_HOST>` — you should get the
login page. The seeded `dev` / `dev` Keycloak user works immediately, so you can verify the whole
path before touching Google.

## 7. Wire up Google login

In the [Google Cloud Console](https://console.cloud.google.com/apis/credentials) create an **OAuth
2.0 Client ID** (type: Web application) and set the **Authorized redirect URI** to exactly:

```
https://<APP_HOST>/auth/realms/wealthstack/broker/google/endpoint
```

Put the resulting id/secret into `.env.prod` (`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`), then:

```bash
docker compose -f compose.prod.yml --env-file .env.prod up -d keycloak
```

> The realm was already imported on first boot, so this restart will **not** re-read the realm file
> — update the Google credentials in the Keycloak admin console instead
> (`https://<APP_HOST>/auth` → *Identity providers → google*). Alternatively, if you have no data
> yet, wipe and re-import: `docker compose -f compose.prod.yml --env-file .env.prod down -v`.

## 8. Claim your data (do this on your very first sign-in)

`WEALTHSTACK_OWNER_EMAIL` is the bootstrap hook: **the first login with that email adopts party 1**
and every operation imported before multi-user support came in. Any other account signing in first
just gets its own empty personal party. So make sure the value is right *before* anyone logs in.

Sign-up is approval-gated by design — a new account can authenticate but sees a "pending approval"
page until it is granted the `wealthstack-user` realm role. Approve accounts (including your own,
the first time) in the Keycloak admin console:

`https://<APP_HOST>/auth` → *Users* → select → *Role mapping* → **Assign** `wealthstack-user`.

## 9. Restore your existing data (optional)

If you have data in your local Postgres you want to carry over:

```bash
# locally
docker exec wealthstack-postgres pg_dump -U wealthstack wealthstack > dump.sql
scp dump.sql ubuntu@<VM_IP>:~/

# on the VM
docker exec -i wealthstack-postgres psql -U wealthstack -d wealthstack < ~/dump.sql
```

Then re-index Elasticsearch so category auto-suggestions work — once per party:
`POST /api/v1/bank-statements/operations/sync`.

---

## Operating it

```bash
# always pass both flags — otherwise Compose reads compose.yaml (the DEV stack) instead
alias wsc='docker compose -f compose.prod.yml --env-file .env.prod'

wsc ps                  # status
wsc logs -f app         # tail the app
wsc up -d --build app   # redeploy after a git pull
wsc down                # stop (volumes, and therefore data, survive)
```

**Backups.** The database is the only thing that matters; everything else is rebuildable.

```bash
docker exec wealthstack-postgres pg_dump -U wealthstack wealthstack | gzip > ws-$(date +%F).sql.gz
```

Worth a cron entry, plus copying the dump off the box — an Always Free VM can be reclaimed by
Oracle if it is idle for long stretches.

## Hardening worth doing once it works

- **Turn off direct access grants.** The realm enables them for curl-based dev testing
  (`directAccessGrantsEnabled: true`), which lets anyone exchange a username/password for a token
  directly. In the admin console: *Clients → wealthstack-web → Settings → uncheck Direct access
  grants*.
- **Remove the seeded `dev` / `dev` user** once Google login works — it ships with the
  `wealthstack-user` role already assigned, meaning it is pre-approved.
- **Restrict the Keycloak admin console.** `/auth/admin` is currently exposed to the internet.
  Either add an IP allowlist in the `Caddyfile` or bind it to SSH-tunnel-only access.

## Moving to a real domain later

Change `APP_HOST` in `.env.prod`, point an A record at the VM's IP, then `wsc up -d`. Caddy issues
the new certificate on its own. Two things do **not** follow automatically: add the new origin to
the Keycloak client's redirect URIs (admin console → *Clients → wealthstack-web*), and update the
redirect URI in the Google console.
