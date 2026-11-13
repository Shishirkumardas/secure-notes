process.env.JWT_SECRET = "test-secret-value-long";
process.env.JWT_EXPIRES_IN = "1h";
process.env.NODE_ENV = "test";

const fs = require("fs");
const path = require("path");
const assert = require("node:assert/strict");
const { before, after, beforeEach, describe, test } = require("node:test");
const mongoose = require("mongoose");
const { MongoMemoryServer } = require("mongodb-memory-server");
const request = require("supertest");

const app = require("../src/app");
const connectDB = require("../src/config/db");
const User = require("../src/models/User");
const Note = require("../src/models/Note");
const Post = require("../src/models/Post");

let mongo;
let sequence = 0;

function nextEmail(prefix) {
  sequence += 1;
  return `${prefix}${sequence}@example.com`;
}

function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

async function registerUser(overrides) {
  const body = {
    name: "Ada Lovelace",
    email: nextEmail("ada"),
    password: "password123",
    interests: ["reading"],
    ...overrides,
  };
  const response = await request(app).post("/api/auth/register").send(body);
  assert.equal(response.status, 201);
  return { response, body, token: response.body.token, user: response.body.user };
}

async function createAdmin() {
  const email = nextEmail("admin");
  const password = "password123";
  const user = await User.create({
    name: "Grace Hopper",
    email,
    password,
    role: "admin",
    interests: ["chess"],
  });
  const response = await request(app).post("/api/auth/login").send({ email, password });
  assert.equal(response.status, 200);
  return { user, token: response.body.token, email, password };
}

function stagesOf(plan, found) {
  const stages = found || [];
  if (!plan || typeof plan !== "object") return stages;
  if (typeof plan.stage === "string") stages.push(plan);
  if (plan.inputStage) stagesOf(plan.inputStage, stages);
  if (plan.thenStage) stagesOf(plan.thenStage, stages);
  if (Array.isArray(plan.inputStages)) {
    for (const stage of plan.inputStages) stagesOf(stage, stages);
  }
  return stages;
}

function assertIndexUsed(explained, indexName) {
  const stages = stagesOf(explained.queryPlanner && explained.queryPlanner.winningPlan);
  const match = stages.find((stage) => stage.stage === "IXSCAN" && stage.indexName === indexName);
  assert.ok(
    match,
    `Expected IXSCAN on ${indexName}. Stages: ${stages.map((stage) => stage.stage + ":" + (stage.indexName || "")).join(", ")}`
  );
}

before(async () => {
  mongo = await MongoMemoryServer.create();
  await connectDB(mongo.getUri());
});

after(async () => {
  await mongoose.disconnect();
  if (mongo) await mongo.stop();
});

beforeEach(async () => {
  await Promise.all([User.deleteMany({}), Note.deleteMany({}), Post.deleteMany({})]);
});

describe("auth", () => {
  test("registers a normal user, hashes the password, and returns a token", async () => {
    const { response, body } = await registerUser({ role: "admin", interests: ["Chess", "chess", ""] });
    assert.equal(response.body.user.role, "user");
    assert.deepEqual(response.body.user.interests, ["chess"]);
    assert.equal(JSON.stringify(response.body).includes(body.password), false);
    assert.equal(JSON.stringify(response.body).includes("$2"), false);

    const stored = await User.findOne({ email: body.email }).select("+password");
    assert.notEqual(stored.password, body.password);
    assert.equal(stored.password.startsWith("$2"), true);

    const me = await request(app).get("/api/auth/me").set(auth(response.body.token));
    assert.equal(me.status, 200);
    assert.equal(me.body.user.email, body.email);
  });

  test("rejects duplicate emails and bad login attempts", async () => {
    const { body } = await registerUser();
    const duplicate = await request(app).post("/api/auth/register").send(body);
    assert.equal(duplicate.status, 409);

    const wrong = await request(app).post("/api/auth/login").send({
      email: body.email,
      password: "wrong-password",
    });
    assert.equal(wrong.status, 401);

    const missing = await request(app).get("/api/notes");
    assert.equal(missing.status, 401);

    const invalidJson = await request(app)
      .post("/api/auth/login")
      .set("Content-Type", "application/json")
      .send("{");
    assert.equal(invalidJson.status, 400);
  });

  test("password change invalidates the previous token", async () => {
    const { token } = await registerUser();
    const updated = await request(app)
      .put("/api/users/me")
      .set(auth(token))
      .send({ password: "newpassword1" });
    assert.equal(updated.status, 200);

    const stale = await request(app).get("/api/auth/me").set(auth(token));
    assert.equal(stale.status, 401);

    const fresh = await request(app).post("/api/auth/login").send({
      email: updated.body.data.email,
      password: "newpassword1",
    });
    assert.equal(fresh.status, 200);
    const me = await request(app).get("/api/auth/me").set(auth(fresh.body.token));
    assert.equal(me.status, 200);
  });
});

