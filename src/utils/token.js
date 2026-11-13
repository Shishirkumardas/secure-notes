const jwt = require("jsonwebtoken");

function secret() {
  const value = process.env.JWT_SECRET;
  if (!value) {
    const error = new Error("Authentication is not configured");
    error.statusCode = 500;
    throw error;
  }
  return value;
}

function signToken(user) {
  return jwt.sign(
    {
      sub: String(user._id),
      tv: user.tokenVersion || 0,
    },
    secret(),
    { expiresIn: process.env.JWT_EXPIRES_IN || "7d" }
  );
}

function verifyToken(token) {
  return jwt.verify(token, secret());
}

module.exports = { signToken, verifyToken };
