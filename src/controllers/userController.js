const mongoose = require("mongoose");
const User = require("../models/User");
const Note = require("../models/Note");
const Post = require("../models/Post");
const ApiError = require("../utils/apiError");
const { parsePagination, paginationMeta } = require("../utils/pagination");
const { serializeUser, serializePost } = require("../utils/serialize");
const {
  requireBody,
  requireName,
  normalizeEmail,
  requirePassword,
  optionalPassword,
  normalizeInterests,
  requireRole,
  requireObjectId,
} = require("../utils/validators");

async function listUsers(req, res) {
  const { page, limit, skip } = parsePagination(req.query);
  const [users, total] = await Promise.all([
    User.find().sort({ createdAt: -1 }).skip(skip).limit(limit).lean(),
    User.countDocuments(),
  ]);

  res.json({
    data: users.map(serializeUser),
    pagination: paginationMeta(page, limit, total),
  });
}

async function createUser(req, res) {
  const body = requireBody(req);
  const user = await User.create({
    name: requireName(body.name),
    email: normalizeEmail(body.email),
    password: requirePassword(body.password),
    role: body.role === undefined ? "user" : requireRole(body.role),
    interests: normalizeInterests(body.interests === undefined ? [] : body.interests),
  });
  res.status(201).json({ data: serializeUser(user) });
}

async function getUser(req, res) {
  requireObjectId(req.params.id, "User id");
  const user = await User.findById(req.params.id);
  if (!user) throw new ApiError(404, "User not found");
  res.json({ data: serializeUser(user) });
}

async function updateSelf(req, res) {
  const body = requireBody(req);
  if (body.role !== undefined) {
    throw new ApiError(403, "You cannot change your role");
  }

  const user = await User.findById(req.user._id).select("+password");
  if (!user) throw new ApiError(401, "Authentication required");

  let changed = false;
  if (body.name !== undefined) {
    user.name = requireName(body.name);
    changed = true;
  }
  if (body.email !== undefined) {
    user.email = normalizeEmail(body.email);
    changed = true;
  }
  if (body.interests !== undefined) {
    user.interests = normalizeInterests(body.interests);
    changed = true;
  }
  const password = optionalPassword(body.password);
  if (password) {
    user.password = password;
    user.tokenVersion = (user.tokenVersion || 0) + 1;
    changed = true;
  }
  if (!changed) throw new ApiError(400, "Nothing to update");

  await user.save();
  res.json({ data: serializeUser(user) });
}

async function updateUser(req, res) {
  const body = requireBody(req);
  const id = requireObjectId(req.params.id, "User id");
  const user = await User.findById(id).select("+password");
  if (!user) throw new ApiError(404, "User not found");

  let changed = false;
  if (body.name !== undefined) {
    user.name = requireName(body.name);
    changed = true;
  }
  if (body.email !== undefined) {
    user.email = normalizeEmail(body.email);
    changed = true;
  }
  if (body.interests !== undefined) {
    user.interests = normalizeInterests(body.interests);
    changed = true;
  }
  const password = optionalPassword(body.password);
  if (password) {
    user.password = password;
    user.tokenVersion = (user.tokenVersion || 0) + 1;
    changed = true;
  }
  if (body.role !== undefined) {
    if (String(user._id) === String(req.user._id)) {
      throw new ApiError(400, "You cannot change your own role");
    }
    user.role = requireRole(body.role);
    changed = true;
  }
  if (!changed) throw new ApiError(400, "Nothing to update");

  await user.save();
  res.json({ data: serializeUser(user) });
}

async function deleteUser(req, res) {
  const id = requireObjectId(req.params.id, "User id");
  if (String(id) === String(req.user._id)) {
    throw new ApiError(400, "You cannot delete your own account");
  }
  const user = await User.findById(id);
  if (!user) throw new ApiError(404, "User not found");

  await Note.deleteMany({ owner: user._id });
  await Post.deleteMany({ author: user._id });
  await user.deleteOne();
  res.json({ ok: true });
}

// Scenario 1. Exactly one collection.aggregate() call groups users by interest.
async function groupByInterests(req, res) {
  const { page, limit, skip } = parsePagination(req.query);

  const pipeline = [
    // Range over every string interest so the multikey index can support the pipeline.
    { $match: { interests: { $gte: "" } } },
    { $project: { name: 1, email: 1, interests: 1 } },
    { $unwind: "$interests" },
    {
      $group: {
        _id: "$interests",
        count: { $sum: 1 },
        users: {
          $push: {
            _id: "$_id",
            name: "$name",
            email: "$email",
          },
        },
      },
    },
    { $sort: { _id: 1 } },
    {
      $facet: {
        metadata: [{ $count: "total" }],
        data: [
          { $skip: skip },
          { $limit: limit },
          {
            $project: {
              _id: 0,
              interest: "$_id",
              count: 1,
              users: 1,
            },
          },
        ],
      },
    },
  ];

  const rows = await User.collection.aggregate(pipeline).toArray();
  const result = rows[0] || { metadata: [], data: [] };
  const total = result.metadata && result.metadata[0] ? result.metadata[0].total : 0;
  const data = (result.data || []).map((group) => ({
    interest: group.interest,
    count: group.count,
    users: (group.users || []).map((user) => ({
      id: String(user._id),
      name: user.name,
      email: user.email,
    })),
  }));

  res.json({
    data,
    pagination: paginationMeta(page, limit, total),
  });
}

// Scenario 2. One aggregation pipeline, with a single $lookup, returns one user's posts.
async function getUserPosts(req, res) {
  const id = requireObjectId(req.params.id, "User id");
  const { page, limit, skip } = parsePagination(req.query);

  const pipeline = [
    { $match: { _id: new mongoose.Types.ObjectId(id) } },
    { $project: { name: 1, email: 1 } },
    {
      $lookup: {
        from: "posts",
        localField: "_id",
        foreignField: "author",
        as: "posts",
      },
    },
    {
      $project: {
        name: 1,
        email: 1,
        total: { $size: "$posts" },
        posts: {
          $slice: [
            { $sortArray: { input: "$posts", sortBy: { createdAt: -1 } } },
            skip,
            limit,
          ],
        },
      },
    },
  ];

  const rows = await User.collection.aggregate(pipeline).toArray();
  if (!rows.length) throw new ApiError(404, "User not found");

  const row = rows[0];
  res.json({
    user: {
      id: String(row._id),
      name: row.name,
      email: row.email,
    },
    data: (row.posts || []).map(serializePost),
    pagination: paginationMeta(page, limit, row.total || 0),
  });
}

module.exports = {
  listUsers,
  createUser,
  getUser,
  updateSelf,
  updateUser,
  deleteUser,
  groupByInterests,
  getUserPosts,
};