describe("notes", () => {
  test("a user manages only their own notes and an admin can read all of them", async () => {
    const ada = await registerUser();
    const grace = await createAdmin();

    const created = await request(app)
      .post("/api/notes")
      .set(auth(ada.token))
      .send({ title: "Private", content: "Ada only" });
    assert.equal(created.status, 201);

    const ownList = await request(app).get("/api/notes").set(auth(ada.token));
    assert.equal(ownList.status, 200);
    assert.equal(ownList.body.data.length, 1);

    const hidden = await request(app).get("/api/notes").set(auth(grace.token));
    assert.equal(hidden.body.data.length, 1);

    const other = await registerUser({ name: "Alan" });
    const otherList = await request(app).get("/api/notes").set(auth(other.token));
    assert.equal(otherList.body.data.length, 0);

    const forbiddenRead = await request(app)
      .get("/api/notes/" + created.body.data.id)
      .set(auth(other.token));
    assert.equal(forbiddenRead.status, 403);

    const adminRead = await request(app)
      .get("/api/notes/" + created.body.data.id)
      .set(auth(grace.token));
    assert.equal(adminRead.status, 200);

    const adminEdit = await request(app)
      .put("/api/notes/" + created.body.data.id)
      .set(auth(grace.token))
      .send({ title: "Hijack" });
    assert.equal(adminEdit.status, 403);

    const edited = await request(app)
      .put("/api/notes/" + created.body.data.id)
      .set(auth(ada.token))
      .send({ title: "Revised" });
    assert.equal(edited.status, 200);
    assert.equal(edited.body.data.title, "Revised");

    const removed = await request(app)
      .delete("/api/notes/" + created.body.data.id)
      .set(auth(ada.token));
    assert.equal(removed.status, 200);
  });

  test("note lists are paginated", async () => {
    const ada = await registerUser();
    for (let index = 0; index < 12; index += 1) {
      const response = await request(app)
        .post("/api/notes")
        .set(auth(ada.token))
        .send({ title: "Note " + index, content: "Body " + index });
      assert.equal(response.status, 201);
      await new Promise((resolve) => setTimeout(resolve, 20));
    }

    const page = await request(app).get("/api/notes?page=1&limit=5").set(auth(ada.token));
    assert.equal(page.status, 200);
    assert.equal(page.body.data.length, 5);
    assert.equal(page.body.pagination.total, 12);
    assert.equal(page.body.pagination.totalPages, 3);
    assert.equal(page.body.data[0].title, "Note 11");

    const last = await request(app).get("/api/notes?page=3&limit=5").set(auth(ada.token));
    assert.equal(last.body.data.length, 2);
    assert.equal(last.body.data[1].title, "Note 0");

    const capped = await request(app).get("/api/notes?page=-4&limit=999").set(auth(ada.token));
    assert.equal(capped.body.pagination.page, 1);
    assert.equal(capped.body.pagination.limit, 50);
  });
});

