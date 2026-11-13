const path = require("path");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const mongoose = require("mongoose");

const authRoutes = require("./routes/auth");
const noteRoutes = require("./routes/notes");
const postRoutes = require("./routes/posts");
const userRoutes = require("./routes/users");
const errorHandler = require("./middleware/errorHandler");

const app = express();

app.set("trust proxy", 1);
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: "100kb" }));

app.get("/api/health", (_req, res) => {
  const connected = mongoose.connection.readyState === 1;
  res.status(connected ? 200 : 503).json({
    ok: connected,
    db: connected ? "connected" : "disconnected",
  });
});

app.use("/api/auth", authRoutes);
app.use("/api/notes", noteRoutes);
app.use("/api/posts", postRoutes);
app.use("/api/users", userRoutes);

app.use("/api", (_req, res) => {
  res.status(404).json({ error: "Not found" });
});

app.use(express.static(path.join(__dirname, "..", "public")));

app.use((req, res, next) => {
  if (req.method !== "GET") {
    next();
    return;
  }
  res.sendFile(path.join(__dirname, "..", "public", "index.html"), (err) => {
    if (err) next(err);
  });
});

app.use(errorHandler);

module.exports = app;
