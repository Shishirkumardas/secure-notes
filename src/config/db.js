const mongoose = require("mongoose");
const User = require("../models/User");
const Note = require("../models/Note");
const Post = require("../models/Post");

async function connectDB(uri) {
  if (!uri) {
    throw new Error("MONGODB_URI is required");
  }

  mongoose.set("strictQuery", true);

  const attempts = 5;
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      await mongoose.connect(uri, { serverSelectionTimeoutMS: 10000 });
      await User.syncIndexes();
      await Note.syncIndexes();
      await Post.syncIndexes();
      return;
    } catch (err) {
      lastError = err;
      await mongoose.disconnect().catch(() => {});
      if (attempt === attempts) break;
      await new Promise((resolve) => setTimeout(resolve, attempt * 2000));
    }
  }

  throw lastError;
}

module.exports = connectDB;
