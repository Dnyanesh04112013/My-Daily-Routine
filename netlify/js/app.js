(function (global) {
  const { DateTime: DT, ScheduleEngine: Engine, Store, Categories } = global.RoutineApp;

  const ICONS = {
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/></svg>',
    routine: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13"/><path d="M8 12h13"/><path d="M8 18h13"/><path d="M3 6h.01"/><path d="M3 12h.01"/><path d="M3 18h.01"/></svg>',
    calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="3"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/></svg>',
    habits: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="m9 12 2 2 4-4"/></svg>',
    profile: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 20a8 8 0 0 1 16 0"/></svg>'
  };

  function esc(str) {
    return String(str ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function categoryMeta(catKey) {
    return Categories.find((c) => c.key === catKey) || Categories[0];
  }

  const state = {
    profile: Store.restoreSession(),
    authMode: "CREATE",
    authError: "",
    authLoading: false,
    tab: "home",
    selectedDateKey: DT.todayDateKey(),
    calendarMode: "WEEK",
    calendarYear: new Date().getFullYear(),
    calendarMonth: new Date().getMonth(),
    reflowOnDrag: true,
    activities: [],
    habits: [],
    modal: null,
    toast: null,
    toastTimer: null,
    apkMeta: { checked: false, available: false, text: "Checking APK availability..." },
    draggedId: null
  };

  function loadCurrentUserData() {
    if (!state.profile) {
      state.activities = [];
      state.habits = [];
      Store.clearScheduledReminders();
      return;
    }
    const data = Store.getUserData(state.profile.uid);
    state.activities = Array.isArray(data.activities) ? data.activities : [];
    state.habits = Array.isArray(data.habits) ? data.habits : [];
    Store.scheduleReminders(state.activities, state.profile);
  }

  function persistUserData() {
    if (!state.profile) return;
    Store.saveUserData(state.profile.uid, {
      activities: state.activities,
      habits: state.habits
    });
    Store.scheduleReminders(state.activities, state.profile);
  }

  function applyTheme(mode) {
    const resolved = mode === "LIGHT" || mode === "DARK"
      ? mode.toLowerCase()
      : (global.matchMedia && global.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    document.documentElement.setAttribute("data-theme", resolved);
  }

  if (global.matchMedia) {
    global.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
      if (!state.profile || state.profile.themeMode === "SYSTEM") {
        applyTheme("SYSTEM");
      }
    });
  }

  function showToast(msg) {
    state.toast = msg;
    if (state.toastTimer) clearTimeout(state.toastTimer);
    renderToastOnly();
    state.toastTimer = setTimeout(() => {
      state.toast = null;
      renderToastOnly();
    }, 3200);
  }

  function renderToastOnly() {
    const el = document.getElementById("toast-root");
    if (!el) return;
    el.innerHTML = state.toast ? `<div class="toast" role="status">${esc(state.toast)}</div>` : "";
  }

  function checkApkAvailability() {
    if (state.apkMeta.checked) return;
    state.apkMeta.checked = true;
    fetch("MyDailyRoutine-v1.0.apk", { method: "HEAD" })
      .then((res) => {
        const ct = (res.headers.get("content-type") || "").toLowerCase();
        if (res.ok && !ct.includes("text/html")) {
          const bytes = Number(res.headers.get("content-length") || 0);
          const mb = bytes > 0 ? ` (${(bytes / (1024 * 1024)).toFixed(1)} MB)` : "";
          state.apkMeta = { checked: true, available: true, text: `MyDailyRoutine-v1.0.apk${mb}` };
        } else {
          state.apkMeta = {
            checked: true,
            available: false,
            text: "APK not bundled yet — place MyDailyRoutine-v1.0.apk in netlify/ to enable direct download."
          };
        }
        if (state.tab === "profile" && state.profile) render();
      })
      .catch(() => {
        state.apkMeta = {
          checked: true,
          available: false,
          text: "APK download available when hosted over HTTP(S) with MyDailyRoutine-v1.0.apk bundled."
        };
        if (state.tab === "profile" && state.profile) render();
      });
  }

  // ---------------- Mutations ----------------

  function dayActivities(dateKey) {
    return Engine.sortDayActivities(state.activities.filter((a) => a.dateKey === dateKey));
  }

  function toggleActivityComplete(id) {
    const target = state.activities.find((a) => a.id === id);
    if (!target) return;
    const completed = !target.completed;
    state.activities = state.activities.map((a) =>
      a.id === id ? { ...a, completed, status: completed ? "COMPLETED" : "SCHEDULED" } : a
    );
    persistUserData();
    showToast(completed ? `Completed "${target.title}"` : `Marked "${target.title}" pending`);
    render();
  }

  function saveActivityFromEditor(payload) {
    const {
      editingId, dateKey, title, description, category,
      startMinute, endMinute, flexible, reminderEnabled, reminderMinutesBefore,
      autoShiftOverlaps
    } = payload;

    const cleanTitle = (title || "").trim();
    if (!cleanTitle) {
      showToast("Please enter an activity title.");
      return;
    }
    if (endMinute <= startMinute) {
      showToast("End time must be after start time.");
      return;
    }

    const dayList = dayActivities(dateKey);
    const existing = editingId ? state.activities.find((a) => a.id === editingId) : null;
    const newItem = {
      id: existing ? existing.id : Store.uid(),
      dateKey,
      title: cleanTitle,
      description: (description || "").trim(),
      category,
      startMinute,
      endMinute,
      flexible,
      completed: existing ? existing.completed : false,
      status: existing ? existing.status : "SCHEDULED",
      sortOrder: existing ? existing.sortOrder : dayList.length,
      reminderEnabled,
      reminderMinutesBefore
    };

    const updatedMap = new Map();
    updatedMap.set(newItem.id, newItem);

    if (autoShiftOverlaps) {
      const buffer = Number(state.profile?.defaultBufferMinutes ?? 10);
      const dayEnd = Number(state.profile?.dayEndMinute ?? 1320);
      const overlaps = Engine.findOverlaps(dayList, startMinute, endMinute, newItem.id);
      let cursor = endMinute + buffer;
      overlaps.forEach((other) => {
        if (!other.completed) {
          const dur = Math.max(5, other.endMinute - other.startMinute);
          const nextStart = Math.min(cursor, Math.max(0, dayEnd - dur));
          const nextEnd = Math.min(1440, nextStart + dur);
          updatedMap.set(other.id, {
            ...other,
            startMinute: nextStart,
            endMinute: nextEnd,
            status: "RESCHEDULED"
          });
          cursor = nextEnd + buffer;
        }
      });
    }

    const existsInState = state.activities.some((a) => a.id === newItem.id);
    if (!existsInState) {
      state.activities = [...state.activities, newItem];
    }
    state.activities = state.activities.map((a) => updatedMap.get(a.id) || a);
    persistUserData();
    state.modal = null;
    showToast(existing ? "Activity updated" : "Activity added");
    render();
  }

  function deleteActivity(id) {
    state.activities = state.activities.filter((a) => a.id !== id);
    persistUserData();
    state.modal = null;
    showToast("Activity deleted");
    render();
  }

  function applyDelayCascade(targetId, delayMinutes) {
    const target = state.activities.find((a) => a.id === targetId);
    if (!target) return;
    const list = dayActivities(target.dateKey);
    const updates = Engine.delayAndCascade(
      list,
      targetId,
      delayMinutes,
      Number(state.profile?.dayEndMinute ?? 1320),
      Number(state.profile?.defaultBufferMinutes ?? 10)
    );
    const map = new Map(updates.map((u) => [u.id, u]));
    state.activities = state.activities.map((a) => map.get(a.id) || a);
    persistUserData();
    state.modal = null;
    showToast(updates.length > 1
      ? `Delayed by ${delayMinutes}m and shifted ${updates.length - 1} flexible task(s)`
      : `Delayed "${target.title}" by ${delayMinutes}m`);
    render();
  }

  function openReorganizePreview(dateKey) {
    const list = dayActivities(dateKey);
    const startFrom = dateKey === DT.todayDateKey()
      ? Math.max(Number(state.profile?.morningStartMinute ?? 420), DT.currentMinuteOfDay())
      : Number(state.profile?.morningStartMinute ?? 420);
    const result = Engine.reorganizeRemainingDay(
      list,
      startFrom,
      Number(state.profile?.dayEndMinute ?? 1320),
      Number(state.profile?.defaultBufferMinutes ?? 10)
    );
    state.modal = { type: "reorganize", dateKey, result };
    render();
  }

  function applyReorganizeResult(result) {
    const map = new Map(result.updatedActivities.map((u) => [u.id, u]));
    state.activities = state.activities.map((a) => map.get(a.id) || a);
    persistUserData();
    state.modal = null;
    showToast(result.movedCount > 0 ? `Reorganized ${result.movedCount} activities` : "Schedule is already optimal");
    render();
  }

  function commitReorderedDay(dateKey, orderedDayList) {
    const buffer = Number(state.profile?.defaultBufferMinutes ?? 10);
    const updates = state.reflowOnDrag
      ? Engine.reorderAndReflowSlots(orderedDayList, buffer)
      : orderedDayList.map((a, idx) => ({ ...a, sortOrder: idx }));
    const map = new Map(updates.map((u) => [u.id, u]));
    state.activities = state.activities.map((a) => map.get(a.id) || a);
    persistUserData();
    showToast(state.reflowOnDrag ? "Reordered & adjusted time slots" : "Updated activity order");
    render();
  }

  function moveActivityOrder(dateKey, id, delta) {
    const list = dayActivities(dateKey);
    const idx = list.findIndex((a) => a.id === id);
    const targetIdx = idx + delta;
    if (idx < 0 || targetIdx < 0 || targetIdx >= list.length) return;
    const copy = [...list];
    const [moved] = copy.splice(idx, 1);
    copy.splice(targetIdx, 0, moved);
    commitReorderedDay(dateKey, copy);
  }

  function rescheduleUnfinished(taskId, targetDateKey) {
    const task = state.activities.find((a) => a.id === taskId);
    if (!task) return;
    const targetList = dayActivities(targetDateKey);
    const earliest = targetDateKey === DT.todayDateKey()
      ? Math.max(Number(state.profile?.morningStartMinute ?? 420), DT.currentMinuteOfDay())
      : Number(state.profile?.morningStartMinute ?? 420);
    const dur = Math.max(15, task.endMinute - task.startMinute);
    const slot = Engine.findNextAvailableSlot(
      targetList,
      dur,
      earliest,
      Number(state.profile?.dayEndMinute ?? 1320),
      Number(state.profile?.defaultBufferMinutes ?? 10),
      task.id
    ) || { start: earliest, end: Math.min(1440, earliest + dur) };

    state.activities = state.activities.map((a) =>
      a.id === taskId
        ? { ...a, dateKey: targetDateKey, startMinute: slot.start, endMinute: slot.end, status: "RESCHEDULED" }
        : a
    );
    persistUserData();
    showToast(`Moved "${task.title}" to ${DT.formatFriendlyDayLabel(targetDateKey)} at ${DT.formatMinute(slot.start)}`);
    render();
  }

  function copyRoutineFromDate(sourceDateKey, targetDateKey) {
    const sourceList = dayActivities(sourceDateKey);
    if (!sourceList.length) {
      showToast(`No activities found on ${DT.formatFriendlyDayLabel(sourceDateKey)} to copy.`);
      return;
    }
    const existingCount = dayActivities(targetDateKey).length;
    const clones = sourceList.map((src, idx) => ({
      ...src,
      id: Store.uid(),
      dateKey: targetDateKey,
      completed: false,
      status: "SCHEDULED",
      sortOrder: existingCount + idx
    }));
    state.activities = [...state.activities, ...clones];
    persistUserData();
    showToast(`Copied ${clones.length} activities from ${DT.formatFriendlyDayLabel(sourceDateKey)}`);
    render();
  }

  function loadStarterRoutine(targetDateKey) {
    const existing = dayActivities(targetDateKey);
    const baseOrder = existing.length;
    const starter = [
      { title: "Morning Stretch & Hydration", desc: "10 min mobility + water before screens", cat: "HEALTH", start: 450, end: 480, flex: true },
      { title: "Deep Focus Work Block", desc: "Top-priority project milestone", cat: "FOCUS", start: 540, end: 660, flex: false },
      { title: "Mindful Lunch Break", desc: "Step away from desk & recharge", cat: "REST", start: 750, end: 810, flex: true },
      { title: "Skill Reading & Notes", desc: "25 minutes focused study", cat: "LEARNING", start: 960, end: 995, flex: true },
      { title: "Evening Walk & Plan Tomorrow", desc: "Light walk and quick day wrap-up", cat: "WELLNESS", start: 1110, end: 1150, flex: true }
    ].map((s, i) => ({
      id: Store.uid(),
      dateKey: targetDateKey,
      title: s.title,
      description: s.desc,
      category: s.cat,
      startMinute: s.start,
      endMinute: s.end,
      flexible: s.flex,
      completed: false,
      status: "SCHEDULED",
      sortOrder: baseOrder + i,
      reminderEnabled: false,
      reminderMinutesBefore: 10
    }));
    state.activities = [...state.activities, ...starter];
    persistUserData();
    showToast("Loaded starter daily routine");
    render();
  }

  function saveHabitFromModal(payload) {
    const cleanTitle = (payload.title || "").trim();
    if (!cleanTitle) {
      showToast("Please enter a habit name.");
      return;
    }
    if (payload.editingId) {
      state.habits = state.habits.map((h) =>
        h.id === payload.editingId
          ? { ...h, title: cleanTitle, category: payload.category, targetDaysPerWeek: payload.targetDaysPerWeek }
          : h
      );
      showToast("Habit updated");
    } else {
      state.habits = [
        ...state.habits,
        {
          id: Store.uid(),
          title: cleanTitle,
          category: payload.category,
          targetDaysPerWeek: payload.targetDaysPerWeek,
          completedDates: []
        }
      ];
      showToast("Habit created");
    }
    persistUserData();
    state.modal = null;
    render();
  }

  function deleteHabit(id) {
    state.habits = state.habits.filter((h) => h.id !== id);
    persistUserData();
    state.modal = null;
    showToast("Habit deleted");
    render();
  }

  function toggleHabitDate(habitId, dateKey) {
    state.habits = state.habits.map((h) => {
      if (h.id !== habitId) return h;
      const set = new Set(h.completedDates || []);
      if (set.has(dateKey)) set.delete(dateKey);
      else set.add(dateKey);
      return { ...h, completedDates: [...set].sort() };
    });
    persistUserData();
    render();
  }

  // ---------------- Renderers ----------------

  function renderAuth() {
    const isCreate = state.authMode === "CREATE";
    return `
      <div class="auth-shell">
        <div class="auth-card">
          <div class="brand-row">
            <div class="brand-mark">M</div>
            <div>
              <h2>My Daily Routine</h2>
              <div class="small muted">Flexible schedule &amp; habit planner</div>
            </div>
          </div>
          <p class="auth-subtitle">Design calm, flexible days that adapt with you—on any device.</p>

          <div class="segmented" style="margin-bottom: 18px;">
            <button type="button" class="${isCreate ? "active" : ""}" data-action="auth-mode" data-mode="CREATE">Create Account</button>
            <button type="button" class="${!isCreate ? "active" : ""}" data-action="auth-mode" data-mode="SIGN_IN">Sign In</button>
          </div>

          ${state.authError ? `<div class="banner-danger" style="margin-bottom: 14px;">${esc(state.authError)}</div>` : ""}

          <form id="auth-form" class="stack">
            <div class="field">
              <label for="auth-username">Username</label>
              <input id="auth-username" class="input" type="text" required minlength="2" maxlength="40" placeholder="Enter your username" autocomplete="username" />
            </div>
            <div class="field">
              <label for="auth-password">${isCreate ? "Create Password" : "Password"}</label>
              <input id="auth-password" class="input" type="password" required minlength="4" maxlength="128" placeholder="${isCreate ? "At least 4 characters" : "Enter your password"}" autocomplete="${isCreate ? "new-password" : "current-password"}" />
            </div>
            <button type="submit" class="btn btn-primary" ${state.authLoading ? "disabled" : ""}>
              ${state.authLoading ? "Please wait..." : (isCreate ? "Create Account & Continue" : "Sign In")}
            </button>
          </form>

          <p class="small muted" style="margin: 14px 0 0; text-align: center;">
            Saved locally in your browser with exportable JSON backups.
          </p>
        </div>
      </div>
    `;
  }

  function renderActivityCard(act, options = {}) {
    const cat = categoryMeta(act.category);
    const dur = DT.formatDuration(act.endMinute - act.startMinute);
    const timeRange = `${DT.formatMinute(act.startMinute)} - ${DT.formatMinute(act.endMinute)}`;
    const showReorder = Boolean(options.allowReorder);
    const idx = options.index ?? 0;
    const total = options.total ?? 1;

    return `
      <article
        class="activity-card ${act.completed ? "completed" : ""}"
        style="border-left-color: ${cat.color};"
        ${showReorder ? `draggable="true" data-drag-id="${esc(act.id)}"` : ""}
      >
        <div class="row-between" style="align-items: flex-start;">
          <div class="row-wrap" style="align-items: flex-start; flex: 1; flex-wrap: nowrap;">
            <button
              type="button"
              class="check-circle ${act.completed ? "checked" : ""}"
              data-action="toggle-complete"
              data-id="${esc(act.id)}"
              aria-label="${act.completed ? "Mark pending" : "Mark completed"}"
            >${act.completed ? "✓" : ""}</button>

            <div style="min-width: 0; flex: 1;">
              <div class="activity-title" style="font-weight: 700; font-size: 1.02rem;">${esc(act.title)}</div>
              <div class="small muted font-mono" style="margin-top: 2px;">${timeRange} &bull; ${dur}</div>
              ${act.description ? `<div class="small muted" style="margin-top: 4px;">${esc(act.description)}</div>` : ""}
            </div>
          </div>

          <div class="row-wrap" style="gap: 6px;">
            ${showReorder ? `
              <button type="button" class="icon-btn" title="Move up" data-action="move-up" data-id="${esc(act.id)}" ${idx === 0 ? "disabled" : ""}>▲</button>
              <button type="button" class="icon-btn" title="Move down" data-action="move-down" data-id="${esc(act.id)}" ${idx === total - 1 ? "disabled" : ""}>▼</button>
            ` : ""}
          </div>
        </div>

        <div class="row-between">
          <div class="row-wrap" style="gap: 6px;">
            <span class="pill" style="color: ${cat.color};">${esc(cat.label)}</span>
            <span class="pill">${act.flexible ? "Flexible" : "Fixed"}</span>
            ${act.status === "DELAYED" ? '<span class="pill pill-warn">Delayed</span>' : ""}
            ${act.status === "RESCHEDULED" ? '<span class="pill pill-primary">Adjusted</span>' : ""}
            ${act.reminderEnabled ? `<span class="pill">Alert ${act.reminderMinutesBefore}m before</span>` : ""}
          </div>
          <div class="row-wrap" style="gap: 6px;">
            ${!act.completed ? `<button type="button" class="btn btn-outline btn-sm" data-action="open-delay" data-id="${esc(act.id)}">Delay</button>` : ""}
            <button type="button" class="btn btn-outline btn-sm" data-action="edit-activity" data-id="${esc(act.id)}">Edit</button>
          </div>
        </div>
      </article>
    `;
  }

  function renderHome() {
    const todayKey = DT.todayDateKey();
    const todayList = dayActivities(todayKey);
    const total = todayList.length;
    const completed = todayList.filter((a) => a.completed).length;
    const pending = total - completed;
    const pct = total > 0 ? Math.round((completed / total) * 100) : 0;

    const unfinishedPast = state.activities
      .filter((a) => a.dateKey < todayKey && !a.completed)
      .sort((a, b) => b.dateKey.localeCompare(a.dateKey) || a.startMinute - b.startMinute)
      .slice(0, 5);

    const name = state.profile?.displayName || "Planner";

    return `
      <div class="stack">
        <section class="hero-card">
          <div class="eyebrow">${esc(DT.formatFullDate(todayKey))}</div>
          <h1 style="margin-top: 4px; font-size: clamp(1.5rem, 3.5vw, 2rem);">${esc(DT.greetingForHour())}, ${esc(name)}</h1>
          <p class="small muted" style="margin: 6px 0 16px;">
            ${total === 0 ? "Your schedule is open today. Add activities or load a starter template." : `${completed} of ${total} activities completed today.`}
          </p>

          <div class="row-between" style="margin-bottom: 6px;">
            <span class="small" style="font-weight: 600;">Daily Progress</span>
            <span class="font-mono" style="font-weight: 700; color: var(--primary);">${pct}%</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" style="width: ${pct}%;"></div>
          </div>

          <div class="row-wrap" style="margin-top: 16px;">
            <button type="button" class="btn btn-primary" data-action="open-add-activity" data-date="${todayKey}">+ Add Activity</button>
            ${pending > 0 ? `<button type="button" class="btn btn-secondary" data-action="open-reorganize" data-date="${todayKey}">Reorganize Remaining Day</button>` : ""}
          </div>
        </section>

        <section class="stats-3">
          <div class="stat-tile">
            <div class="stat-value font-mono">${total}</div>
            <div class="small muted">Total Tasks</div>
          </div>
          <div class="stat-tile">
            <div class="stat-value font-mono" style="color: var(--cat-health);">${completed}</div>
            <div class="small muted">Completed</div>
          </div>
          <div class="stat-tile">
            <div class="stat-value font-mono" style="color: var(--secondary);">${pending}</div>
            <div class="small muted">Pending</div>
          </div>
        </section>

        ${unfinishedPast.length ? `
          <section class="card">
            <div class="row-between" style="margin-bottom: 10px;">
              <div>
                <h3>Unfinished Tasks</h3>
                <div class="small muted">Carry unfinished items from earlier days forward</div>
              </div>
            </div>
            <div class="stack">
              ${unfinishedPast.map((task) => `
                <div class="card-soft row-between">
                  <div>
                    <div style="font-weight: 700;">${esc(task.title)}</div>
                    <div class="small muted font-mono">${esc(DT.formatFriendlyDayLabel(task.dateKey))} &bull; ${DT.formatMinute(task.startMinute)}</div>
                  </div>
                  <div class="row-wrap">
                    <button type="button" class="btn btn-secondary btn-sm" data-action="reschedule-today" data-id="${esc(task.id)}">Today</button>
                    <button type="button" class="btn btn-outline btn-sm" data-action="reschedule-tomorrow" data-id="${esc(task.id)}">Tomorrow</button>
                  </div>
                </div>
              `).join("")}
            </div>
          </section>
        ` : ""}

        <section class="stack">
          <div class="row-between">
            <h2>Today's Schedule</h2>
            <button type="button" class="btn btn-outline btn-sm" data-action="go-routine-today">Manage &amp; Reorder</button>
          </div>
          ${todayList.length ? todayList.map((a) => renderActivityCard(a)).join("") : `
            <div class="empty-box stack" style="align-items: center;">
              <h3>No activities scheduled for today</h3>
              <p class="small muted" style="margin: 0; max-width: 420px;">
                Start quickly with a balanced starter routine, copy yesterday's plan, or add your own custom time blocks.
              </p>
              <div class="row-wrap" style="justify-content: center;">
                <button type="button" class="btn btn-primary" data-action="load-starter" data-date="${todayKey}">Load Starter Routine</button>
                <button type="button" class="btn btn-outline" data-action="copy-yesterday" data-date="${todayKey}">Copy Yesterday</button>
              </div>
            </div>
          `}
        </section>
      </div>
    `;
  }

  function renderRoutine() {
    const dk = state.selectedDateKey;
    const list = dayActivities(dk);
    const pendingCount = list.filter((a) => !a.completed).length;

    return `
      <div class="stack">
        <div class="row-between">
          <div>
            <h1>My Routine</h1>
            <div class="small muted">Drag or use arrows to reorder; adjust timings anytime</div>
          </div>
          <button type="button" class="btn btn-primary" data-action="open-add-activity" data-date="${dk}">+ New Activity</button>
        </div>

        <section class="card">
          <div class="row-between">
            <button type="button" class="icon-btn" data-action="shift-selected-date" data-delta="-1" aria-label="Previous day">&larr;</button>
            <div style="text-align: center;">
              <div style="font-weight: 700;">${esc(DT.formatFriendlyDayLabel(dk))}</div>
              <div class="small muted">${esc(DT.formatFullDate(dk))}</div>
            </div>
            <button type="button" class="icon-btn" data-action="shift-selected-date" data-delta="1" aria-label="Next day">&rarr;</button>
          </div>

          <div class="row-between" style="margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--border);">
            <div class="row-wrap">
              ${dk !== DT.todayDateKey() ? `<button type="button" class="btn btn-outline btn-sm" data-action="select-date" data-date="${DT.todayDateKey()}">Today</button>` : ""}
              <button type="button" class="btn btn-outline btn-sm" data-action="copy-prev-day" data-date="${dk}">Copy Previous Day</button>
              ${pendingCount > 0 ? `<button type="button" class="btn btn-secondary btn-sm" data-action="open-reorganize" data-date="${dk}">Reorganize Remaining</button>` : ""}
            </div>

            <div class="switch-row">
              <span class="small" style="font-weight: 600;">Reflow on Drag</span>
              <button type="button" class="toggle ${state.reflowOnDrag ? "on" : ""}" data-action="toggle-reflow" aria-pressed="${state.reflowOnDrag}"></button>
            </div>
          </div>
        </section>

        <section class="stack" id="routine-drag-list">
          ${list.length ? list.map((a, idx) => renderActivityCard(a, { allowReorder: true, index: idx, total: list.length })).join("") : `
            <div class="empty-box stack" style="align-items: center;">
              <h3>Nothing planned for ${esc(DT.formatFriendlyDayLabel(dk))}</h3>
              <p class="small muted" style="margin: 0;">Add an activity, load a starter template, or copy the previous day's schedule.</p>
              <div class="row-wrap" style="justify-content: center;">
                <button type="button" class="btn btn-primary" data-action="open-add-activity" data-date="${dk}">+ Add Activity</button>
                <button type="button" class="btn btn-outline" data-action="load-starter" data-date="${dk}">Load Starter Template</button>
              </div>
            </div>
          `}
        </section>
      </div>
    `;
  }

  function renderCalendar() {
    const dk = state.selectedDateKey;
    const todayKey = DT.todayDateKey();
    const list = dayActivities(dk);

    let calendarGridHtml = "";
    if (state.calendarMode === "WEEK") {
      const weekKeys = DT.weekDateKeys(dk);
      calendarGridHtml = `
        <div class="week-strip">
          ${weekKeys.map((k) => {
            const count = state.activities.filter((a) => a.dateKey === k).length;
            const cls = [
              "day-cell",
              k === dk ? "selected" : "",
              k === todayKey ? "today" : ""
            ].filter(Boolean).join(" ");
            return `
              <button type="button" class="${cls}" data-action="select-date" data-date="${k}">
                <span class="small" style="font-size: 0.72rem; opacity: 0.85;">${DT.shortDayName(k)}</span>
                <span class="font-mono" style="font-weight: 700;">${DT.dayNumber(k)}</span>
                <div class="dot-row">
                  ${Array.from({ length: Math.min(3, count) }, () => '<span class="dot"></span>').join("")}
                </div>
              </button>
            `;
          }).join("")}
        </div>
      `;
    } else if (state.calendarMode === "MONTH") {
      const blanks = DT.monthLeadingBlanks(state.calendarYear, state.calendarMonth);
      const monthKeys = DT.monthDateKeys(state.calendarYear, state.calendarMonth);
      calendarGridHtml = `
        <div class="row-between" style="margin-bottom: 10px;">
          <button type="button" class="icon-btn" data-action="shift-month" data-delta="-1" aria-label="Previous month">&larr;</button>
          <strong>${esc(DT.monthTitle(state.calendarYear, state.calendarMonth))}</strong>
          <button type="button" class="icon-btn" data-action="shift-month" data-delta="1" aria-label="Next month">&rarr;</button>
        </div>
        <div class="cal-month-grid">
          ${["M", "T", "W", "T", "F", "S", "S"].map((h) => `<div class="cal-day-header">${h}</div>`).join("")}
          ${Array.from({ length: blanks }, () => "<div></div>").join("")}
          ${monthKeys.map((k) => {
            const count = state.activities.filter((a) => a.dateKey === k).length;
            const cls = [
              "day-cell",
              k === dk ? "selected" : "",
              k === todayKey ? "today" : ""
            ].filter(Boolean).join(" ");
            return `
              <button type="button" class="${cls}" data-action="select-date" data-date="${k}">
                <span class="font-mono" style="font-weight: 700; font-size: 0.88rem;">${DT.dayNumber(k)}</span>
                <div class="dot-row">
                  ${Array.from({ length: Math.min(3, count) }, () => '<span class="dot"></span>').join("")}
                </div>
              </button>
            `;
          }).join("")}
        </div>
      `;
    } else {
      calendarGridHtml = `
        <div class="row-between">
          <button type="button" class="icon-btn" data-action="shift-selected-date" data-delta="-1" aria-label="Previous day">&larr;</button>
          <div style="text-align: center;">
            <strong>${esc(DT.formatFriendlyDayLabel(dk))}</strong>
            <div class="small muted">${esc(DT.formatFullDate(dk))}</div>
          </div>
          <button type="button" class="icon-btn" data-action="shift-selected-date" data-delta="1" aria-label="Next day">&rarr;</button>
        </div>
      `;
    }

    return `
      <div class="stack">
        <div class="row-between">
          <div>
            <h1>Calendar</h1>
            <div class="small muted">Switch Daily, Weekly, or Monthly views &amp; plan any date</div>
          </div>
          <button type="button" class="btn btn-outline btn-sm" data-action="select-date" data-date="${todayKey}">Jump to Today</button>
        </div>

        <div class="segmented">
          <button type="button" class="${state.calendarMode === "DAY" ? "active" : ""}" data-action="cal-mode" data-mode="DAY">Daily</button>
          <button type="button" class="${state.calendarMode === "WEEK" ? "active" : ""}" data-action="cal-mode" data-mode="WEEK">Weekly</button>
          <button type="button" class="${state.calendarMode === "MONTH" ? "active" : ""}" data-action="cal-mode" data-mode="MONTH">Monthly</button>
        </div>

        <section class="card">${calendarGridHtml}</section>

        <section class="stack">
          <div class="row-between">
            <div>
              <h2>${esc(DT.formatFriendlyDayLabel(dk))}</h2>
              <div class="small muted">${list.length} scheduled activities</div>
            </div>
            <div class="row-wrap">
              <button type="button" class="btn btn-outline btn-sm" data-action="copy-prev-day" data-date="${dk}">Copy Previous Day</button>
              <button type="button" class="btn btn-primary btn-sm" data-action="open-add-activity" data-date="${dk}">+ Add for Date</button>
            </div>
          </div>

          ${list.length ? list.map((a) => renderActivityCard(a)).join("") : `
            <div class="empty-box">
              <p class="small muted" style="margin: 0 0 12px;">No routine created for ${esc(DT.formatFullDate(dk))} yet.</p>
              <button type="button" class="btn btn-primary btn-sm" data-action="open-add-activity" data-date="${dk}">Create Routine Block</button>
            </div>
          `}
        </section>
      </div>
    `;
  }

  function renderHabits() {
    const todayKey = DT.todayDateKey();
    const recent7 = DT.recent7DayKeys(todayKey);
    const enriched = state.habits.map((h) => ({ habit: h, stats: Engine.computeHabitStats(h, todayKey) }));
    const dueToday = enriched.filter((x) => !x.stats.completedToday);
    const completedToday = enriched.filter((x) => x.stats.completedToday);

    const totalWeeklyDone = enriched.reduce((acc, x) => acc + x.stats.weeklyCompleted, 0);
    const totalWeeklyTarget = Math.max(1, enriched.reduce((acc, x) => acc + x.stats.weeklyTarget, 0));
    const weeklyPct = enriched.length ? Math.min(100, Math.round((totalWeeklyDone / totalWeeklyTarget) * 100)) : 0;

    const totalMonthlyDone = enriched.reduce((acc, x) => acc + x.stats.monthlyCompleted, 0);
    const totalMonthlyTarget = Math.max(1, enriched.reduce((acc, x) => acc + x.stats.monthlyTarget, 0));
    const monthlyPct = enriched.length ? Math.min(100, Math.round((totalMonthlyDone / totalMonthlyTarget) * 100)) : 0;

    const renderHabitCard = ({ habit, stats }) => {
      const cat = categoryMeta(habit.category);
      const wPct = Math.min(100, Math.round((stats.weeklyCompleted / stats.weeklyTarget) * 100));
      const mPct = Math.min(100, Math.round((stats.monthlyCompleted / stats.monthlyTarget) * 100));
      return `
        <article class="card stack">
          <div class="row-between">
            <div>
              <div style="font-weight: 700; font-size: 1.04rem;">${esc(habit.title)}</div>
              <div class="row-wrap" style="margin-top: 4px; gap: 6px;">
                <span class="pill" style="color: ${cat.color};">${esc(cat.label)}</span>
                <span class="pill">Streak: ${stats.currentStreak}d</span>
                <span class="pill">Best: ${stats.bestStreak}d</span>
              </div>
            </div>
            <div class="row-wrap">
              <button type="button" class="btn btn-outline btn-sm" data-action="edit-habit" data-id="${esc(habit.id)}">Edit</button>
              <button
                type="button"
                class="btn ${stats.completedToday ? "btn-secondary" : "btn-primary"} btn-sm"
                data-action="toggle-habit-date"
                data-id="${esc(habit.id)}"
                data-date="${todayKey}"
              >${stats.completedToday ? "Done Today ✓" : "Complete Today"}</button>
            </div>
          </div>

          <div class="habit-days">
            ${recent7.map((dKey) => {
              const done = (habit.completedDates || []).includes(dKey);
              return `
                <button
                  type="button"
                  class="habit-day-btn ${done ? "done" : ""}"
                  data-action="toggle-habit-date"
                  data-id="${esc(habit.id)}"
                  data-date="${dKey}"
                  title="${esc(dKey)}"
                >
                  <span>${DT.shortDayLetter(dKey)}</span>
                  <span class="font-mono">${done ? "✓" : DT.dayNumber(dKey)}</span>
                </button>
              `;
            }).join("")}
          </div>

          <div class="stats-2">
            <div>
              <div class="row-between small"><span class="muted">Weekly (${stats.weeklyCompleted}/${stats.weeklyTarget})</span><span class="font-mono">${wPct}%</span></div>
              <div class="progress-track" style="margin-top: 4px; height: 7px;"><div class="progress-fill" style="width: ${wPct}%;"></div></div>
            </div>
            <div>
              <div class="row-between small"><span class="muted">Monthly (${stats.monthlyCompleted}/${stats.monthlyTarget})</span><span class="font-mono">${mPct}%</span></div>
              <div class="progress-track" style="margin-top: 4px; height: 7px;"><div class="progress-fill" style="width: ${mPct}%;"></div></div>
            </div>
          </div>
        </article>
      `;
    };

    return `
      <div class="stack">
        <div class="row-between">
          <div>
            <h1>Habit Tracker</h1>
            <div class="small muted">Build daily streaks &amp; monitor weekly and monthly consistency</div>
          </div>
          <button type="button" class="btn btn-primary" data-action="open-add-habit">+ New Habit</button>
        </div>

        <section class="stats-2">
          <div class="card">
            <div class="small muted">Weekly Consistency</div>
            <div class="stat-value font-mono" style="margin: 6px 0;">${weeklyPct}%</div>
            <div class="progress-track"><div class="progress-fill" style="width: ${weeklyPct}%;"></div></div>
          </div>
          <div class="card">
            <div class="small muted">Monthly Consistency</div>
            <div class="stat-value font-mono" style="margin: 6px 0;">${monthlyPct}%</div>
            <div class="progress-track"><div class="progress-fill" style="width: ${monthlyPct}%;"></div></div>
          </div>
        </section>

        ${!enriched.length ? `
          <div class="empty-box stack" style="align-items: center;">
            <h3>No habits tracked yet</h3>
            <p class="small muted" style="margin: 0;">Add daily habits like Hydration, Reading, Meditation, or Exercise to track streaks.</p>
            <button type="button" class="btn btn-primary" data-action="open-add-habit">+ Create First Habit</button>
          </div>
        ` : `
          <section class="stack">
            <h2>Due / Missed Today (${dueToday.length})</h2>
            ${dueToday.length ? dueToday.map(renderHabitCard).join("") : '<div class="card-soft small muted">All habits completed for today!</div>'}
          </section>

          <section class="stack">
            <h2>Completed Today (${completedToday.length})</h2>
            ${completedToday.length ? completedToday.map(renderHabitCard).join("") : '<div class="card-soft small muted">Complete a habit above to see it here.</div>'}
          </section>
        `}
      </div>
    `;
  }

  function renderProfile() {
    const p = state.profile;
    const todayKey = DT.todayDateKey();
    const totalCompletedTasks = state.activities.filter((a) => a.completed).length;
    const bestStreak = state.habits.reduce((mx, h) => Math.max(mx, Engine.computeHabitStats(h, todayKey).bestStreak), 0);
    const initials = (p.displayName || "U")
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((s) => s[0].toUpperCase())
      .join("") || "U";

    return `
      <div class="stack">
        <h1>Profile &amp; Settings</h1>

        <section class="card row-wrap">
          <div class="avatar">${esc(initials)}</div>
          <div style="flex: 1; min-width: 180px;">
            <h2 style="font-size: 1.25rem;">${esc(p.displayName)}</h2>
            <div class="row-wrap" style="margin-top: 6px;">
              <span class="pill pill-primary">@${esc(p.username)}</span>
              <span class="pill">Saved Locally</span>
            </div>
          </div>
        </section>

        <section class="card stack">
          <div class="row-between">
            <div>
              <h3>Android App Package (APK)</h3>
              <div class="small muted">${esc(state.apkMeta.text)}</div>
            </div>
            <span class="pill pill-primary font-mono">v1.0</span>
          </div>
          <div class="row-wrap">
            <a
              class="btn btn-primary"
              href="MyDailyRoutine-v1.0.apk"
              download="MyDailyRoutine-v1.0.apk"
              aria-disabled="${state.apkMeta.available ? "false" : "true"}"
            >Download Android APK</a>
          </div>
        </section>

        <section class="stats-2">
          <div class="stat-tile">
            <div class="stat-value font-mono">${totalCompletedTasks}</div>
            <div class="small muted">Tasks Done</div>
          </div>
          <div class="stat-tile">
            <div class="stat-value font-mono">${bestStreak}d</div>
            <div class="small muted">Best Habit Streak</div>
          </div>
        </section>

        <section class="card stack">
          <h3>Appearance</h3>
          <div class="segmented">
            ${["LIGHT", "DARK", "SYSTEM"].map((mode) => `
              <button
                type="button"
                class="${p.themeMode === mode ? "active" : ""}"
                data-action="set-theme"
                data-mode="${mode}"
              >${mode[0] + mode.slice(1).toLowerCase()}</button>
            `).join("")}
          </div>
        </section>

        <section class="card stack">
          <h3>Schedule Preferences</h3>
          <form id="profile-prefs-form" class="stack">
            <div class="field">
              <label for="pref-name">Display Name</label>
              <input id="pref-name" class="input" type="text" maxlength="40" value="${esc(p.displayName)}" required />
            </div>

            <div class="stats-2">
              <div class="field">
                <label for="pref-start">Day Starts</label>
                <input id="pref-start" class="input font-mono" type="time" value="${DT.minuteToInputTime(p.morningStartMinute)}" />
              </div>
              <div class="field">
                <label for="pref-end">Day Ends</label>
                <input id="pref-end" class="input font-mono" type="time" value="${DT.minuteToInputTime(Math.min(1439, p.dayEndMinute))}" />
              </div>
            </div>

            <div class="field">
              <label>Reorganize Buffer Between Tasks</label>
              <div class="chip-row">
                ${[0, 5, 10, 15, 20].map((m) => `
                  <button
                    type="button"
                    class="chip ${Number(p.defaultBufferMinutes) === m ? "selected" : ""}"
                    data-action="set-buffer"
                    data-mins="${m}"
                  >${m}m</button>
                `).join("")}
              </div>
            </div>

            <div class="switch-row">
              <div>
                <div style="font-weight: 600;">Browser Activity Reminders</div>
                <div class="small muted">Receive notifications when a scheduled block approaches</div>
              </div>
              <button
                type="button"
                class="toggle ${p.notificationsEnabled ? "on" : ""}"
                data-action="toggle-notifs"
                aria-pressed="${Boolean(p.notificationsEnabled)}"
              ></button>
            </div>

            <button type="submit" class="btn btn-primary">Save Preferences</button>
          </form>
        </section>

        <section class="card stack">
          <h3>Backup &amp; Portability</h3>
          <div class="small muted">Export your routines and habits as JSON, or restore from a backup file.</div>
          <div class="row-wrap">
            <button type="button" class="btn btn-outline" data-action="export-backup">Export JSON Backup</button>
            <label class="btn btn-outline" style="margin: 0;">
              Import JSON Backup
              <input id="import-backup-input" type="file" accept="application/json,.json" hidden />
            </label>
          </div>
        </section>

        <button type="button" class="btn btn-danger" data-action="sign-out">Sign Out</button>
      </div>
    `;
  }

  function renderModal() {
    const m = state.modal;
    if (!m) return "";

    if (m.type === "activity") {
      const a = m.draft;
      const dayList = dayActivities(a.dateKey);
      const overlaps = Engine.findOverlaps(dayList, a.startMinute, a.endMinute, m.editingId);
      const dur = Math.max(5, a.endMinute - a.startMinute);

      return `
        <div class="modal-backdrop" data-action="close-modal-backdrop">
          <div class="modal-panel stack">
            <div class="row-between">
              <h2>${m.editingId ? "Edit Activity" : "New Activity"}</h2>
              <button type="button" class="icon-btn" data-action="close-modal" aria-label="Close">✕</button>
            </div>

            <div class="field">
              <label for="act-title">Activity Name</label>
              <input id="act-title" class="input" type="text" maxlength="80" placeholder="e.g., Morning Focus Sprint" value="${esc(a.title)}" />
            </div>

            <div class="field">
              <label for="act-desc">Description</label>
              <textarea id="act-desc" class="textarea" maxlength="300" placeholder="Optional notes or goals">${esc(a.description)}</textarea>
            </div>

            <div class="stats-2">
              <div class="field">
                <label for="act-date">Date</label>
                <input id="act-date" class="input" type="date" value="${esc(a.dateKey)}" />
              </div>
              <div class="field">
                <label for="act-cat">Category</label>
                <select id="act-cat" class="select">
                  ${Categories.map((c) => `<option value="${c.key}" ${a.category === c.key ? "selected" : ""}>${esc(c.label)}</option>`).join("")}
                </select>
              </div>
            </div>

            <div class="stats-2">
              <div class="field">
                <label for="act-start">Start Time</label>
                <input id="act-start" class="input font-mono" type="time" value="${DT.minuteToInputTime(a.startMinute)}" />
              </div>
              <div class="field">
                <label for="act-end">End Time (${DT.formatDuration(dur)})</label>
                <input id="act-end" class="input font-mono" type="time" value="${DT.minuteToInputTime(Math.min(1439, a.endMinute))}" />
              </div>
            </div>

            ${overlaps.length ? `
              <div class="banner-warn stack" style="gap: 8px;">
                <div><strong>Overlap Warning:</strong> Collides with ${overlaps.map((o) => `"${esc(o.title)}" (${DT.formatMinute(o.startMinute)}-${DT.formatMinute(o.endMinute)})`).join(", ")}.</div>
                <div class="row-wrap">
                  <button type="button" class="btn btn-outline btn-sm" data-action="auto-fit-slot">Auto-Fit Next Free Slot</button>
                  <label class="row-wrap small" style="gap: 6px; cursor: pointer;">
                    <input type="checkbox" id="act-autoshift" ${m.autoShiftOverlaps ? "checked" : ""} />
                    Shift conflicting tasks on save
                  </label>
                </div>
              </div>
            ` : ""}

            <div class="switch-row">
              <div>
                <div style="font-weight: 600;">Flexible Time Slot</div>
                <div class="small muted">Allow Reorganize &amp; Delay Cascade to adjust this block</div>
              </div>
              <button type="button" class="toggle ${a.flexible ? "on" : ""}" data-action="draft-toggle-flex"></button>
            </div>

            <div class="switch-row">
              <div>
                <div style="font-weight: 600;">Reminder Alert</div>
                <div class="small muted">Notify before start time</div>
              </div>
              <button type="button" class="toggle ${a.reminderEnabled ? "on" : ""}" data-action="draft-toggle-rem"></button>
            </div>

            ${a.reminderEnabled ? `
              <div class="chip-row">
                ${[0, 5, 10, 15, 30].map((mins) => `
                  <button type="button" class="chip ${a.reminderMinutesBefore === mins ? "selected" : ""}" data-action="draft-rem-mins" data-mins="${mins}">
                    ${mins === 0 ? "At start" : `${mins}m before`}
                  </button>
                `).join("")}
              </div>
            ` : ""}

            <div class="row-between" style="margin-top: 6px;">
              ${m.editingId ? `<button type="button" class="btn btn-danger" data-action="delete-activity" data-id="${esc(m.editingId)}">Delete</button>` : "<span></span>"}
              <div class="row-wrap">
                <button type="button" class="btn btn-outline" data-action="close-modal">Cancel</button>
                <button type="button" class="btn btn-primary" data-action="save-activity">Save Activity</button>
              </div>
            </div>
          </div>
        </div>
      `;
    }

    if (m.type === "delay") {
      const act = state.activities.find((a) => a.id === m.activityId);
      if (!act) return "";
      return `
        <div class="modal-backdrop" data-action="close-modal-backdrop">
          <div class="modal-panel stack">
            <div class="row-between">
              <h2>Running Late?</h2>
              <button type="button" class="icon-btn" data-action="close-modal" aria-label="Close">✕</button>
            </div>
            <p class="small muted" style="margin: 0;">
              Delaying <strong>${esc(act.title)}</strong> automatically cascades any colliding flexible tasks forward while keeping fixed anchors in place.
            </p>
            <div class="chip-row">
              ${[10, 15, 30, 45, 60].map((mins) => `
                <button type="button" class="btn btn-secondary" data-action="apply-delay" data-id="${esc(act.id)}" data-mins="${mins}">+${mins} min</button>
              `).join("")}
            </div>
          </div>
        </div>
      `;
    }

    if (m.type === "reorganize") {
      const r = m.result;
      return `
        <div class="modal-backdrop" data-action="close-modal-backdrop">
          <div class="modal-panel stack">
            <div class="row-between">
              <h2>Reorganize Remaining Day</h2>
              <button type="button" class="icon-btn" data-action="close-modal" aria-label="Close">✕</button>
            </div>
            <p class="small muted" style="margin: 0;">
              ${r.movedCount > 0 ? `Proposed adjustments for ${r.movedCount} flexible task(s):` : "No changes needed—your remaining flexible tasks already fit cleanly."}
            </p>
            ${r.unscheduledOverflowCount > 0 ? `<div class="banner-warn">${r.unscheduledOverflowCount} task(s) extend past your Day End setting and were kept unchanged.</div>` : ""}
            <div class="stack" style="max-height: 280px; overflow-y: auto;">
              ${r.updatedActivities.map((u) => `
                <div class="card-soft row-between">
                  <span style="font-weight: 600;">${esc(u.title)}</span>
                  <span class="font-mono small" style="color: var(--primary);">${DT.formatMinute(u.startMinute)} - ${DT.formatMinute(u.endMinute)}</span>
                </div>
              `).join("")}
            </div>
            <div class="row-between">
              <button type="button" class="btn btn-outline" data-action="close-modal">Cancel</button>
              ${r.movedCount > 0 ? `<button type="button" class="btn btn-primary" data-action="confirm-reorganize">Apply Plan</button>` : ""}
            </div>
          </div>
        </div>
      `;
    }

    if (m.type === "habit") {
      const h = m.draft;
      return `
        <div class="modal-backdrop" data-action="close-modal-backdrop">
          <div class="modal-panel stack">
            <div class="row-between">
              <h2>${m.editingId ? "Edit Habit" : "New Daily Habit"}</h2>
              <button type="button" class="icon-btn" data-action="close-modal" aria-label="Close">✕</button>
            </div>
            <div class="field">
              <label for="hab-title">Habit Name</label>
              <input id="hab-title" class="input" type="text" maxlength="60" placeholder="e.g., Drink 2L Water" value="${esc(h.title)}" />
            </div>
            <div class="field">
              <label for="hab-cat">Category</label>
              <select id="hab-cat" class="select">
                ${Categories.map((c) => `<option value="${c.key}" ${h.category === c.key ? "selected" : ""}>${esc(c.label)}</option>`).join("")}
              </select>
            </div>
            <div class="field">
              <label>Weekly Target (${h.targetDaysPerWeek} days/week)</label>
              <div class="chip-row">
                ${[1, 2, 3, 4, 5, 6, 7].map((d) => `
                  <button type="button" class="chip ${h.targetDaysPerWeek === d ? "selected" : ""}" data-action="habit-target" data-days="${d}">${d}d</button>
                `).join("")}
              </div>
            </div>
            <div class="row-between">
              ${m.editingId ? `<button type="button" class="btn btn-danger" data-action="delete-habit" data-id="${esc(m.editingId)}">Delete</button>` : "<span></span>"}
              <div class="row-wrap">
                <button type="button" class="btn btn-outline" data-action="close-modal">Cancel</button>
                <button type="button" class="btn btn-primary" data-action="save-habit">Save Habit</button>
              </div>
            </div>
          </div>
        </div>
      `;
    }

    return "";
  }

  function render() {
    const root = document.getElementById("app-root");
    if (!root) return;

    if (!state.profile) {
      applyTheme("SYSTEM");
      root.innerHTML = renderAuth();
      bindEvents();
      renderToastOnly();
      return;
    }

    applyTheme(state.profile.themeMode);
    checkApkAvailability();

    const navItems = [
      { id: "home", label: "Home", icon: ICONS.home },
      { id: "routine", label: "My Routine", icon: ICONS.routine },
      { id: "calendar", label: "Calendar", icon: ICONS.calendar },
      { id: "habits", label: "Habits", icon: ICONS.habits },
      { id: "profile", label: "Profile", icon: ICONS.profile }
    ];

    let pageHtml = "";
    if (state.tab === "home") pageHtml = renderHome();
    else if (state.tab === "routine") pageHtml = renderRoutine();
    else if (state.tab === "calendar") pageHtml = renderCalendar();
    else if (state.tab === "habits") pageHtml = renderHabits();
    else pageHtml = renderProfile();

    const showFab = state.tab === "home" || state.tab === "routine" || state.tab === "habits";

    root.innerHTML = `
      <div class="app-shell">
        <aside class="nav-rail" aria-label="Main navigation">
          <div class="rail-brand">
            <div class="brand-mark">M</div>
            <div>
              <div style="font-weight: 700; font-size: 0.96rem;">My Daily Routine</div>
              <div class="small muted">${esc(state.profile.displayName)}</div>
            </div>
          </div>
          ${navItems.map((item) => `
            <button type="button" class="nav-btn ${state.tab === item.id ? "active" : ""}" data-action="switch-tab" data-tab="${item.id}">
              ${item.icon}<span>${item.label}</span>
            </button>
          `).join("")}
        </aside>

        <main class="main-area">${pageHtml}</main>

        ${showFab ? `
          <button
            type="button"
            class="fab"
            aria-label="${state.tab === "habits" ? "Add habit" : "Add activity"}"
            data-action="${state.tab === "habits" ? "open-add-habit" : "open-add-activity"}"
            data-date="${state.tab === "home" ? DT.todayDateKey() : state.selectedDateKey}"
          >+</button>
        ` : ""}

        <nav class="bottom-nav" aria-label="Bottom navigation">
          ${navItems.map((item) => `
            <button type="button" class="nav-btn ${state.tab === item.id ? "active" : ""}" data-action="switch-tab" data-tab="${item.id}">
              ${item.icon}<span>${item.label}</span>
            </button>
          `).join("")}
        </nav>
      </div>
      ${renderModal()}
    `;

    bindEvents();
    renderToastOnly();
  }

  function syncDraftFromDom() {
    if (!state.modal || state.modal.type !== "activity") return;
    const d = state.modal.draft;
    const titleEl = document.getElementById("act-title");
    const descEl = document.getElementById("act-desc");
    const dateEl = document.getElementById("act-date");
    const catEl = document.getElementById("act-cat");
    const startEl = document.getElementById("act-start");
    const endEl = document.getElementById("act-end");
    const shiftEl = document.getElementById("act-autoshift");
    if (titleEl) d.title = titleEl.value;
    if (descEl) d.description = descEl.value;
    if (dateEl && dateEl.value) d.dateKey = dateEl.value;
    if (catEl) d.category = catEl.value;
    if (startEl) d.startMinute = DT.inputTimeToMinute(startEl.value, d.startMinute);
    if (endEl) {
      const parsedEnd = DT.inputTimeToMinute(endEl.value, d.endMinute);
      d.endMinute = parsedEnd === 0 ? 1440 : parsedEnd;
    }
    if (shiftEl) state.modal.autoShiftOverlaps = shiftEl.checked;
  }

  function bindEvents() {
    const authForm = document.getElementById("auth-form");
    if (authForm) {
      authForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const username = document.getElementById("auth-username").value;
        const password = document.getElementById("auth-password").value;
        state.authLoading = true;
        state.authError = "";
        render();
        try {
          state.profile = state.authMode === "CREATE"
            ? await Store.createAccount(username, password)
            : await Store.signIn(username, password);
          state.authLoading = false;
          loadCurrentUserData();
          showToast(`Welcome, ${state.profile.displayName}!`);
          render();
        } catch (err) {
          state.authLoading = false;
          state.authError = err.message || "Authentication failed.";
          render();
        }
      });
    }

    const prefsForm = document.getElementById("profile-prefs-form");
    if (prefsForm) {
      prefsForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const name = document.getElementById("pref-name").value;
        const start = DT.inputTimeToMinute(document.getElementById("pref-start").value, state.profile.morningStartMinute);
        let end = DT.inputTimeToMinute(document.getElementById("pref-end").value, state.profile.dayEndMinute);
        if (end <= start) end = Math.min(1440, start + 60);
        state.profile = Store.updateProfilePreferences(state.profile.uid, {
          displayName: name,
          morningStartMinute: start,
          dayEndMinute: end
        });
        Store.scheduleReminders(state.activities, state.profile);
        showToast("Preferences saved");
        render();
      });
    }

    const importInput = document.getElementById("import-backup-input");
    if (importInput) {
      importInput.addEventListener("change", (e) => {
        const file = e.target.files && e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = () => {
          try {
            const parsed = JSON.parse(String(reader.result || "{}"));
            if (!Array.isArray(parsed.activities) || !Array.isArray(parsed.habits)) {
              throw new Error("Invalid backup format.");
            }
            state.activities = parsed.activities;
            state.habits = parsed.habits;
            persistUserData();
            showToast(`Restored ${state.activities.length} activities & ${state.habits.length} habits`);
            render();
          } catch (err) {
            showToast(err.message || "Could not import JSON backup.");
          }
        };
        reader.readAsText(file);
      });
    }

    // Activity modal live time/date change overlap check
    ["act-date", "act-start", "act-end"].forEach((id) => {
      const el = document.getElementById(id);
      if (el) {
        el.addEventListener("change", () => {
          syncDraftFromDom();
          render();
        });
      }
    });

    // Drag & Drop in Routine tab
    const dragList = document.getElementById("routine-drag-list");
    if (dragList) {
      dragList.querySelectorAll("[data-drag-id]").forEach((card) => {
        card.addEventListener("dragstart", (e) => {
          state.draggedId = card.getAttribute("data-drag-id");
          card.classList.add("dragging");
          if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
        });
        card.addEventListener("dragend", () => {
          card.classList.remove("dragging");
          dragList.querySelectorAll(".drag-over").forEach((c) => c.classList.remove("drag-over"));
          state.draggedId = null;
        });
        card.addEventListener("dragover", (e) => {
          e.preventDefault();
          card.classList.add("drag-over");
        });
        card.addEventListener("dragleave", () => {
          card.classList.remove("drag-over");
        });
        card.addEventListener("drop", (e) => {
          e.preventDefault();
          card.classList.remove("drag-over");
          const targetId = card.getAttribute("data-drag-id");
          if (!state.draggedId || !targetId || state.draggedId === targetId) return;
          const list = dayActivities(state.selectedDateKey);
          const fromIdx = list.findIndex((a) => a.id === state.draggedId);
          const toIdx = list.findIndex((a) => a.id === targetId);
          if (fromIdx < 0 || toIdx < 0) return;
          const next = [...list];
          const [item] = next.splice(fromIdx, 1);
          next.splice(toIdx, 0, item);
          state.draggedId = null;
          commitReorderedDay(state.selectedDateKey, next);
        });
      });
    }
  }

  // Delegated click handler
  document.addEventListener("click", (e) => {
    const target = e.target.closest("[data-action]");
    if (!target) return;
    const action = target.getAttribute("data-action");

    if (action === "auth-mode") {
      state.authMode = target.getAttribute("data-mode");
      state.authError = "";
      render();
      return;
    }

    if (action === "switch-tab") {
      state.tab = target.getAttribute("data-tab");
      render();
      return;
    }

    if (action === "go-routine-today") {
      state.selectedDateKey = DT.todayDateKey();
      state.tab = "routine";
      render();
      return;
    }

    if (action === "select-date") {
      state.selectedDateKey = target.getAttribute("data-date");
      const d = DT.parseDateKey(state.selectedDateKey);
      state.calendarYear = d.getFullYear();
      state.calendarMonth = d.getMonth();
      render();
      return;
    }

    if (action === "shift-selected-date") {
      const delta = Number(target.getAttribute("data-delta"));
      state.selectedDateKey = DT.addDays(state.selectedDateKey, delta);
      const d = DT.parseDateKey(state.selectedDateKey);
      state.calendarYear = d.getFullYear();
      state.calendarMonth = d.getMonth();
      render();
      return;
    }

    if (action === "shift-month") {
      const delta = Number(target.getAttribute("data-delta"));
      const d = new Date(state.calendarYear, state.calendarMonth + delta, 1);
      state.calendarYear = d.getFullYear();
      state.calendarMonth = d.getMonth();
      render();
      return;
    }

    if (action === "cal-mode") {
      state.calendarMode = target.getAttribute("data-mode");
      render();
      return;
    }

    if (action === "toggle-complete") {
      toggleActivityComplete(target.getAttribute("data-id"));
      return;
    }

    if (action === "move-up") {
      moveActivityOrder(state.selectedDateKey, target.getAttribute("data-id"), -1);
      return;
    }

    if (action === "move-down") {
      moveActivityOrder(state.selectedDateKey, target.getAttribute("data-id"), 1);
      return;
    }

    if (action === "toggle-reflow") {
      state.reflowOnDrag = !state.reflowOnDrag;
      render();
      return;
    }

    if (action === "open-add-activity") {
      const dk = target.getAttribute("data-date") || state.selectedDateKey;
      const dayList = dayActivities(dk);
      const earliest = dk === DT.todayDateKey()
        ? Math.max(Number(state.profile?.morningStartMinute ?? 420), DT.currentMinuteOfDay())
        : Number(state.profile?.morningStartMinute ?? 420);
      const freeSlot = Engine.findNextAvailableSlot(
        dayList,
        45,
        earliest,
        Number(state.profile?.dayEndMinute ?? 1320),
        Number(state.profile?.defaultBufferMinutes ?? 10)
      ) || { start: 540, end: 585 };

      state.modal = {
        type: "activity",
        editingId: null,
        autoShiftOverlaps: false,
        draft: {
          dateKey: dk,
          title: "",
          description: "",
          category: "FOCUS",
          startMinute: freeSlot.start,
          endMinute: freeSlot.end,
          flexible: true,
          reminderEnabled: Boolean(state.profile?.notificationsEnabled),
          reminderMinutesBefore: 10
        }
      };
      render();
      return;
    }

    if (action === "edit-activity") {
      const act = state.activities.find((a) => a.id === target.getAttribute("data-id"));
      if (!act) return;
      state.modal = {
        type: "activity",
        editingId: act.id,
        autoShiftOverlaps: false,
        draft: { ...act }
      };
      render();
      return;
    }

    if (action === "draft-toggle-flex") {
      syncDraftFromDom();
      state.modal.draft.flexible = !state.modal.draft.flexible;
      render();
      return;
    }

    if (action === "draft-toggle-rem") {
      syncDraftFromDom();
      state.modal.draft.reminderEnabled = !state.modal.draft.reminderEnabled;
      render();
      return;
    }

    if (action === "draft-rem-mins") {
      syncDraftFromDom();
      state.modal.draft.reminderMinutesBefore = Number(target.getAttribute("data-mins"));
      render();
      return;
    }

    if (action === "auto-fit-slot") {
      syncDraftFromDom();
      const d = state.modal.draft;
      const dur = Math.max(15, d.endMinute - d.startMinute);
      const slot = Engine.findNextAvailableSlot(
        dayActivities(d.dateKey),
        dur,
        d.startMinute,
        Number(state.profile?.dayEndMinute ?? 1320),
        Number(state.profile?.defaultBufferMinutes ?? 10),
        state.modal.editingId
      );
      if (slot) {
        d.startMinute = slot.start;
        d.endMinute = slot.end;
        render();
      } else {
        showToast("No free slot found before your Day End time.");
      }
      return;
    }

    if (action === "save-activity") {
      syncDraftFromDom();
      saveActivityFromEditor({
        editingId: state.modal.editingId,
        autoShiftOverlaps: state.modal.autoShiftOverlaps,
        ...state.modal.draft
      });
      return;
    }

    if (action === "delete-activity") {
      deleteActivity(target.getAttribute("data-id"));
      return;
    }

    if (action === "open-delay") {
      state.modal = { type: "delay", activityId: target.getAttribute("data-id") };
      render();
      return;
    }

    if (action === "apply-delay") {
      applyDelayCascade(target.getAttribute("data-id"), Number(target.getAttribute("data-mins")));
      return;
    }

    if (action === "open-reorganize") {
      openReorganizePreview(target.getAttribute("data-date"));
      return;
    }

    if (action === "confirm-reorganize") {
      if (state.modal && state.modal.result) {
        applyReorganizeResult(state.modal.result);
      }
      return;
    }

    if (action === "reschedule-today") {
      rescheduleUnfinished(target.getAttribute("data-id"), DT.todayDateKey());
      return;
    }

    if (action === "reschedule-tomorrow") {
      rescheduleUnfinished(target.getAttribute("data-id"), DT.tomorrowDateKey());
      return;
    }

    if (action === "copy-yesterday" || action === "copy-prev-day") {
      const targetDk = target.getAttribute("data-date");
      copyRoutineFromDate(DT.addDays(targetDk, -1), targetDk);
      return;
    }

    if (action === "load-starter") {
      loadStarterRoutine(target.getAttribute("data-date"));
      return;
    }

    if (action === "open-add-habit") {
      state.modal = {
        type: "habit",
        editingId: null,
        draft: { title: "", category: "HEALTH", targetDaysPerWeek: 5 }
      };
      render();
      return;
    }

    if (action === "edit-habit") {
      const hab = state.habits.find((h) => h.id === target.getAttribute("data-id"));
      if (!hab) return;
      state.modal = {
        type: "habit",
        editingId: hab.id,
        draft: { title: hab.title, category: hab.category, targetDaysPerWeek: hab.targetDaysPerWeek }
      };
      render();
      return;
    }

    if (action === "habit-target") {
      const titleEl = document.getElementById("hab-title");
      const catEl = document.getElementById("hab-cat");
      if (titleEl) state.modal.draft.title = titleEl.value;
      if (catEl) state.modal.draft.category = catEl.value;
      state.modal.draft.targetDaysPerWeek = Number(target.getAttribute("data-days"));
      render();
      return;
    }

    if (action === "save-habit") {
      const titleEl = document.getElementById("hab-title");
      const catEl = document.getElementById("hab-cat");
      saveHabitFromModal({
        editingId: state.modal.editingId,
        title: titleEl ? titleEl.value : state.modal.draft.title,
        category: catEl ? catEl.value : state.modal.draft.category,
        targetDaysPerWeek: state.modal.draft.targetDaysPerWeek
      });
      return;
    }

    if (action === "delete-habit") {
      deleteHabit(target.getAttribute("data-id"));
      return;
    }

    if (action === "toggle-habit-date") {
      toggleHabitDate(target.getAttribute("data-id"), target.getAttribute("data-date"));
      return;
    }

    if (action === "set-theme") {
      const mode = target.getAttribute("data-mode");
      state.profile = Store.updateProfilePreferences(state.profile.uid, { themeMode: mode });
      render();
      return;
    }

    if (action === "set-buffer") {
      const mins = Number(target.getAttribute("data-mins"));
      state.profile = Store.updateProfilePreferences(state.profile.uid, { defaultBufferMinutes: mins });
      render();
      return;
    }

    if (action === "toggle-notifs") {
      const next = !state.profile.notificationsEnabled;
      if (next && "Notification" in global && global.Notification.permission === "default") {
        global.Notification.requestPermission().then((perm) => {
          const enabled = perm === "granted";
          state.profile = Store.updateProfilePreferences(state.profile.uid, { notificationsEnabled: enabled });
          Store.scheduleReminders(state.activities, state.profile);
          showToast(enabled ? "Browser reminders enabled" : "Notification permission declined");
          render();
        });
      } else {
        state.profile = Store.updateProfilePreferences(state.profile.uid, { notificationsEnabled: next });
        Store.scheduleReminders(state.activities, state.profile);
        render();
      }
      return;
    }

    if (action === "export-backup") {
      const blob = new Blob([JSON.stringify({
        exportedAt: new Date().toISOString(),
        username: state.profile.username,
        activities: state.activities,
        habits: state.habits
      }, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `my-daily-routine-${DT.todayDateKey()}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      showToast("Exported JSON backup");
      return;
    }

    if (action === "sign-out") {
      Store.signOut();
      Store.clearScheduledReminders();
      state.profile = null;
      state.activities = [];
      state.habits = [];
      state.tab = "home";
      render();
      return;
    }

    if (action === "close-modal") {
      state.modal = null;
      render();
      return;
    }

    if (action === "close-modal-backdrop" && e.target === target) {
      state.modal = null;
      render();
    }
  });

  loadCurrentUserData();
  render();
})(window);
