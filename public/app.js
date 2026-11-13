const state = {
  user: null,
  view: "notes",
  error: "",
  message: "",
  authMode: "login",
  loginDraft: { email: "", password: "" },
  registerDraft: { name: "", email: "", password: "", interests: "" },
  notes: [],
  notesPage: emptyPage(),
  noteDraft: { title: "", content: "" },
  editingNoteId: null,
  posts: [],
  postsPage: emptyPage(),
  postDraft: { title: "", content: "" },
  editingPostId: null,
  users: [],
  usersPage: emptyPage(),
  userDraft: emptyUserDraft(),
  editingUserId: null,
  groups: [],
  groupsPage: emptyPage(),
  profileDraft: { name: "", interests: "", password: "" },
  userPosts: null,
  userPostsPage: emptyPage(),
  returnView: "posts",
};

let busy = false;

function emptyPage() {
  return { page: 1, limit: 10, total: 0, totalPages: 0 };
}

function emptyUserDraft() {
  return { name: "", email: "", password: "", role: "user", interests: "" };
}

function el(tag, props, children) {
  const node = document.createElement(tag);
  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value == null || value === false) continue;
      if (key === "class") node.className = value;
      else if (key === "text") node.textContent = value;
      else if (key.startsWith("on") && typeof value === "function") {
        node.addEventListener(key.slice(2).toLowerCase(), value);
      } else if (key === "disabled") node.disabled = Boolean(value);
      else node.setAttribute(key, value);
    }
  }
  for (const child of children || []) {
    if (child == null || child === false) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

function textInput(props) {
  const node = el("input", props);
  if (props.value != null) node.value = props.value;
  return node;
}

function textArea(name, value, required) {
  const node = el("textarea", { name, required: required ? "true" : null });
  node.value = value || "";
  return node;
}

function field(label, control) {
  return el("label", {}, [el("span", { text: label }), control]);
}

function roleSelect(role, disabled) {
  const select = el("select", { name: "role", disabled: disabled }, [
    el("option", { value: "user" }, ["user"]),
    el("option", { value: "admin" }, ["admin"]),
  ]);
  select.value = role === "admin" ? "admin" : "user";
  return select;
}

function formatWhen(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString();
}

async function api(path, options) {
  const settings = options || {};
  const headers = { ...(settings.headers || {}) };
  if (settings.body) headers["Content-Type"] = "application/json";
  const token = localStorage.getItem("token");
  if (token) headers.Authorization = "Bearer " + token;

  const response = await fetch(path, { ...settings, headers });
  const text = await response.text();
  let payload = {};
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch (_err) {
      payload = { error: "Unexpected response from the server" };
    }
  }

  if (response.status === 401 && state.user && !path.startsWith("/api/auth/")) {
    localStorage.removeItem("token");
    state.user = null;
    state.authMode = "login";
    payload.error = "Your session has ended. Sign in again.";
  }

  if (!response.ok) {
    const error = new Error(payload.error || "Request failed");
    error.status = response.status;
    throw error;
  }
  return payload;
}

async function withBusy(fn) {
  if (busy) return;
  busy = true;
  try {
    await fn();
  } finally {
    busy = false;
  }
}

function render() {
  const session = document.getElementById("session");
  const app = document.getElementById("app");
  session.replaceChildren();
  app.replaceChildren();

  if (!state.user) {
    session.append(el("span", { class: "meta", text: "Not signed in" }));
    app.append(authView());
    return;
  }

  session.append(
    el("div", {}, [
      el("strong", { text: state.user.name }),
      el("div", { class: "meta", text: state.user.email + " · " + state.user.role }),
      el("button", { type: "button", onclick: logout }, ["Sign out"]),
    ])
  );

  app.append(nav(), banner(), viewFor(state.view));
}

function banner() {
  if (!state.error && !state.message) return el("div");
  return el("div", {}, [
    state.error ? el("div", { class: "banner error", text: state.error }) : null,
    state.message ? el("div", { class: "banner message", text: state.message }) : null,
  ]);
}

