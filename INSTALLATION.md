# Matrix (Synapse) — Installation Guide

This guide walks through deploying a **Matrix Synapse** homeserver with Docker
Compose and wiring it to the eXo/Meeds platform through this addon
(`io.meeds.chat`). It complements the property reference in
[`README.md`](README.md) with a full, ordered installation procedure.

## Architecture — read this first

Two distinct code paths talk to Synapse, and they have **different**
same-origin requirements:

- **Server-to-server (Java backend, `io.meeds.chat.service`)** — the eXo
  backend calls the Synapse **Client-Server** and **Admin** APIs directly
  (JWT login exchange, room/user provisioning, the shared-secret registration
  HMAC flow). This traffic never goes through a browser, so it can hit
  Synapse on an internal address (e.g. a Docker network hostname) and does
  **not** need to share an origin with the portal.
- **Browser (Vue app, `webapp/src/main/webapp/vue-apps/matrix`)** — the
  frontend hand-rolls Matrix Client-Server API calls (`/sync` long-poll,
  send/receive messages, media upload/thumbnails, receipts, pushers) using
  **relative URLs** such as `/_matrix/client/v3/sync`. There is no
  `matrix-js-sdk` and no configurable base URL: whatever origin serves the
  eXo portal page is the origin the browser will call for `/_matrix/...`.

**Consequence**: Synapse's Client-Server API must be reverse-proxied onto the
**exact same scheme+host+port** as the eXo portal, under the `/_matrix` path
(and `/_synapse/client` for some admin/client-facing helper endpoints). This
is the single most common source of "chat doesn't load" issues — it is not a
CORS problem (Synapse allows CORS by default), it is a same-origin **path
routing** problem: a relative-URL `fetch('/_matrix/...')` will silently 404
against the eXo portal itself if Synapse isn't reachable at that exact
origin.

```
Browser ──HTTPS──▶ reverse proxy (chat.acme.com)
                      ├── /               → eXo portal   (internal:8080)
                      └── /_matrix, /_synapse/client → Synapse (internal:8008)

eXo backend (Java) ──HTTP──▶ Synapse admin/client API (internal:8008, direct)
```

Keep this split in mind: `meeds.matrix.server.url` (below) is the **internal**
backend-to-backend address; `meeds.matrix.server.name` is the **public**
Matrix domain that must equal Synapse's `server_name` and the portal's own
public origin.

## Prerequisites

- Docker Engine + Docker Compose v2
- A DNS name for the Matrix/portal origin (e.g. `chat.acme.com`) — can be the
  same domain the eXo portal is already served from
- A reverse proxy able to route by path on that domain (nginx used below;
  Traefik/Apache work the same way)
- The eXo/Meeds platform with the `matrix` addon installed (see the
  `addons-manager` for the ZIP produced by `packaging`)

## Step 1 — Deploy Synapse with Docker Compose

Create a working directory (e.g. `matrix-server/`) with:

```
matrix-server/
├── docker-compose.yml
└── synapse-data/        # generated in the next step
```

`docker-compose.yml`:

```yaml
services:
  synapse-db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: synapse
      POSTGRES_PASSWORD: ${SYNAPSE_DB_PASSWORD}
      POSTGRES_DB: synapse
      POSTGRES_INITDB_ARGS: "--encoding=UTF8 --locale=C"
    volumes:
      - synapse-db-data:/var/lib/postgresql/data
    networks:
      - matrix

  synapse:
    image: matrixdotorg/synapse:v1.118.0   # pin an explicit version — do not track :latest
    restart: unless-stopped
    depends_on:
      - synapse-db
    environment:
      SYNAPSE_SERVER_NAME: chat.acme.com
      SYNAPSE_REPORT_STATS: "no"
    volumes:
      - ./synapse-data:/data
    ports:
      - "8008:8008"     # host-exposed on purpose, see note below
    networks:
      - matrix

networks:
  matrix:
    driver: bridge

volumes:
  synapse-db-data:
```

