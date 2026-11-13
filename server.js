require("dotenv").config();

const mongoose = require("mongoose");
const app = require("./src/app");
const connectDB = require("./src/config/db");
const seedAdmin = require("./src/seed");

const port = Number(process.env.PORT) || 3000;
const host = "0.0.0.0";

function listen() {
  return new Promise((resolve, reject) => {
    const server = app.listen(port, host, () => {
      console.log(`Server listening on ${host}:${port}`);
      resolve(server);
    });
    server.once("error", reject);
  });
}

async function connectForever() {
  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 16) {
    app.locals.startupError = "Set JWT_SECRET in Coolify to at least 16 characters, then redeploy.";
    console.error(app.locals.startupError);
    return;
  }

  for (;;) {
    try {
      await connectDB(process.env.MONGODB_URI);
      await seedAdmin();
      app.locals.startupError = null;
      console.log("Database ready");
      return;
    } catch (err) {
      app.locals.startupError = err.message || "Database connection failed";
      console.error(app.locals.startupError);
      await new Promise((resolve) => setTimeout(resolve, 5000));
    }
  }
}

async function start() {
  app.locals.startupError = "Connecting to the database";
  const server = await listen();
  connectForever();

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
