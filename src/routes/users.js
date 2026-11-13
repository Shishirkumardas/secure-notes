const express = require("express");
const asyncHandler = require("../utils/asyncHandler");
const { requireAuth, requireAdmin } = require("../middleware/auth");
const {
  listUsers,
  createUser,
  getUser,
  updateSelf,
  updateUser,
  deleteUser,
  groupByInterests,
  getUserPosts,
} = require("../controllers/userController");

const router = express.Router();

router.use(requireAuth);

router.get("/interests", requireAdmin, asyncHandler(groupByInterests));
router.put("/me", asyncHandler(updateSelf));

router.get("/", requireAdmin, asyncHandler(listUsers));
router.post("/", requireAdmin, asyncHandler(createUser));
router.get("/:id/posts", asyncHandler(getUserPosts));
router.get("/:id", requireAdmin, asyncHandler(getUser));
router.put("/:id", requireAdmin, asyncHandler(updateUser));
router.delete("/:id", requireAdmin, asyncHandler(deleteUser));

module.exports = router;
