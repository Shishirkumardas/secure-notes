const bcrypt = require("bcryptjs");
const User = require("../models/User");
const ApiError = require("../utils/apiError");
const { signToken } = require("../utils/token");
const { serializeUser } = require("../utils/serialize");
const {
  requireBody,
  requireName,
  normalizeEmail,
  requirePassword,
  normalizeInterests,
} = require("../utils/validators");

// Valid bcrypt hash used only so a missing user still pays for a compare.
const DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

async function register(req, res) {
  const body = requireBody(req);
  const interests = normalizeInterests(body.interests === undefined ? [] : body.interests);
  const user = await User.create({
    name: requireName(body.name),
    email: normalizeEmail(body.email),
    password: requirePassword(body.password),
    interests,
    role: "user",
  });

  res.status(201).json({
    token: signToken(user),
    user: serializeUser(user),
  });
}

async function login(req, res) {
  const email = typeof req.body?.email === "string" ? req.body.email.trim().toLowerCase() : "";
  const password = typeof req.body?.password === "string" ? req.body.password : "";
  const emailLooksValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  const user = emailLooksValid ? await User.findOne({ email }).select("+password") : null;
  let matches = false;
  try {
    matches = await bcrypt.compare(password, user ? user.password : DUMMY_HASH);
  } catch (_err) {
    matches = false;
  }
  if (!user || !matches) {
    throw new ApiError(401, "Invalid email or password");
  }

  res.json({
    token: signToken(user),
    user: serializeUser(user),
  });
}

function me(req, res) {
  res.json({ user: serializeUser(req.user) });
}

module.exports = { register, login, me };
