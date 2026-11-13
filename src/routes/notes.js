const express = require("express");
const asyncHandler = require("../utils/asyncHandler");
const { requireAuth } = require("../middleware/auth");
const {
  listNotes,
  createNote,
  getNote,
  updateNote,
  deleteNote,
} = require("../controllers/noteController");

const router = express.Router();

router.use(requireAuth);
router.get("/", asyncHandler(listNotes));
router.post("/", asyncHandler(createNote));
router.get("/:id", asyncHandler(getNote));
router.put("/:id", asyncHandler(updateNote));
router.delete("/:id", asyncHandler(deleteNote));

module.exports = router;