function nav() {
  const buttons = [
    ["notes", "Notes"],
    ["posts", "Posts"],
    ["profile", "Profile"],
  ];
  if (state.user.role === "admin") {
    buttons.push(["users", "Users"], ["interests", "Interests"]);
  }
  return el(
    "nav",
    {},
    buttons.map(([view, label]) =>
      el("button", {
        type: "button",
        class: state.view === view ? "active" : "",
        onclick: () => switchView(view),
        text: label,
      })
    )
  );
}

function viewFor(view) {
  if (view === "posts") return postsView();
  if (view === "profile") return profileView();
  if (view === "users") return usersView();
  if (view === "interests") return interestsView();
  if (view === "userPosts") return userPostsView();
  return notesView();
}

function pager(meta, onPage) {
  return el("div", { class: "pager" }, [
    el("button", { type: "button", disabled: meta.page <= 1, onclick: () => onPage(meta.page - 1) }, ["Previous"]),
    el("span", { text: "Page " + meta.page + " of " + Math.max(meta.totalPages, 1) + " (" + meta.total + ")" }),
    el("button", {
      type: "button",
      disabled: meta.totalPages === 0 || meta.page >= meta.totalPages,
      onclick: () => onPage(meta.page + 1),
    }, ["Next"]),
  ]);
}

function authView() {
  const login = state.authMode !== "register";
  return el("section", { class: "panel" }, [
    el("div", { class: "actions" }, [
      el("button", { type: "button", class: login ? "active" : "", onclick: () => setAuthMode("login") }, ["Sign in"]),
      el("button", { type: "button", class: login ? "" : "active", onclick: () => setAuthMode("register") }, ["Register"]),
    ]),
    banner(),
    login ? loginForm() : registerForm(),
  ]);
}

function setAuthMode(mode) {
  state.authMode = mode;
  state.error = "";
  state.message = "";
  render();
}

function loginForm() {
  return el("form", { onsubmit: submitLogin }, [
    field("Email", textInput({ name: "email", type: "email", required: "true", autocomplete: "username", value: state.loginDraft.email })),
    field("Password", textInput({ name: "password", type: "password", required: "true", autocomplete: "current-password", value: state.loginDraft.password })),
    el("button", { class: "primary", type: "submit" }, ["Sign in"]),
  ]);
}

function registerForm() {
  const draft = state.registerDraft;
  return el("form", { onsubmit: submitRegister }, [
    field("Name", textInput({ name: "name", required: "true", value: draft.name })),
    field("Email", textInput({ name: "email", type: "email", required: "true", autocomplete: "username", value: draft.email })),
    field("Password", textInput({ name: "password", type: "password", required: "true", autocomplete: "new-password", value: draft.password })),
    field("Interests, separated by commas", textInput({ name: "interests", value: draft.interests })),
    el("button", { class: "primary", type: "submit" }, ["Create account"]),
  ]);
}

function notesView() {
  const draft = state.noteDraft;
  return el("section", {}, [
    el("form", { class: "panel", onsubmit: submitNote }, [
      el("h2", { text: state.editingNoteId ? "Edit note" : "New note" }),
      field("Title", textInput({ name: "title", required: "true", value: draft.title })),
      field("Content", textArea("content", draft.content, true)),
      el("div", { class: "actions" }, [
        el("button", { class: "primary", type: "submit" }, [state.editingNoteId ? "Save note" : "Create note"]),
        state.editingNoteId
          ? el("button", { type: "button", onclick: cancelNoteEdit }, ["Cancel"])
          : null,
      ]),
    ]),
    pager(state.notesPage, (page) => loadNotes(page).catch(showError)),
    state.notes.length
      ? el("div", {}, state.notes.map(noteCard))
      : el("p", { class: "meta", text: "No notes on this page." }),
  ]);
}

function noteCard(note) {
  const mine = note.owner && note.owner.id === state.user.id;
  return el("article", { class: "card" }, [
    el("div", { class: "row" }, [
      el("h2", { text: note.title }),
      el("span", { class: "meta", text: formatWhen(note.createdAt) }),
    ]),
    state.user.role === "admin"
      ? el("p", { class: "meta", text: "Owner: " + (note.owner.name || note.owner.email || note.owner.id) })
      : null,
    el("p", { class: "content", text: note.content }),
    mine
      ? el("div", { class: "actions" }, [
          el("button", { type: "button", onclick: () => editNote(note) }, ["Edit"]),
          el("button", { type: "button", onclick: () => removeNote(note) }, ["Delete"]),
        ])
      : null,
  ]);
}

