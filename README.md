# Secure Notes

A note-taking app with a Spring Boot JSON API and a small browser client. Users keep private notes. Admins can read every note and manage accounts. Every signed-in user can read and write posts.

- `backend/` is the Java 17 Spring Boot API.
- `frontend/` is the vanilla browser client. nginx serves it and proxies `/api` to the API.
- `docker-compose.yml` runs both, plus MongoDB 7, for Coolify.

The API behavior, indexes, and the two aggregation pipelines are documented in `backend/README.md`.

## Run locally

```bash
docker compose up --build
```

Open http://localhost:3000. The override file publishes the website on port 3000, the API on port 8080, and MongoDB on `127.0.0.1:27017`. Coolify deploys `docker-compose.yml` only and does not use that override.

The first boot creates an admin from `ADMIN_EMAIL` and `ADMIN_PASSWORD` when that email is not already in the database. The defaults are `admin@example.com` / `ChangeMeAdmin1`. Change both before a real deployment. The seed does not reset an existing password.

To run the API on the host instead, start only MongoDB with `docker compose up mongo`, then from `backend/`:

```bash
mvnw.cmd spring-boot:run
```

`mvnw.cmd test` runs the API tests against an embedded MongoDB.

## Deploy on Coolify

Redeploy this repository with the Docker Compose build pack. Base directory `/`. Compose file `docker-compose.yml`.

1. The `app` service is the website on container port 3000. The saved domain should look like `http://your-domain:3000`. The `:3000` is only the container port. Open the site without `:3000`.
2. The `api` service is the Spring Boot API on container port 8080.
3. Leave `JWT_SECRET` empty so Coolify generates one. If a value is already saved, it must be at least 16 characters. A shorter saved value is kept and the API will reject sign-in until you delete it and redeploy.

| Name | Required | Value |
| --- | --- | --- |
| `JWT_SECRET` | No | Empty uses a generated secret. Any typed value must be at least 16 characters |
| `ADMIN_EMAIL` | No | Defaults to `admin@example.com` |
| `ADMIN_PASSWORD` | No | Defaults to `ChangeMeAdmin1` |
| `ADMIN_NAME` | No | Defaults to `Admin` |
| `JWT_EXPIRES_IN` | No | Defaults to `7d` |

The database URI inside Compose is `mongodb://mongo:27017/secure_notes`. MongoDB is not published to the internet. Its data stays in the `mongo_data` volume.

`GET /api/health` returns HTTP 200 while the API process is up. `ok` is true after MongoDB is connected.

This Coolify app is wired for:

- Website: `http://app-ctijtqlbwc55j0icbkd7yv3w.169.58.157.13.sslip.io/`
- API: `http://api-ctijtqlbwc55j0icbkd7yv3w.169.58.157.13.sslip.io/`

The website proxies `/api` to the API container, so the browser keeps using relative `/api` paths. The API host answers directly at `/api/health`.
