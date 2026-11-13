const mongoose = require("mongoose");
const bcrypt = require("bcryptjs");

const userSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, maxlength: 80 },
    email: { type: String, required: true, trim: true, lowercase: true, maxlength: 254 },
    password: { type: String, required: true, minlength: 8, select: false },
    role: { type: String, enum: ["user", "admin"], default: "user" },
    interests: {
      type: [{ type: String, maxlength: 40 }],
      default: [],
    },
    // Incremented on password change. Checked in memory after the _id lookup.
    tokenVersion: { type: Number, default: 0 },
  },
  { timestamps: true }
);

// Login finds a user by email.
userSchema.index({ email: 1 }, { unique: true });

// Admin user list is sorted by createdAt descending and paginated.
userSchema.index({ createdAt: -1 });

// Group-by-interests aggregation starts with a match on this multikey field.
userSchema.index({ interests: 1 });

userSchema.pre("save", async function hashPassword() {
  if (!this.isModified("password")) return;
  this.password = await bcrypt.hash(this.password, 10);
});

userSchema.methods.comparePassword = function comparePassword(candidate) {
  return bcrypt.compare(candidate, this.password);
};

module.exports = mongoose.model("User", userSchema);