function postsView() {
  const draft = state.postDraft;
  return el("section", {}, [
    el("form", { class: "panel", onsubmit: submitPost }, [
      el("h2", { text: state.editingPostId ? "Edit post" : "New post" }),
      field("Title", textInput({ name: "title", required: "true", value: draft.title })),
      field("Content", textArea("content", draft.content, true)),
      el("div", { class: "actions" }, [
        el("button", { class: "primary", type: "submit" }, [state.editingPostId ? "Save post" : "Create post"]),
        state.editingPostId
          ? el("button", { type: "button", onclick: cancelPostEdit }, ["Cancel"])
          : null,
      ]),
    ]),
    pager(state.postsPage, (page) => loadPosts(page).catch(showError)),
    state.posts.length
      ? el("div", {}, state.posts.map(postCard))
      : el("p", { class: "meta", text: "No posts on this page." }),
  ]);
}

function postCard(post) {
  const mine = post.author && post.author.id === state.user.id;
  const authorName = post.author.name || post.author.email || post.author.id;
  return el("article", { class: "card" }, [
    el("div", { class: "row" }, [
      el("h2", { text: post.title }),
      el("span", { class: "meta", text: formatWhen(post.createdAt) }),
    ]),
    el("p", { class: "meta", text: "Author: " + authorName }),
    el("p", { class: "content", text: post.content }),
    el("div", { class: "actions" }, [
      el("button", { type: "button", onclick: () => openUserPosts(post.author.id, "posts").catch(showError) }, ["This author's posts"]),
      mine ? el("button", { type: "button", onclick: () => editPost(post) }, ["Edit"]) : null,
      mine ? el("button", { type: "button", onclick: () => removePost(post) }, ["Delete"]) : null,
    ]),
  ]);
}

function profileView() {
  const draft = state.profileDraft;
  return el("form", { class: "panel", onsubmit: submitProfile }, [
    el("h2", { text: "Your profile" }),
    field("Name", textInput({ name: "name", required: "true", value: draft.name })),
    field("Interests, separated by commas", textInput({ name: "interests", value: draft.interests })),
    field("New password (optional)", textInput({ name: "password", type: "password", autocomplete: "new-password", value: draft.password })),
    el("button", { class: "primary", type: "submit" }, ["Save profile"]),
  ]);
}

function usersView() {
  if (state.user.role !== "admin") return el("p", { text: "Admin access required." });
  const draft = state.userDraft;
  const editingSelf = state.editingUserId === state.user.id;
  return el("section", {}, [
    el("form", { class: "panel", onsubmit: submitUser }, [
      el("h2", { text: state.editingUserId ? "Edit user" : "New user" }),
      field("Name", textInput({ name: "name", required: "true", value: draft.name })),
      field("Email", textInput({ name: "email", type: "email", required: "true", value: draft.email })),
      field(
        state.editingUserId ? "Password (leave blank to keep the current one)" : "Password",
        textInput({
          name: "password",
          type: "password",
          required: state.editingUserId ? null : "true",
          value: draft.password,
        })
      ),
      field("Role", roleSelect(draft.role, editingSelf)),
      field("Interests, separated by commas", textInput({ name: "interests", value: draft.interests })),
      el("div", { class: "actions" }, [
        el("button", { class: "primary", type: "submit" }, [state.editingUserId ? "Save user" : "Create user"]),
        state.editingUserId
          ? el("button", { type: "button", onclick: cancelUserEdit }, ["Cancel"])
          : null,
      ]),
    ]),
    pager(state.usersPage, (page) => loadUsers(page).catch(showError)),
    el("table", {}, [
      el("thead", {}, [
        el("tr", {}, ["Name", "Email", "Role", "Interests", ""].map((label) => el("th", { text: label }))),
      ]),
      el("tbody", {}, state.users.map(userRow)),
    ]),
  ]);
}

