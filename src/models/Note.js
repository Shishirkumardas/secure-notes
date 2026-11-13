const mongoose = require("mongoose");

const noteSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 120 },
    content: { type: String, required: true, maxlength: 20000 },
    owner: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
  },
  { timestamps: true }
);

// A user listing their own notes, newest first.
noteSchema.index({ owner: 1, createdAt: -1 });

// An admin listing every note, newest first.
// The owner prefix above cannot serve a sort that has no owner filter.
noteSchema.index({ createdAt: -1 });

module.exports = mongoose.model("Note", noteSchema);
