const Post = require("../models/Post");
const ApiError = require("../utils/apiError");
const { parsePagination, paginationMeta } = require("../utils/pagination");
const { serializePost } = require("../utils/serialize");
const { requireBody, requireText, requireObjectId } = require("../utils/validators");

async function listPosts(req, res) {
  const { page, limit, skip } = parsePagination(req.query);
  const [posts, total] = await Promise.all([
    Post.find()
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("author", "name email")
      .lean(),
    Post.countDocuments(),
  ]);

  res.json({
    data: posts.map(serializePost),
    pagination: paginationMeta(page, limit, total),
  });
}

async function createPost(req, res) {
  const body = requireBody(req);
  const post = await Post.create({
    title: requireText(body.title, "Title", 120),
    content: requireText(body.content, "Content", 20000),
    author: req.user._id,
  });
  await post.populate("author", "name email");
  res.status(201).json({ data: serializePost(post) });
}

async function getPost(req, res) {
  requireObjectId(req.params.id, "Post id");
  const post = await Post.findById(req.params.id).populate("author", "name email");
  if (!post) throw new ApiError(404, "Post not found");
  res.json({ data: serializePost(post) });
}

async function updatePost(req, res) {
  const body = requireBody(req);
  requireObjectId(req.params.id, "Post id");
  const post = await Post.findById(req.params.id);
  if (!post) throw new ApiError(404, "Post not found");
  if (String(post.author) !== String(req.user._id)) {
    throw new ApiError(403, "You can only edit your own posts");
  }
  if (body.title === undefined && body.content === undefined) {
    throw new ApiError(400, "Nothing to update");
  }
  if (body.title !== undefined) post.title = requireText(body.title, "Title", 120);
  if (body.content !== undefined) post.content = requireText(body.content, "Content", 20000);
  await post.save();
  await post.populate("author", "name email");
  res.json({ data: serializePost(post) });
}

async function deletePost(req, res) {
  requireObjectId(req.params.id, "Post id");
  const post = await Post.findById(req.params.id);
  if (!post) throw new ApiError(404, "Post not found");
  if (String(post.author) !== String(req.user._id)) {
    throw new ApiError(403, "You can only delete your own posts");
  }
  await post.deleteOne();
  res.json({ ok: true });
}

module.exports = { listPosts, createPost, getPost, updatePost, deletePost };