describe("posts and users", () => {
  test("posts are visible to every signed-in user and only the author can change them", async () => {
    const ada = await registerUser();
    const alan = await registerUser({ name: "Alan" });
    const created = await request(app)
      .post("/api/posts")
      .set(auth(ada.token))
      .send({ title: "Hello", content: "World" });
    assert.equal(created.status, 201);

    const list = await request(app).get("/api/posts").set(auth(alan.token));
    assert.equal(list.status, 200);
    assert.equal(list.body.data.length, 1);
    assert.equal(list.body.data[0].author.name, "Ada Lovelace");

    const blocked = await request(app)
      .put("/api/posts/" + created.body.data.id)
      .set(auth(alan.token))
      .send({ title: "Nope" });
    assert.equal(blocked.status, 403);
  });

  test("admins manage users and a demotion applies before the token expires", async () => {
    const admin = await createAdmin();
    const created = await request(app).post("/api/users").set(auth(admin.token)).send({
      name: "Second Admin",
      email: nextEmail("second"),
      password: "password123",
      role: "admin",
      interests: ["music", "Music"],
    });
    assert.equal(created.status, 201);
    assert.deepEqual(created.body.data.interests, ["music"]);

    const login = await request(app).post("/api/auth/login").send({
      email: created.body.data.email,
      password: "password123",
    });
    assert.equal(login.status, 200);

    const regular = await registerUser();
    const denied = await request(app).get("/api/users").set(auth(regular.token));
    assert.equal(denied.status, 403);

    const demoted = await request(app)
      .put("/api/users/" + created.body.data.id)
      .set(auth(admin.token))
      .send({ role: "user" });
    assert.equal(demoted.status, 200);

    const after = await request(app).get("/api/users").set(auth(login.body.token));
    assert.equal(after.status, 403);

    const selfDelete = await request(app)
      .delete("/api/users/" + admin.user.id)
      .set(auth(admin.token));
    assert.equal(selfDelete.status, 400);

    const note = await request(app)
      .post("/api/notes")
      .set(auth(regular.token))
      .send({ title: "Gone", content: "Soon" });
    const post = await request(app)
      .post("/api/posts")
      .set(auth(regular.token))
      .send({ title: "Gone post", content: "Soon" });

    const removed = await request(app)
      .delete("/api/users/" + regular.user.id)
      .set(auth(admin.token));
    assert.equal(removed.status, 200);
    assert.equal(await Note.countDocuments({ _id: note.body.data.id }), 0);
    assert.equal(await Post.countDocuments({ _id: post.body.data.id }), 0);

    const list = await request(app).get("/api/users?page=1&limit=1").set(auth(admin.token));
    assert.equal(list.status, 200);
    assert.equal(list.body.pagination.limit, 1);
    assert.equal(JSON.stringify(list.body).includes("$2"), false);
  });

  test("keeps the same password when an admin updates a name", async () => {
    const admin = await createAdmin();
    const ada = await registerUser();
    const updated = await request(app)
      .put("/api/users/" + ada.user.id)
      .set(auth(admin.token))
      .send({ name: "Ada Renamed" });
    assert.equal(updated.status, 200);

    const login = await request(app).post("/api/auth/login").send({
      email: ada.user.email,
      password: "password123",
    });
    assert.equal(login.status, 200);
    assert.equal(login.body.user.name, "Ada Renamed");
  });
});

