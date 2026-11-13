const Note = require("../models/Note");
const ApiError = require("../utils/apiError");
const { parsePagination, paginationMeta } = require("../utils/pagination");
const { serializeNote } = require("../utils/serialize");
const { requireBody, requireText, requireObjectId } = require("../utils/validators");

async function listNotes(req, res) {
  const { page, limit, skip } = parsePagination(req.query);
  const filter = req.user.role === "admin" ? {} : { owner: req.user._id };
  const [notes, total] = await Promise.all([
    Note.find(filter)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("owner", "name email")
      .lean(),
    Note.countDocuments(filter),
  ]);

  res.json({
    data: notes.map(serializeNote),
    pagination: paginationMeta(page, limit, total),
  });
}

async function createNote(req, res) {
  const body = requireBody(req);
  const note = await Note.create({
    title: requireText(body.title, "Title", 120),
    content: requireText(body.content, "Content", 20000),
    owner: req.user._id,
  });
  await note.populate("owner", "name email");
  res.status(201).json({ data: serializeNote(note) });
}

async function getNote(req, res) {
  requireObjectId(req.params.id, "Note id");
  const note = await Note.findById(req.params.id).populate("owner", "name email");
  if (!note || !note.owner) throw new ApiError(404, "Note not found");
  const ownerId = note.owner._id || note.owner;
  const owns = String(ownerId) === String(req.user._id);
  if (!owns && req.user.role !== "admin") {
    throw new ApiError(403, "You cannot view this note");
  }
  res.json({ data: serializeNote(note) });
}

async function updateNote(req, res) {
  const body = requireBody(req);
  requireObjectId(req.params.id, "Note id");
  const note = await Note.findById(req.params.id);
  if (!note) throw new ApiError(404, "Note not found");
  if (String(note.owner) !== String(req.user._id)) {
    throw new ApiError(403, "You can only edit your own notes");
  }
  if (body.title === undefined && body.content === undefined) {
    throw new ApiError(400, "Nothing to update");
  }
  if (body.title !== undefined) note.title = requireText(body.title, "Title", 120);
  if (body.content !== undefined) note.content = requireText(body.content, "Content", 20000);
  await note.save();
  await note.populate("owner", "name email");
  res.json({ data: serializeNote(note) });
}

async function deleteNote(req, res) {
  requireObjectId(req.params.id, "Note id");
  const note = await Note.findById(req.params.id);
  if (!note) throw new ApiError(404, "Note not found");
  if (String(note.owner) !== String(req.user._id)) {
    throw new ApiError(403, "You can only delete your own notes");
  }
  await note.deleteOne();
  res.json({ ok: true });
}

module.exports = { listNotes, createNote, getNote, updateNote, deleteNote };
