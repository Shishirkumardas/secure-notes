function errorHandler(err, req, res, next) {
  if (res.headersSent) {
    next(err);
    return;
  }

  if (err.type === "entity.parse.failed" || (err instanceof SyntaxError && err.status === 400)) {
    res.status(400).json({ error: "Request body must be valid JSON" });
    return;
  }

  if (err.code === 11000) {
    res.status(409).json({ error: "Email is already in use" });
    return;
  }

  if (err.name === "ValidationError") {
    res.status(400).json({ error: err.message });
    return;
  }

  if (err.name === "CastError") {
    res.status(400).json({ error: "Invalid id" });
    return;
  }

  const status = err.statusCode || err.status || 500;
  if (status >= 500) {
    console.error(err);
  }

  const message =
    status >= 500 && process.env.NODE_ENV === "production"
      ? "Internal server error"
      : err.message || "Internal server error";

  res.status(status).json({ error: message });
}

module.exports = errorHandler;
