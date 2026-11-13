const User = require("../models/User");
const ApiError = require("../utils/apiError");
const { verifyToken } = require("../utils/token");

// Authorization uses the user loaded by _id. The JWT only identifies the session.
async function requireAuth(req, res, next) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) {
    next(new ApiError(401, "Authentication required"));
    return;
  }

  let payload;
  try {
    payload = verifyToken(token);
  } catch (err) {
    if (err.name === "JsonWebTokenError" || err.name === "TokenExpiredError") {
      next(new ApiError(401, "Authentication required"));
      return;
    }
    next(err);
    return;
  }

  if (typeof payload.sub !== "string" || !/^[a-fA-F0-9]{24}$/.test(payload.sub)) {
    next(new ApiError(401, "Authentication required"));
    return;
  }

  try {
    const user = await User.findById(payload.sub);
    if (!user || (payload.tv || 0) !== (user.tokenVersion || 0)) {
      throw new ApiError(401, "Authentication required");
    }
    req.user = user;
    next();
  } catch (err) {
    next(err);
  }
}

function requireAdmin(req, res, next) {
  if (!req.user || req.user.role !== "admin") {
    next(new ApiError(403, "Admin access required"));
    return;
  }
  next();
}

module.exports = { requireAuth, requireAdmin };
