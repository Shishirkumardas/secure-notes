require("dotenv").config();

const mongoose = require("mongoose");
const app = require("./src/app");
const connectDB = require("./src/config/db");
const seedAdmin = require("./src/seed");

const port = Number(process.env.PORT) || 3000;
const host = "0.0.0.0";

async function start() {
  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 16) {
    throw new Error("JWT_SECRET is required and must be at least 16 characters");
  }

  await connectDB(process.env.MONGODB_URI);
  await seedAdmin();

  const server = app.listen(port, host, () => {
    console.log(`Server listening on ${host}:${port}`);
  });

  function shutdown(signal) {
    console.log(`Received ${signal}, shutting down`);
    server.close(async () => {
      await mongoose.disconnect();
      process.exit(0);
    });
    setTimeout(() => process.exit(1), 10000).unref();
  }

  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGTERM", () => shutdown("SIGTERM"));
}

start().catch((err) => {
  console.error("Failed to start:", err.message);
  process.exit(1);
});
