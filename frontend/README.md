# Secure Notes

Browser client for the secure notes API. It is plain HTML, CSS, and JavaScript. There is no framework and no template engine.

The page calls `/api/...` on the same host. nginx proxies those requests to the Spring Boot API.

## Run with the API

From the deploy repository:

```bash
docker compose up --build
```

Open http://localhost:3000. The sample admin is `admin@example.com` / `ChangeMeAdmin1` until you change `ADMIN_EMAIL` and `ADMIN_PASSWORD`.

## What it does

Sign in or register, then use Notes, Posts, and Profile. An admin also gets Users, interest groups, and the posts written by one user. Notes stay with their owner. An admin can read another person's note and cannot edit it. Posts are visible to every signed-in user, and only the author can change a post.