function userRow(user) {
  return el("tr", {}, [
    el("td", { text: user.name }),
    el("td", { text: user.email }),
    el("td", { text: user.role }),
    el("td", { text: (user.interests || []).join(", ") }),
    el("td", {}, [
      el("div", { class: "actions" }, [
        el("button", { type: "button", onclick: () => editUser(user) }, ["Edit"]),
        el("button", { type: "button", onclick: () => openUserPosts(user.id, "users").catch(showError) }, ["Posts"]),
        user.id === state.user.id
          ? null
          : el("button", { type: "button", onclick: () => removeUser(user) }, ["Delete"]),
      ]),
    ]),
  ]);
}

function interestsView() {
  if (state.user.role !== "admin") return el("p", { text: "Admin access required." });
  return el("section", {}, [
    el("h2", { text: "Users grouped by interest" }),
    pager(state.groupsPage, (page) => loadInterests(page).catch(showError)),
    state.groups.length
      ? el("div", {}, state.groups.map(groupCard))
      : el("p", { class: "meta", text: "No interests yet. Add interests on a user profile." }),
  ]);
}

function groupCard(group) {
  return el("article", { class: "card" }, [
    el("h2", { text: group.interest + " (" + group.count + ")" }),
    el("ul", {}, (group.users || []).map((user) =>
      el("li", {}, [
        el("span", { text: user.name + " · " + user.email + " " }),
        el("button", { type: "button", onclick: () => openUserPosts(user.id, "interests").catch(showError) }, ["Posts"]),
      ])
    )),
  ]);
}

function userPostsView() {
  const info = state.userPosts;
  if (!info) return el("p", { text: "No user selected." });
  return el("section", {}, [
    el("div", { class: "actions" }, [
      el("button", { type: "button", onclick: () => switchView(state.returnView) }, ["Back"]),
    ]),
    el("h2", { text: "Posts by " + info.user.name }),
    el("p", { class: "meta", text: info.user.email }),
    pager(state.userPostsPage, (page) => openUserPosts(info.user.id, state.returnView, page).catch(showError)),
    info.data.length
      ? el("div", {}, info.data.map((post) =>
          el("article", { class: "card" }, [
            el("h2", { text: post.title }),
            el("p", { class: "meta", text: formatWhen(post.createdAt) }),
            el("p", { class: "content", text: post.content }),
          ])
        ))
      : el("p", { class: "meta", text: "This user has no posts on this page." }),
  ]);
}

function showError(err) {
  state.error = err.message;
  render();
}

function splitInterests(value) {
  return String(value || "")
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
}

async function submitLogin(event) {
  event.preventDefault();
  const data = new FormData(event.target);
  state.loginDraft = {
    email: String(data.get("email") || ""),
    password: String(data.get("password") || ""),
  };
  await withBusy(async () => {
    try {
      const result = await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(state.loginDraft),
      });
      localStorage.setItem("token", result.token);
      state.user = result.user;
      state.error = "";
      state.message = "";
      state.loginDraft.password = "";
      prepareProfileDraft();
      await loadNotes(1);
    } catch (err) {
      state.error = err.message;
      render();
    }
  });
}

async function submitRegister(event) {
  event.preventDefault();
  const data = new FormData(event.target);
  state.registerDraft = {
    name: String(data.get("name") || ""),
    email: String(data.get("email") || ""),
    password: String(data.get("password") || ""),
    interests: String(data.get("interests") || ""),
  };
  await withBusy(async () => {
    try {
      const result = await api("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          name: state.registerDraft.name,
          email: state.registerDraft.email,
          password: state.registerDraft.password,
          interests: splitInterests(state.registerDraft.interests),
        }),
      });
      localStorage.setItem("token", result.token);
      state.user = result.user;
      state.error = "";
      state.message = "Account created.";
      state.registerDraft.password = "";
      prepareProfileDraft();
      await loadNotes(1);
    } catch (err) {
      state.error = err.message;
      render();
    }
  });
}

function logout() {
  localStorage.removeItem("token");
  state.user = null;
  state.view = "notes";
  state.authMode = "login";
  state.error = "";
  state.message = "Signed out.";
  render();
}