Set `SYNAPSE_DB_PASSWORD` in a sibling `.env` file (never commit it). Pin
`synapse`'s image tag to a specific release (check
[the release list](https://github.com/element-hq/synapse/releases)) rather
than `:latest` — an unattended `docker compose pull` on `:latest` can jump
several major versions at once and break the deployment without warning.

> **Why `8008` is published to the host**: the reverse proxy and the eXo
> backend both reach Synapse over the internal `matrix` network and don't
> need this mapping. It's kept here so you can point a standalone Matrix
> client (e.g. [Element](https://element.io)) at
> `http://<docker-host>:8008` directly for testing/debugging — logging in
> as the bot account or a provisioned user this way is a quick way to
> confirm Synapse itself works, independent of the eXo integration and the
> reverse proxy. Remove the mapping in a production deployment where the
> eXo backend runs inside the same Docker network and no direct client
> access is needed.

Generate the initial `homeserver.yaml` before the first `up` (this creates
`homeserver.yaml`, the signing key and log config under `synapse-data/`):

```bash
mkdir -p synapse-data
docker run -it --rm \
  -v "$(pwd)/synapse-data:/data" \
  -e SYNAPSE_SERVER_NAME=chat.acme.com \
  -e SYNAPSE_REPORT_STATS=no \
  matrixdotorg/synapse:v1.118.0 generate
```

## Step 2 — Configure `homeserver.yaml`

Edit `synapse-data/homeserver.yaml`, generated in the previous step.

**Database** — replace the default SQLite block with Postgres:

```yaml
database:
  name: psycopg2
  args:
    user: synapse
    password: "<value of SYNAPSE_DB_PASSWORD>"
    database: synapse
    host: synapse-db
    port: 5432
    cp_min: 5
    cp_max: 10
```

**Registration secret** — required by the addon's shared-secret admin
registration flow (`meeds.matrix.shared_secret_registration` below).
Generate a long random value once and keep it identical on both sides:

```yaml
registration_shared_secret: "<openssl rand -hex 32>"
```

**Disable open/public registration** — the addon provisions accounts itself
through the admin API, so the homeserver should not accept self-service
sign-up:

```yaml
enable_registration: false
enable_registration_without_verification: false
```

**JWT login** — the addon logs users in with `type: org.matrix.login.jwt`,
signing a JWT that carries **only** a `sub` (Matrix user id) and `exp` claim
— no issuer, no audience. Enable Synapse's native JWT support and leave
`jwt_issuer` / `jwt_audiences` **unset** (setting them causes Synapse to
reject these tokens, since they carry no `iss`/`aud`):

```yaml
jwt_config:
  enabled: true
  secret: "<same value as meeds.matrix.jwt.secret below>"
  algorithm: "HS256"
```

> **Minimum secret length**: the addon builds the signing key with jjwt's
> `Keys.hmacShaKeyFor(secret.getBytes())`, which **throws
> `WeakKeyException` at runtime** if the resulting byte array is shorter
> than 32 bytes (256 bits) for HS256. Generate the secret with something
> like `openssl rand -hex 32` (64 ASCII characters → 64 bytes, well above
> the minimum) rather than a short hand-picked string.

**Reverse proxy awareness** — since Synapse sits behind nginx (Step 3),
confirm the `client` listener has `x_forwarded: true`:

```yaml
listeners:
  - port: 8008
    tls: false
    type: http
    x_forwarded: true
    resources:
      - names: [client]
        compress: false
```

> Federation is not required for this integration (the addon only talks to
> its own homeserver). Leaving the `federation` listener resource out — or
> firewalling port 8448 — is recommended unless you specifically need
> cross-homeserver federation.

## Step 3 — Reverse proxy (same-origin routing)

nginx example putting the eXo portal and Synapse's Client-Server API on the
same public origin, per the [architecture note](#architecture--read-this-first)
above:

```nginx
server {
    listen 443 ssl http2;
    server_name chat.acme.com;

    # ssl_certificate / ssl_certificate_key ...

    location ~ ^(/_matrix|/_synapse/client) {
        proxy_pass http://synapse:8008;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Host $host;
        client_max_body_size 50M;      # media uploads
        proxy_read_timeout 300s;       # long-poll /sync
    }

    location / {
        proxy_pass http://exo-platform:8080;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Host $host;
    }
}
```

Start the stack:

```bash
docker compose up -d
docker compose logs -f synapse   # confirm it starts cleanly
```

## Step 4 — Create the bot/admin account

The addon acts as a Matrix user (`meeds.matrix.user.name`) with **server
admin** rights — it needs those to call the Synapse Admin API (register,
deactivate, override rate limits, room admin actions). Create it once,
directly on the Synapse container:

```bash
docker exec -it <synapse-container-name> bash
register_new_matrix_user -a -u exo -p '<a-strong-password>' -c /data/homeserver.yaml
```

Answer `yes` to "Make admin". The password itself is not used afterwards by
the addon (it authenticates via the JWT flow, not password login), but the
account must exist and be flagged admin.

## Step 5 — Configure `exo.properties`

Add to the eXo platform's `exo.properties` (or an equivalent
`system-properties` override):

```properties
# ── Matrix / Synapse chat integration ─────────────────────────────────
# Internal address the eXo backend uses to call Synapse's Client-Server
# and Admin APIs directly (server-to-server; does not need to match the
# public origin and does not go through the reverse proxy).
meeds.matrix.server.url=http://synapse:8008

# Public Matrix domain. Must equal Synapse's server_name (homeserver.yaml)
# AND the public origin the eXo portal is served from — the browser calls
# /_matrix/... with relative URLs, so this has to be the same origin as
# the portal, reverse-proxied to Synapse (see Step 3).
meeds.matrix.server.name=chat.acme.com

# Username of the Matrix account the backend uses to act as bot/admin
# (created in Step 4).
meeds.matrix.user.name=exo

# Display name for the bot account. Optional — defaults to "Chat Bot".
meeds.matrix.user.display.name=eXo Chat Bot

# Must equal homeserver.yaml's registration_shared_secret (Step 2).
meeds.matrix.shared_secret_registration=<same value as homeserver.yaml>

# Must equal homeserver.yaml's jwt_config.secret (Step 2).
# Minimum 32 bytes — a shorter secret makes the backend throw
# WeakKeyException at startup/login time (see Step 2 note).
meeds.matrix.jwt.secret=<same value as homeserver.yaml>

# Prefix prepended to eXo usernames that are purely numeric, since Matrix
# localparts have their own constraints. Optional — defaults to "u".
meeds.matrix.username.prefix=u

# Comma-separated eXo group ids excluded from chat/Matrix provisioning.
# Optional — no group excluded if unset.
meeds.matrix.restricted.users.groupId=/platform/externals
```

Restart the eXo server so `MatrixService` picks up the properties (they are
read via `PropertyManager`, evaluated at `@PostConstruct` startup and again
per-call — a restart is the safest way to confirm they took effect).

## Step 6 — Verify

1. **Synapse reachable internally**: from the eXo host,
   `curl http://synapse:8008/health` → `OK`.
2. **Reverse proxy routes correctly**: from outside,
   `curl https://chat.acme.com/_matrix/client/versions` → a JSON list of
   supported spec versions (not the eXo portal's HTML/404).
3. **Backend init succeeded**: check the eXo server log for `MatrixService`
   startup — no `MATRIX_SERVER_URL_IS_REQUIRED` /
   `MATRIX_ADMIN_USERNAME_IS_REQUIRED` errors, and no exception marking the
   service unavailable.
4. **End-to-end**: log in to the portal as a regular user and open the chat
   application — a Matrix session should establish and the `/sync` loop
   should start (visible in the browser's network tab as recurring
   `/_matrix/client/v3/sync` calls returning `200`).

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `MATRIX_SERVER_URL_IS_REQUIRED` at startup | `meeds.matrix.server.url` missing/blank |
| `MATRIX_ADMIN_USERNAME_IS_REQUIRED` at startup | `meeds.matrix.user.name` missing/blank |
| Chat UI never connects, `/_matrix/...` calls 404 in the browser | Reverse proxy not routing `/_matrix` (and `/_synapse/client`) on the portal's own origin — see Step 3 |
| JWT login rejected by Synapse (`M_UNAUTHORIZED` / `M_FORBIDDEN`) | `meeds.matrix.jwt.secret` and `homeserver.yaml`'s `jwt_config.secret` don't match, or `jwt_issuer`/`jwt_audiences` were set in `homeserver.yaml` (must stay unset — the addon's tokens carry no `iss`/`aud`) |
| New user provisioning fails / HMAC mismatch on registration | `meeds.matrix.shared_secret_registration` and `homeserver.yaml`'s `registration_shared_secret` don't match |
| Admin API calls (deactivate, rate-limit override, room admin) return 403 | The `meeds.matrix.user.name` account was not created with `-a` (admin) in Step 4 |
| Media upload fails at a fixed size | `client_max_body_size` too low on the reverse proxy, or Synapse's `max_upload_size` |
| `WeakKeyException` at backend startup or on user login | `meeds.matrix.jwt.secret` (or `homeserver.yaml`'s `jwt_config.secret`) is shorter than 32 bytes |

## Appendix — Backups

Two things make a Synapse deployment recoverable:

- **`synapse-db-data` volume** (Postgres) — all rooms, messages, accounts,
  device/E2EE state. Back it up like any production database (e.g.
  `pg_dump`/`pg_basebackup` on a schedule), not just as a raw volume
  snapshot taken while the container is running.
- **`synapse-data/homeserver.signing.key`** — the server's long-term
  identity key. Losing it doesn't lose message history, but the server
  will generate a new one on next start and other servers/clients that
  cached the old key may distrust it; back it up once and keep it with
  the same care as the secrets in `homeserver.yaml`.

## Appendix — Enabling email in Synapse

Only needed if you want Synapse itself to email new users (password reset,
3PID verification), independent of eXo's own notification system. Add to
`homeserver.yaml`:

```yaml
enable_3pid_changes: true
email:
  smtp_host: mail.acme.com
  smtp_port: 25
  force_tls: false
  require_transport_security: false
  enable_tls: false
  notif_from: "Your Friendly %(app)s homeserver <noreply@acme.com>"
  app_name: eXo Matrix Chat
  enable_notifs: true
  notif_for_new_users: true
  client_base_url: "https://chat.acme.com"
  validation_token_lifetime: 15m
  invite_client_location: https://app.element.io
```

## Appendix — Linking an existing space to a Matrix room

A REST endpoint links an eXo space to a Matrix room (creating the room if
needed):

```
GET /portal/rest/matrix/linkroom?spaceGroupId=<groupId>&roomId=<roomId>&create=true
```

- `spaceGroupId` (required) — the space's group id, taken from its URL
  between `:spaces:` and the next `/` (e.g. `support_team` for
  `/portal/g/:spaces:support_team/`).
- `roomId` (optional) — an existing Matrix room's technical id.
- `create` — ignored if `roomId` is set; when `true`, creates a new room for
  the space. Defaults to `false`.
