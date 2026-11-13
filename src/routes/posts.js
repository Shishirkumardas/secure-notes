const express = require("express");
const asyncHandler = require("../utils/asyncHandler");
const { requireAuth } = require("../middleware/auth");
const {
  listPosts,
  createPost,
  getPost,
  updatePost,
  deletePost,
} = require("../controllers/postController");

const router = express.Router();

router.use(requireAuth);
router.get("/", asyncHandler(listPosts));
router.post("/", asyncHandler(createPost));
router.get("/:id", asyncHandler(getPost));
router.put("/:id", asyncHandler(updatePost));
router.delete("/:id", asyncHandler(deletePost));

module.exports = router;