async function switchView(view) {
  state.error = "";
  state.message = "";
  state.view = view;
  try {
    if (view === "notes") await loadNotes(state.notesPage.page || 1);
    else if (view === "posts") await loadPosts(state.postsPage.page || 1);
    else if (view === "users") await loadUsers(state.usersPage.page || 1);
    else if (view === "interests") await loadInterests(state.groupsPage.page || 1);
    else {
      if (view === "profile") prepareProfileDraft();
      render();
    }
  } catch (err) {
    showError(err);
  }
}

function prepareProfileDraft() {
  state.profileDraft = {
    name: state.user.name,
    interests: (state.user.interests || []).join(", "),
    password: "",
  };
}

async function loadNotes(page) {
  const result = await api("/api/notes?page=" + page + "&limit=10");
  state.notes = result.data;
  state.notesPage = result.pagination;
  state.view = "notes";
  render();
}

async function submitNote(event) {
  event.preventDefault();
  const data = new FormData(event.target);
  state.noteDraft = {
    title: String(data.get("title") || ""),
    content: String(data.get("content") || ""),
  };
  await withBusy(async () => {
    try {
      const payload = JSON.stringify(state.noteDraft);
      const edited = Boolean(state.editingNoteId);
      if (edited) {
        await api("/api/notes/" + state.editingNoteId, { method: "PUT", body: payload });
        state.message = "Note updated.";
      } else {
        await api("/api/notes", { method: "POST", body: payload });
        state.message = "Note created.";
      }
      state.error = "";
      state.editingNoteId = null;
      state.noteDraft = { title: "", content: "" };
      await loadNotes(edited ? state.notesPage.page : 1);
    } catch (err) {
      showError(err);
    }
  });
}

function editNote(note) {
  state.editingNoteId = note.id;
  state.noteDraft = { title: note.title, content: note.content };
  state.error = "";
  state.message = "";
  render();
  window.scrollTo(0, 0);
}

function cancelNoteEdit() {
  state.editingNoteId = null;
  state.noteDraft = { title: "", content: "" };
  render();
}

async function removeNote(note) {
  if (!window.confirm("Delete this note?")) return;
  await withBusy(async () => {
    try {
      await api("/api/notes/" + note.id, { method: "DELETE" });
      state.message = "Note deleted.";
      state.error = "";
      if (state.editingNoteId === note.id) cancelNoteEdit();
      await loadNotes(state.notesPage.page);
    } catch (err) {
      showError(err);
    }
  });
}

async function loadPosts(page) {
  const result = await api("/api/posts?page=" + page + "&limit=10");
  state.posts = result.data;
  state.postsPage = result.pagination;
  state.view = "posts";
  render();
}

async function submitPost(event) {
  event.preventDefault();
  const data = new FormData(event.target);
  state.postDraft = {
    title: String(data.get("title") || ""),
    content: String(data.get("content") || ""),
  };
  await withBusy(async () => {
    try {
      const payload = JSON.stringify(state.postDraft);
      const edited = Boolean(state.editingPostId);
      if (edited) {
        await api("/api/posts/" + state.editingPostId, { method: "PUT", body: payload });
        state.message = "Post updated.";
      } else {
        await api("/api/posts", { method: "POST", body: payload });
        state.message = "Post created.";
      }
      state.error = "";
      state.editingPostId = null;
      state.postDraft = { title: "", content: "" };
      await loadPosts(edited ? state.postsPage.page : 1);
    } catch (err) {
      showError(err);
    }
  });
}

function editPost(post) {
  state.editingPostId = post.id;
  state.postDraft = { title: post.title, content: post.content };
  state.error = "";
  state.message = "";
  render();
  window.scrollTo(0, 0);
}

function cancelPostEdit() {
  state.editingPostId = null;
  state.postDraft = { title: "", content: "" };
  render();
}

async function removePost(post) {
  if (!window.confirm("Delete this post?")) return;
  await withBusy(async () => {
    try {
      await api("/api/posts/" + post.id, { method: "DELETE" });
      state.message = "Post deleted.";
      state.error = "";
      if (state.editingPostId === post.id) {
        state.editingPostId = null;
        state.postDraft = { title: "", content: "" };
      }
      await loadPosts(state.postsPage.page);
    } catch (err) {
      showError(err);
    }
  });
}

