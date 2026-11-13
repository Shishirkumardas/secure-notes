# Secure Notes API

JSON API for the secure notes app. Users keep private notes. Admins can read every note and manage accounts. Every signed-in user can read and write posts.

The stack is Java 17, Spring Boot 3, Spring Security, MongoDB, and JWT. Passwords are hashed with bcrypt at cost 10.

## Run locally

Java 17 is required. MongoDB has to be listening on `127.0.0.1:27017`, or set `MONGODB_URI`.

```bash
mvnw.cmd test
mvnw.cmd spring-boot:run
```

On macOS or Linux, use `./mvnw` instead of `mvnw.cmd`. The API listens on `http://127.0.0.1:8080`. `GET /api/health` returns `{ "ok": true, "db": "connected" }` after MongoDB answers.

Copy the sample values from the deploy repo's `.env.example` if you want a seeded admin. The seed runs only when that email is not already stored. It does not reset an existing password.

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

Public registration always creates a `user`. An admin creates other admins. An admin cannot delete their own account or change their own role. Changing a password invalidates that account's existing tokens. The role is read from the database on every request.

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

List endpoints take `page` and `limit`. `limit` defaults to 10 and caps at 50. The response shape is `{ data, pagination }`. Errors are `{ error }`.

Interests are trimmed, lowercased, and de-duplicated. Empty strings are dropped. A profile holds at most 20 interests.

### Group users by interest

`GET /api/users/interests` runs one aggregation on `users`. The pipeline matches interests, unwinds the array, groups by interest, and paginates with `$facet`.

### Posts for one user

`GET /api/users/:id/posts` runs one aggregation with one `$lookup` from `users` to the `posts` collection on `author`.

## Indexes

Indexes are created when the API connects. `_id` is MongoDB's default and is not declared again.

| Collection | Index | Query it serves |
| --- | --- | --- |
| users | `{ email: 1 }` unique | Login by email |
| users | `{ createdAt: -1 }` | Admin user list, newest first |
| users | `{ interests: 1 }` | Group-by-interests match |
| notes | `{ owner: 1, createdAt: -1 }` | A user's notes, newest first |
| notes | `{ createdAt: -1 }` | Admin list of every note, newest first |
| posts | `{ author: 1 }` | Lookup on `author`, and delete-by-author |
| posts | `{ createdAt: -1 }` | Post list, newest first |

These fields are not indexed: role, name, title, content, `tokenVersion`, and `updatedAt`. `tokenVersion` is checked in memory after the user is loaded by `_id`.

## Docker

`Dockerfile` builds a Java 17 image. It listens on `0.0.0.0:8080`. `GET /api/health` returns HTTP 200 while the process is up. `ok` is true only after MongoDB answers. The container keeps running if MongoDB is still starting.
