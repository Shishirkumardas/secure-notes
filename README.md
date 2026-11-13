# Secure Notes

A note-taking app with a JSON API and a small browser client. Users keep private notes. Admins can read every note and manage accounts. Every signed-in user can read and write posts.

The stack is Node.js, Express, MongoDB, Mongoose, and JWT. Passwords are hashed with bcrypt.

## Run locally

Copy the sample environment file and change the admin password:

```bash
copy .env.example .env
```

Start the app and MongoDB together:

```bash
docker compose up --build
```

Open http://localhost:3000. `docker-compose.override.yml` publishes the app on port 3000 and MongoDB on `127.0.0.1:27017`. That override is for this machine. Coolify deploys `docker-compose.yml` only.

To run the Node process on the host instead, start only MongoDB with `docker compose up mongo`, then:

```bash
npm install
npm start
```

The first boot creates an admin from `ADMIN_EMAIL` and `ADMIN_PASSWORD` when that email is not already in the database. The sample file uses `admin@example.com` / `ChangeMeAdmin1`. Change both before you deploy.

`npm test` runs the API tests against an in-memory MongoDB. It does not use your `.env` database.

## Accounts and permissions

| Action | User | Admin |
| --- | --- | --- |
| Create, edit, delete, and list their own notes | Yes | Yes |
| Read someone else's notes | No | Yes |
| Edit or delete someone else's notes | No | No |
| Create and edit their own posts | Yes | Yes |
| Read every post | Yes | Yes |
| Create, edit, delete, and list users | No | Yes |
| See users grouped by interest | No | Yes |

Public registration always creates a `user`. An admin creates other admins. An admin cannot delete their own account or change their own role, so the current admin cannot lock themselves out. Changing a password invalidates that account's existing tokens.

## API

Send `Authorization: Bearer <token>` except on register, login, and health.

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/auth/register` | Public |
| POST | `/api/auth/login` | Public |
| GET | `/api/auth/me` | Signed in |
| GET, POST | `/api/notes` | Signed in |
| GET, PUT, DELETE | `/api/notes/:id` | Owner, admin can GET |
| GET, POST | `/api/posts` | Signed in |
| GET, PUT, DELETE | `/api/posts/:id` | Signed in, author can change |
| PUT | `/api/users/me` | Signed in |
| GET, POST | `/api/users` | Admin |
| GET, PUT, DELETE | `/api/users/:id` | Admin |
| GET | `/api/users/interests` | Admin |
| GET | `/api/users/:id/posts` | Signed in |
| GET | `/api/health` | Public |

List endpoints take `page` and `limit`. `limit` defaults to 10 and caps at 50. The response shape is `{ data, pagination }`.

Interests are an array of strings, such as `{ "interests": ["chess", "reading"] }`. The API trims them, lowercases them, and drops duplicates.

### Group users by interest

`GET /api/users/interests` runs exactly one `collection.aggregate()`. The pipeline matches interests, unwinds the array, groups by interest, and paginates with `$facet`.

### Posts for one user

`GET /api/users/:id/posts` runs one aggregation with one `$lookup` from `users` to `posts` on `author`.

## Indexes

Indexes are declared with `schema.index` and synced on startup. `_id` is MongoDB's default index and is not declared again. Reads of one user, note, or post by id use that index.

| Collection | Index | Query it serves |
| --- | --- | --- |
| users | `{ email: 1 }` unique | Login `findOne({ email })` |
| users | `{ createdAt: -1 }` | Admin user list, newest first |
| users | `{ interests: 1 }` | Group-by-interests `$match` |
| notes | `{ owner: 1, createdAt: -1 }` | A user's notes, newest first |
| notes | `{ createdAt: -1 }` | Admin list of every note, newest first |
| posts | `{ author: 1 }` | `$lookup` on `author`, and delete-by-author |
| posts | `{ createdAt: -1 }` | Post list for every user, newest first |

The note indexes are separate on purpose. A user's list filters by `owner` and then sorts by `createdAt`, so `owner` has to be the prefix. The admin list has no owner filter and sorts by `createdAt`, so that field has to be the prefix. One compound index cannot serve both shapes. The same split is why posts have `{ author: 1 }` for the lookup and `{ createdAt: -1 }` for the global list.

These fields are not indexed: role, name, title, content, `tokenVersion`, and `updatedAt`. No query filters or sorts on them. `tokenVersion` is checked in memory after the user is loaded by `_id`.

## Deploy on Coolify

`docker-compose.yml` builds the app and runs MongoDB 7 beside it. The app listens on `0.0.0.0:3000`. Coolify's proxy reaches that port. `/api/health` returns 200 while the process is up, and `ok` is true after MongoDB is connected. Both services define health checks.

1. In Coolify, create a new resource from this GitHub repository.
2. Choose the **Docker Compose** build pack. Base directory `/`. Compose file `docker-compose.yml`.
3. Coolify reads `SERVICE_URL_APP_3000` and assigns a domain to the `app` service on port 3000. Open **Domains** and set the service to `app` and the container port to `3000`. The saved value looks like `http://your-domain:3000`. The `:3000` is only the container port. Open the address without `:3000`. A domain with no port makes Traefik return `404 page not found`.
4. Open **Environment Variables** and set the three runtime values below. They are not build arguments. The image build does not receive them. The process checks `JWT_SECRET` when the container starts.

| Name | Required | Value |
| --- | --- | --- |
| `JWT_SECRET` | Yes | A long random string, at least 16 characters |
| `ADMIN_EMAIL` | Yes | Admin login email |
| `ADMIN_PASSWORD` | Yes | Admin password, at least 8 characters |
| `ADMIN_NAME` | No | Defaults to `Admin` |
| `JWT_EXPIRES_IN` | No | Defaults to `7d` |

The database URI inside Compose is `mongodb://mongo:27017/secure_notes`. MongoDB is not published to the internet. Its data is stored in the `mongo_data` volume.

5. Deploy. The first boot creates the admin user when that email is not already in the database. Sign in, then create a normal user and check notes, posts, interests, and the per-user posts view.

To deploy the app image alone, choose the **Dockerfile** build pack, set **Ports Exposes** to `3000`, and set the health check path to `/api/health`. Supply `MONGODB_URI` for a Coolify MongoDB database or any other MongoDB host, plus the same JWT and admin variables. Do not commit `.env`.
