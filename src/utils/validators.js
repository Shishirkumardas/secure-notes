const mongoose = require("mongoose");
const ApiError = require("./apiError");

function requireBody(req) {
  if (!req.body || typeof req.body !== "object" || Array.isArray(req.body)) {
    throw new ApiError(400, "Request body must be a JSON object");
  }
  return req.body;
}

function requireName(name) {
  if (typeof name !== "string" || !name.trim()) {
    throw new ApiError(400, "Name is required");
  }
  const value = name.trim();
  if (value.length > 80) {
    throw new ApiError(400, "Name must be 80 characters or fewer");
  }
  return value;
}

function normalizeEmail(email) {
  if (typeof email !== "string") {
    throw new ApiError(400, "Email is required");
  }
  const value = email.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    throw new ApiError(400, "Email is invalid");
  }
  return value;
}

function requirePassword(password) {
  if (typeof password !== "string" || password.length < 8) {
    throw new ApiError(400, "Password must be at least 8 characters");
  }
  if (password.length > 72) {
    throw new ApiError(400, "Password must be 72 characters or fewer");
  }
  return password;
}

function optionalPassword(password) {
  if (password === undefined || password === "") return undefined;
  return requirePassword(password);
}

function normalizeInterests(interests) {
  if (interests === undefined) return undefined;
  if (!Array.isArray(interests)) {
    throw new ApiError(400, "Interests must be an array of strings");
  }
  const cleaned = [];
  const seen = new Set();
  for (const value of interests) {
    if (typeof value !== "string") {
      throw new ApiError(400, "Each interest must be a string");
    }
    const interest = value.trim().toLowerCase();
    if (!interest) continue;
    if (interest.length > 40) {
      throw new ApiError(400, "Each interest must be 40 characters or fewer");
    }
    if (!seen.has(interest)) {
      seen.add(interest);
      cleaned.push(interest);
    }
  }
  if (cleaned.length > 20) {
    throw new ApiError(400, "A profile can have at most 20 interests");
  }
  return cleaned;
}

function requireText(value, label, max) {
  if (typeof value !== "string" || !value.trim()) {
    throw new ApiError(400, `${label} is required`);
  }
  const text = value.trim();
  if (text.length > max) {
    throw new ApiError(400, `${label} must be ${max} characters or fewer`);
  }
  return text;
}

function requireRole(role) {
  if (role !== "user" && role !== "admin") {
    throw new ApiError(400, "Role must be user or admin");
  }
  return role;
}

function requireObjectId(value, label) {
  if (typeof value !== "string" || !/^[a-fA-F0-9]{24}$/.test(value)) {
    throw new ApiError(400, `${label} is invalid`);
  }
  if (!mongoose.Types.ObjectId.isValid(value)) {
    throw new ApiError(400, `${label} is invalid`);
  }
  return value;
}

module.exports = {
  requireBody,
  requireName,
  normalizeEmail,
  requirePassword,
  optionalPassword,
  normalizeInterests,
  requireText,
  requireRole,
  requireObjectId,
};
