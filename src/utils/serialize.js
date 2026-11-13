function serializeUser(user) {
  return {
    id: String(user._id),
    name: user.name,
    email: user.email,
    role: user.role,
    interests: user.interests || [],
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  };
}

function serializeOwner(value) {
  if (value && typeof value === "object" && value._id && (value.name || value.email)) {
    return {
      id: String(value._id),
      name: value.name,
      email: value.email,
    };
  }
  return { id: String(value) };
}

function serializeNote(note) {
  return {
    id: String(note._id),
    title: note.title,
    content: note.content,
    owner: serializeOwner(note.owner),
    createdAt: note.createdAt,
    updatedAt: note.updatedAt,
  };
}

function serializePost(post) {
  return {
    id: String(post._id),
    title: post.title,
    content: post.content,
    author: serializeOwner(post.author),
    createdAt: post.createdAt,
    updatedAt: post.updatedAt,
  };
}

module.exports = { serializeUser, serializeNote, serializePost };
