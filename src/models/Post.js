const mongoose = require("mongoose");

const postSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 120 },
    content: { type: String, required: true, maxlength: 20000 },
    author: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
  },
  { timestamps: true, collection: "posts" }
);

// $lookup matches posts on author. Deleting a user also deletes by this field.
postSchema.index({ author: 1 });

// The public post list is sorted by createdAt descending and paginated.
// author must not be the prefix here, or this global sort cannot use the index.
postSchema.index({ createdAt: -1 });

module.exports = mongoose.model("Post", postSchema);
