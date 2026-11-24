(function (global) {
  const DAYS_SHORT = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const DAYS_FULL = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
  const MONTHS_SHORT = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const MONTHS_FULL = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  ];

  function pad2(n) {
    return String(n).padStart(2, "0");
  }

  function toDateKey(date) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  }

  function parseDateKey(key) {
    const [y, m, d] = key.split("-").map(Number);
    return new Date(y, (m || 1) - 1, d || 1);
  }

  function todayDateKey() {
    return toDateKey(new Date());
  }

  function addDays(dateKey, delta) {
    const d = parseDateKey(dateKey);
    d.setDate(d.getDate() + delta);
    return toDateKey(d);
  }

  function yesterdayDateKey() {
    return addDays(todayDateKey(), -1);
  }

  function tomorrowDateKey() {
    return addDays(todayDateKey(), 1);
  }

  function formatMinute(minuteOfDay) {
    const clamped = Math.max(0, Math.min(1440, Math.round(minuteOfDay)));
    if (clamped === 1440) return "24:00";
    const h = Math.floor(clamped / 60);
    const m = clamped % 60;
    return `${pad2(h)}:${pad2(m)}`;
  }

  function minuteToInputTime(minuteOfDay) {
    const clamped = Math.max(0, Math.min(1439, Math.round(minuteOfDay)));
    return `${pad2(Math.floor(clamped / 60))}:${pad2(clamped % 60)}`;
  }

  function inputTimeToMinute(val, fallback) {
    if (!val || !val.includes(":")) return fallback;
    const [h, m] = val.split(":").map(Number);
    if (Number.isNaN(h) || Number.isNaN(m)) return fallback;
    return Math.max(0, Math.min(1439, h * 60 + m));
  }

  function formatDuration(minutes) {
    const mins = Math.max(1, Math.round(minutes));
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    if (h > 0 && m > 0) return `${h}h ${m}m`;
    if (h > 0) return `${h}h`;
    return `${m}m`;
  }

  function currentMinuteOfDay() {
    const now = new Date();
    return now.getHours() * 60 + now.getMinutes();
  }

  function greetingForHour() {
    const h = new Date().getHours();
    if (h < 12) return "Good morning";
    if (h < 17) return "Good afternoon";
    return "Good evening";
  }

  function formatFullDate(dateKey) {
    const d = parseDateKey(dateKey);
    return `${DAYS_FULL[d.getDay()]}, ${MONTHS_SHORT[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
  }

  function formatFriendlyDayLabel(dateKey) {
    const today = todayDateKey();
    if (dateKey === today) return "Today";
    if (dateKey === yesterdayDateKey()) return "Yesterday";
    if (dateKey === tomorrowDateKey()) return "Tomorrow";
    const d = parseDateKey(dateKey);
    return `${DAYS_SHORT[d.getDay()]}, ${MONTHS_SHORT[d.getMonth()]} ${d.getDate()}`;
  }

  function weekDateKeys(anchorDateKey) {
    const d = parseDateKey(anchorDateKey);
    const mondayOffset = (d.getDay() + 6) % 7;
    const start = addDays(anchorDateKey, -mondayOffset);
    return Array.from({ length: 7 }, (_, i) => addDays(start, i));
  }

  function recent7DayKeys(anchorDateKey) {
    return Array.from({ length: 7 }, (_, i) => addDays(anchorDateKey, i - 6));
  }

  function monthDateKeys(year, monthIndex) {
    const days = new Date(year, monthIndex + 1, 0).getDate();
    return Array.from({ length: days }, (_, i) => `${year}-${pad2(monthIndex + 1)}-${pad2(i + 1)}`);
  }

  function monthTitle(year, monthIndex) {
    return `${MONTHS_FULL[monthIndex]} ${year}`;
  }

  function monthLeadingBlanks(year, monthIndex) {
    const firstDay = new Date(year, monthIndex, 1).getDay();
    return (firstDay + 6) % 7; // Monday-first
  }

  function dayNumber(dateKey) {
    return parseDateKey(dateKey).getDate();
  }

  function shortDayName(dateKey) {
    return DAYS_SHORT[parseDateKey(dateKey).getDay()];
  }

  function shortDayLetter(dateKey) {
    return DAYS_SHORT[parseDateKey(dateKey).getDay()].slice(0, 1);
  }

  global.RoutineApp = global.RoutineApp || {};
  global.RoutineApp.DateTime = {
    todayDateKey,
    yesterdayDateKey,
    tomorrowDateKey,
    addDays,
    formatMinute,
    minuteToInputTime,
    inputTimeToMinute,
    formatDuration,
    currentMinuteOfDay,
    greetingForHour,
    formatFullDate,
    formatFriendlyDayLabel,
    weekDateKeys,
    recent7DayKeys,
    monthDateKeys,
    monthTitle,
    monthLeadingBlanks,
    dayNumber,
    shortDayName,
    shortDayLetter,
    parseDateKey
  };
})(window);