async function submitProfile(event) {
  event.preventDefault();
  const data = new FormData(event.target);
  const password = String(data.get("password") || "");
  state.profileDraft = {
    name: String(data.get("name") || ""),
    interests: String(data.get("interests") || ""),
    password,
  };
  await withBusy(async () => {
    try {
      const body = {
        name: state.profileDraft.name,
        interests: splitInterests(state.profileDraft.interests),
      };
      if (password) body.password = password;
      const result = await api("/api/users/me", { method: "PUT", body: JSON.stringify(body) });
      if (password) {
        localStorage.removeItem("token");
        state.user = null;
        state.message = "Password updated. Sign in again.";
        state.error = "";
        render();
        return;
      }
      state.user = result.data;
      state.message = "Profile updated.";
      state.error = "";
      prepareProfileDraft();
      render();
    } catch (err) {
      showError(err);
    }
  });
}

async function loadUsers(page) {
  const result = await api("/api/users?page=" + page + "&limit=10");
  state.users = result.data;
  state.usersPage = result.pagination;
  state.view = "users";
  render();
}

async function submitUser(event) {
  event.preventDefault();
  const data = new FormData(event.target);
  const password = String(data.get("password") || "");
  state.userDraft = {
    name: String(data.get("name") || ""),
    email: String(data.get("email") || ""),
    password,
    role: String(data.get("role") || state.userDraft.role || "user"),
    interests: String(data.get("interests") || ""),
  };
  const editingSelf = state.editingUserId === state.user.id;
  await withBusy(async () => {
    try {
      const body = {
        name: state.userDraft.name,
        email: state.userDraft.email,
        interests: splitInterests(state.userDraft.interests),
      };
      if (!editingSelf) body.role = state.userDraft.role;
      if (password) body.password = password;
      if (state.editingUserId) {
        await api("/api/users/" + state.editingUserId, { method: "PUT", body: JSON.stringify(body) });
        state.message = "User updated.";
      } else {
        body.password = password;
        await api("/api/users", { method: "POST", body: JSON.stringify(body) });
        state.message = "User created.";
      }
      state.error = "";
      const changedOwnPassword = editingSelf && password;
      state.editingUserId = null;
      state.userDraft = emptyUserDraft();
      if (changedOwnPassword) {
        localStorage.removeItem("token");
        state.user = null;
        state.message = "Password updated. Sign in again.";
        render();
        return;
      }
      await loadUsers(1);
    } catch (err) {
      showError(err);
    }
  });
}

function editUser(user) {
  state.editingUserId = user.id;
  state.userDraft = {
    name: user.name,
    email: user.email,
    password: "",
    role: user.role,
    interests: (user.interests || []).join(", "),
  };
  state.error = "";
  state.message = "";
  render();
  window.scrollTo(0, 0);
}

function cancelUserEdit() {
  state.editingUserId = null;
  state.userDraft = emptyUserDraft();
  render();
}

async function removeUser(user) {
  if (!window.confirm("Delete " + user.email + " and their notes and posts?")) return;
  await withBusy(async () => {
    try {
      await api("/api/users/" + user.id, { method: "DELETE" });
      state.message = "User deleted.";
      state.error = "";
      if (state.editingUserId === user.id) {
        state.editingUserId = null;
        state.userDraft = emptyUserDraft();
      }
      await loadUsers(state.usersPage.page);
    } catch (err) {
      showError(err);
    }
  });
}

async function loadInterests(page) {
  const result = await api("/api/users/interests?page=" + page + "&limit=10");
  state.groups = result.data;
  state.groupsPage = result.pagination;
  state.view = "interests";
  render();
}

async function openUserPosts(userId, returnView, page) {
  const nextPage = page || 1;
  state.returnView = returnView || state.view;
  const result = await api("/api/users/" + userId + "/posts?page=" + nextPage + "&limit=10");
  state.userPosts = result;
  state.userPostsPage = result.pagination;
  state.view = "userPosts";
  state.error = "";
  render();
}

async function init() {
  if (!localStorage.getItem("token")) {
    render();
    return;
  }
  try {
    const result = await api("/api/auth/me");
    state.user = result.user;
    prepareProfileDraft();
    await loadNotes(1);
  } catch (_err) {
    localStorage.removeItem("token");
    state.user = null;
    state.error = "";
    render();
  }
}

init();
