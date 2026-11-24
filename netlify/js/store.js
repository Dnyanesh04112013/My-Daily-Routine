(function (global) {
  const USERS_KEY = "mdr_users_v1";
  const SESSION_KEY = "mdr_session_uid_v1";
  const DATA_PREFIX = "mdr_data_v1_";

  const CATEGORIES = [
    { key: "FOCUS", label: "Focus & Work", color: "var(--cat-focus)" },
    { key: "HEALTH", label: "Health & Fitness", color: "var(--cat-health)" },
    { key: "WELLNESS", label: "Mindfulness", color: "var(--cat-wellness)" },
    { key: "LEARNING", label: "Learning", color: "var(--cat-learning)" },
    { key: "PERSONAL", label: "Personal", color: "var(--cat-personal)" },
    { key: "REST", label: "Rest & Break", color: "var(--cat-rest)" }
  ];

  function uid() {
    if (global.crypto && typeof global.crypto.randomUUID === "function") {
      return global.crypto.randomUUID();
    }
    return "id_" + Date.now().toString(36) + "_" + Math.random().toString(36).slice(2, 10);
  }

  function randomSaltHex() {
    const bytes = new Uint8Array(16);
    if (global.crypto && typeof global.crypto.getRandomValues === "function") {
      global.crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  }

  function hexToBytes(hex) {
    const out = new Uint8Array(hex.length / 2);
    for (let i = 0; i < out.length; i++) {
      out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  function bytesToHex(bytes) {
    return Array.from(new Uint8Array(bytes), (b) => b.toString(16).padStart(2, "0")).join("");
  }

  function fallbackDigest(saltHex, password) {
    let h1 = 0x811c9dc5;
    let h2 = 0x9e3779b9;
    const input = saltHex + ":" + password;
    for (let round = 0; round < 2048; round++) {
      for (let i = 0; i < input.length; i++) {
        const c = input.charCodeAt(i) ^ (round & 0xff);
        h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0;
        h2 = Math.imul(h2 ^ (c + round), 0x85ebca6b) >>> 0;
      }
    }
    return "fb:" + saltHex + ":" + h1.toString(16).padStart(8, "0") + h2.toString(16).padStart(8, "0");
  }

  async function hashPassword(password, existingSaltHex = null) {
    const saltHex = existingSaltHex || randomSaltHex();
    if (global.crypto && global.crypto.subtle && global.TextEncoder) {
      try {
        const enc = new TextEncoder();
        const keyMat = await global.crypto.subtle.importKey(
          "raw",
          enc.encode(password),
          { name: "PBKDF2" },
          false,
          ["deriveBits"]
        );
        const bits = await global.crypto.subtle.deriveBits(
          { name: "PBKDF2", salt: hexToBytes(saltHex), iterations: 65536, hash: "SHA-256" },
          keyMat,
          256
        );
        return "pbkdf2:" + saltHex + ":" + bytesToHex(bits);
      } catch (_) {
        return fallbackDigest(saltHex, password);
      }
    }
    return fallbackDigest(saltHex, password);
  }

  function constantTimeEquals(a, b) {
    if (a.length !== b.length) return false;
    let diff = 0;
    for (let i = 0; i < a.length; i++) {
      diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
    }
    return diff === 0;
  }

  async function verifyPassword(password, storedHash) {
    if (!storedHash || !storedHash.includes(":")) return false;
    const parts = storedHash.split(":");
    const algo = parts[0];
    const saltHex = parts[1];
    if (!saltHex) return false;
    if (algo === "fb") {
      return constantTimeEquals(fallbackDigest(saltHex, password), storedHash);
    }
    const derived = await hashPassword(password, saltHex);
    return constantTimeEquals(derived, storedHash);
  }

  function readJson(key, fallback) {
    try {
      const raw = global.localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (_) {
      return fallback;
    }
  }

  function writeJson(key, value) {
    try {
      global.localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (_) {
      return false;
    }
  }

  function getUsers() {
    return readJson(USERS_KEY, []);
  }

  function saveUsers(users) {
    return writeJson(USERS_KEY, users);
  }

  function getUserData(userId) {
    return readJson(DATA_PREFIX + userId, { activities: [], habits: [] });
  }

  function saveUserData(userId, data) {
    return writeJson(DATA_PREFIX + userId, data);
  }

  async function createAccount(usernameRaw, password) {
    const username = (usernameRaw || "").trim();
    if (username.length < 2 || username.length > 40) {
      throw new Error("Username must be between 2 and 40 characters.");
    }
    if (!password || password.length < 4 || password.length > 128) {
      throw new Error("Password must be at least 4 characters.");
    }

    const users = getUsers();
    const usernameLower = username.toLowerCase();
    if (users.some((u) => u.usernameLower === usernameLower)) {
      throw new Error("That username is already taken. Sign in instead.");
    }

    const passwordHash = await hashPassword(password);
    const profile = {
      uid: uid(),
      username,
      usernameLower,
      displayName: username,
      passwordHash,
      themeMode: "SYSTEM",
      morningStartMinute: 420,
      dayEndMinute: 1320,
      defaultBufferMinutes: 10,
      notificationsEnabled: false,
      createdAt: Date.now()
    };

    users.push(profile);
    saveUsers(users);
    saveUserData(profile.uid, { activities: [], habits: [] });
    global.localStorage.setItem(SESSION_KEY, profile.uid);
    return sanitizeProfile(profile);
  }

  async function signIn(usernameRaw, password) {
    const username = (usernameRaw || "").trim();
    if (!username || !password) {
      throw new Error("Enter both your username and password.");
    }
    const users = getUsers();
    const user = users.find((u) => u.usernameLower === username.toLowerCase());
    if (!user) {
      throw new Error("No account found with that username.");
    }
    const ok = await verifyPassword(password, user.passwordHash);
    if (!ok) {
      throw new Error("Incorrect password. Please try again.");
    }
    global.localStorage.setItem(SESSION_KEY, user.uid);
    return sanitizeProfile(user);
  }

  function signOut() {
    global.localStorage.removeItem(SESSION_KEY);
  }

  function restoreSession() {
    const activeId = global.localStorage.getItem(SESSION_KEY);
    if (!activeId) return null;
    const users = getUsers();
    const user = users.find((u) => u.uid === activeId);
    return user ? sanitizeProfile(user) : null;
  }

  function sanitizeProfile(user) {
    const { passwordHash, ...safe } = user;
    return safe;
  }

  function updateProfilePreferences(userId, patch) {
    const users = getUsers();
    const idx = users.findIndex((u) => u.uid === userId);
    if (idx < 0) return null;
    const nextDisplay = (patch.displayName || users[idx].displayName).trim().slice(0, 40) || users[idx].displayName;
    users[idx] = {
      ...users[idx],
      displayName: nextDisplay,
      themeMode: patch.themeMode || users[idx].themeMode,
      morningStartMinute: Number(patch.morningStartMinute ?? users[idx].morningStartMinute),
      dayEndMinute: Number(patch.dayEndMinute ?? users[idx].dayEndMinute),
      defaultBufferMinutes: Number(patch.defaultBufferMinutes ?? users[idx].defaultBufferMinutes),
      notificationsEnabled: Boolean(patch.notificationsEnabled)
    };
    saveUsers(users);
    return sanitizeProfile(users[idx]);
  }

  let reminderTimers = [];

  function clearScheduledReminders() {
    reminderTimers.forEach((id) => clearTimeout(id));
    reminderTimers = [];
  }

  function scheduleReminders(activities, profile) {
    clearScheduledReminders();
    if (!profile || !profile.notificationsEnabled) return;
    if (!("Notification" in global) || global.Notification.permission !== "granted") return;

    const DT = global.RoutineApp.DateTime;
    const todayKey = DT.todayDateKey();
    const nowMin = DT.currentMinuteOfDay();

    activities
      .filter((a) => a.dateKey === todayKey && !a.completed && a.reminderEnabled)
      .forEach((act) => {
        const triggerMin = act.startMinute - (Number(act.reminderMinutesBefore) || 10);
        const delayMs = (triggerMin - nowMin) * 60 * 1000;
        if (delayMs > 0 && delayMs < 24 * 60 * 60 * 1000) {
          const timerId = setTimeout(() => {
            try {
              new global.Notification(`Upcoming: ${act.title}`, {
                body: `Starts at ${DT.formatMinute(act.startMinute)} (${DT.formatDuration(act.endMinute - act.startMinute)})`
              });
            } catch (_) {}
          }, delayMs);
          reminderTimers.push(timerId);
        }
      });
  }

  global.RoutineApp = global.RoutineApp || {};
  global.RoutineApp.Categories = CATEGORIES;
  global.RoutineApp.Store = {
    uid,
    createAccount,
    signIn,
    signOut,
    restoreSession,
    getUserData,
    saveUserData,
    updateProfilePreferences,
    scheduleReminders,
    clearScheduledReminders
  };
})(window);