describe("aggregations", () => {
  test("groups users by interest with one aggregate call", async () => {
    const source = fs.readFileSync(
      path.join(__dirname, "../src/controllers/userController.js"),
      "utf8"
    );
    const code = source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
    assert.equal((code.match(/\.aggregate\(/g) || []).length, 2);
    assert.equal((code.match(/\$lookup/g) || []).length, 1);
    assert.equal((code.match(/\$group/g) || []).length, 1);

    const admin = await createAdmin();
    await registerUser({ name: "Ada", interests: ["Chess", "reading"] });
    await registerUser({ name: "Alan", interests: ["chess", "music"] });
    await registerUser({ name: "Empty", interests: [] });

    const outsider = await registerUser({ name: "Outsider", interests: [] });
    const denied = await request(app).get("/api/users/interests").set(auth(outsider.token));
    assert.equal(denied.status, 403);

    const grouped = await request(app)
      .get("/api/users/interests?page=1&limit=10")
      .set(auth(admin.token));
    assert.equal(grouped.status, 200);

    const byName = Object.fromEntries(grouped.body.data.map((group) => [group.interest, group]));
    assert.equal(byName.chess.count, 3);
    assert.equal(byName.reading.count, 1);
    assert.equal(byName.music.count, 1);
    assert.equal(byName.chess.users.some((user) => user.name === "Empty"), false);
    assert.deepEqual(
      grouped.body.data.map((group) => group.interest),
      ["chess", "music", "reading"]
    );
    assert.equal(JSON.stringify(grouped.body).includes("$2"), false);
  });

  test("returns one user's posts through a lookup pipeline", async () => {
    const ada = await registerUser({ name: "Ada" });
    const alan = await registerUser({ name: "Alan" });
    await request(app).post("/api/posts").set(auth(ada.token)).send({ title: "One", content: "A" });
    await new Promise((resolve) => setTimeout(resolve, 20));
    await request(app).post("/api/posts").set(auth(ada.token)).send({ title: "Two", content: "B" });
    await request(app).post("/api/posts").set(auth(alan.token)).send({ title: "Other", content: "C" });

    const posts = await request(app)
      .get("/api/users/" + ada.user.id + "/posts?page=1&limit=10")
      .set(auth(alan.token));
    assert.equal(posts.status, 200);
    assert.equal(posts.body.user.name, "Ada");
    assert.deepEqual(
      posts.body.data.map((post) => post.title),
      ["Two", "One"]
    );
    assert.equal(posts.body.pagination.total, 2);

    const missing = await request(app)
      .get("/api/users/" + new mongoose.Types.ObjectId().toString() + "/posts")
      .set(auth(alan.token));
    assert.equal(missing.status, 404);

    const badId = await request(app).get("/api/users/not-an-id/posts").set(auth(alan.token));
    assert.equal(badId.status, 400);
  });
});

describe("indexes", () => {
  test("declares only the indexes the queries use", async () => {
    assert.equal(User.schema.indexes().length, 3);
    assert.equal(Note.schema.indexes().length, 2);
    assert.equal(Post.schema.indexes().length, 2);

    const names = async (model) => (await model.collection.indexes()).map((index) => index.name).sort();
    assert.deepEqual(await names(User), ["_id_", "createdAt_-1", "email_1", "interests_1"]);
    assert.deepEqual(await names(Note), ["_id_", "createdAt_-1", "owner_1_createdAt_-1"]);
    assert.deepEqual(await names(Post), ["_id_", "author_1", "createdAt_-1"]);

    const emailIndex = (await User.collection.indexes()).find((index) => index.name === "email_1");
    assert.equal(emailIndex.unique, true);
  });

  test("list and lookup queries are index scans", async () => {
    const ada = await registerUser({ interests: ["chess"] });
    await Note.create({ title: "T", content: "C", owner: ada.user.id });
    await Post.create({ title: "P", content: "C", author: ada.user.id });

    assertIndexUsed(
      await User.findOne({ email: ada.user.email }).explain(),
      "email_1"
    );
    assertIndexUsed(
      await User.find().sort({ createdAt: -1 }).skip(0).limit(10).explain(),
      "createdAt_-1"
    );
    assertIndexUsed(
      await Note.find({ owner: ada.user.id }).sort({ createdAt: -1 }).skip(0).limit(10).explain(),
      "owner_1_createdAt_-1"
    );
    assertIndexUsed(
      await Note.find().sort({ createdAt: -1 }).skip(0).limit(10).explain(),
      "createdAt_-1"
    );
    assertIndexUsed(
      await Post.find().sort({ createdAt: -1 }).skip(0).limit(10).explain(),
      "createdAt_-1"
    );
    assertIndexUsed(
      await Post.find({ author: ada.user.id }).explain(),
      "author_1"
    );

    const grouped = await User.collection.aggregate([
      { $match: { interests: { $gte: "" } } },
      { $project: { name: 1, email: 1, interests: 1 } },
      { $unwind: "$interests" },
      { $group: { _id: "$interests", count: { $sum: 1 } } },
    ]).explain("queryPlanner");
    const groupedText = JSON.stringify(grouped);
    assert.ok(groupedText.includes("interests_1"), groupedText.slice(0, 2000));
    assert.ok(groupedText.includes("IXSCAN"), groupedText.slice(0, 2000));

    const lookup = await User.collection.aggregate([
      { $match: { _id: new mongoose.Types.ObjectId(ada.user.id) } },
      { $lookup: { from: "posts", localField: "_id", foreignField: "author", as: "posts" } },
    ]).explain("executionStats");
    const lookupText = JSON.stringify(lookup);
    assert.ok(lookupText.includes("author_1"), lookupText.slice(0, 2000));
  });
});

describe("frontend shell", () => {
  test("serves the app and a health check", async () => {
    const health = await request(app).get("/api/health");
    assert.equal(health.status, 200);
    assert.equal(health.body.ok, true);

    const page = await request(app).get("/");
    assert.equal(page.status, 200);
    assert.equal(page.text.includes("Secure Notes"), true);
    assert.equal(page.text.includes("/app.js"), true);

    const missing = await request(app).get("/api/does-not-exist");
    assert.equal(missing.status, 404);
  });
});
