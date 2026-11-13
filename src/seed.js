const User = require("./models/User");

async function seedAdmin() {
  const email = (process.env.ADMIN_EMAIL || "").trim().toLowerCase();
  const password = process.env.ADMIN_PASSWORD || "";
  const name = (process.env.ADMIN_NAME || "Admin").trim() || "Admin";

  if (!email || !password) {
    console.log("Admin seed skipped. Set ADMIN_EMAIL and ADMIN_PASSWORD to create one.");
    return;
  }
  if (password.length < 8) {
    throw new Error("ADMIN_PASSWORD must be at least 8 characters");
  }

  const existing = await User.findOne({ email });
  if (existing) return;

  await User.create({
    name,
    email,
    password,
    role: "admin",
    interests: [],
  });
  console.log(`Seeded admin user ${email}`);
}

module.exports = seedAdmin;
