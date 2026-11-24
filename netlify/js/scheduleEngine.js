(function (global) {
  function sortDayActivities(list) {
    return [...list].sort((a, b) => (a.startMinute - b.startMinute) || (a.sortOrder - b.sortOrder));
  }

  function durationOf(act) {
    return Math.max(5, Math.min(720, act.endMinute - act.startMinute));
  }

  function fitAfterFixed(earliestStart, duration, fixedBlocks, dayEnd, bufferMinutes) {
    let candidate = Math.max(0, Math.min(1435, earliestStart));
    const orderedFixed = [...fixedBlocks].sort((a, b) => a.startMinute - b.startMinute);
    for (const fixed of orderedFixed) {
      if (candidate + duration <= fixed.startMinute) break;
      if (candidate < fixed.endMinute && candidate + duration > fixed.startMinute) {
        candidate = fixed.endMinute + bufferMinutes;
      }
    }
    return Math.min(candidate, Math.max(0, dayEnd - duration));
  }

  function findOverlaps(activities, startMinute, endMinute, excludeActivityId = null) {
    return activities
      .filter((act) => act.id !== excludeActivityId && startMinute < act.endMinute && endMinute > act.startMinute)
      .sort((a, b) => a.startMinute - b.startMinute);
  }

  function findNextAvailableSlot(activities, durationMinutes, earliestStart, dayEnd = 1440, bufferMinutes = 5, excludeActivityId = null) {
    const duration = Math.max(5, Math.min(720, durationMinutes));
    const occupied = activities
      .filter((act) => act.id !== excludeActivityId)
      .sort((a, b) => a.startMinute - b.startMinute);

    let candidate = Math.max(0, Math.min(1435, earliestStart));
    for (const block of occupied) {
      if (candidate + duration <= block.startMinute) {
        return { start: candidate, end: candidate + duration };
      }
      if (candidate < block.endMinute) {
        candidate = block.endMinute + bufferMinutes;
      }
    }
    const maxEnd = Math.min(1440, dayEnd);
    if (candidate + duration <= maxEnd) {
      return { start: candidate, end: candidate + duration };
    }
    return null;
  }

  function delayAndCascade(dayActivities, targetActivityId, delayMinutes, dayEnd = 1440, bufferMinutes = 5) {
    const sorted = sortDayActivities(dayActivities);
    const target = sorted.find((a) => a.id === targetActivityId);
    if (!target) return [];

    const duration = durationOf(target);
    const newTargetStart = Math.min(1440 - duration, target.startMinute + delayMinutes);
    const fixedBlocks = sorted.filter((a) => !a.flexible && a.id !== targetActivityId);

    const adjustedStart = fitAfterFixed(newTargetStart, duration, fixedBlocks, dayEnd, bufferMinutes);
    const adjustedEnd = Math.min(1440, adjustedStart + duration);
    const changed = [{
      ...target,
      startMinute: adjustedStart,
      endMinute: adjustedEnd,
      status: "DELAYED"
    }];

    let cursorEnd = adjustedEnd;
    const subsequentFlexible = sorted.filter(
      (a) => a.id !== targetActivityId && a.flexible && !a.completed && a.startMinute >= target.startMinute
    );

    for (const item of subsequentFlexible) {
      const itemDur = durationOf(item);
      if (item.startMinute < cursorEnd + bufferMinutes) {
        const nextStart = fitAfterFixed(cursorEnd + bufferMinutes, itemDur, fixedBlocks, dayEnd, bufferMinutes);
        const nextEnd = Math.min(1440, nextStart + itemDur);
        if (nextStart < 1440 && nextEnd > nextStart) {
          changed.push({
            ...item,
            startMinute: nextStart,
            endMinute: nextEnd,
            status: "RESCHEDULED"
          });
          cursorEnd = nextEnd;
        }
      } else {
        cursorEnd = Math.max(cursorEnd, item.endMinute);
      }
    }

    return changed;
  }

  function reorganizeRemainingDay(dayActivities, startFromMinute, dayEnd = 1440, bufferMinutes = 10) {
    const anchors = dayActivities
      .filter((a) => a.completed || !a.flexible)
      .sort((a, b) => a.startMinute - b.startMinute);
    const flexiblePending = dayActivities
      .filter((a) => !a.completed && a.flexible)
      .sort((a, b) => (a.sortOrder - b.sortOrder) || (a.startMinute - b.startMinute));

    if (flexiblePending.length === 0) {
      return { updatedActivities: [], movedCount: 0, unscheduledOverflowCount: 0 };
    }

    const updated = [];
    let cursor = Math.max(0, Math.min(1435, startFromMinute));
    let overflow = 0;

    flexiblePending.forEach((item, index) => {
      const dur = durationOf(item);
      const fittedStart = fitAfterFixed(cursor, dur, anchors, dayEnd, bufferMinutes);
      const fittedEnd = fittedStart + dur;
      if (fittedEnd <= Math.min(1440, dayEnd)) {
        const moved = item.startMinute !== fittedStart || item.endMinute !== fittedEnd || item.sortOrder !== index;
        if (moved) {
          updated.push({
            ...item,
            startMinute: fittedStart,
            endMinute: fittedEnd,
            sortOrder: index,
            status: item.startMinute !== fittedStart ? "RESCHEDULED" : item.status
          });
        }
        cursor = fittedEnd + bufferMinutes;
      } else {
        overflow++;
      }
    });

    return {
      updatedActivities: updated,
      movedCount: updated.length,
      unscheduledOverflowCount: overflow
    };
  }

  function reorderAndReflowSlots(orderedActivities, bufferMinutes = 5) {
    if (!orderedActivities.length) return [];
    const fixedBlocks = orderedActivities
      .filter((a) => !a.flexible || a.completed)
      .sort((a, b) => a.startMinute - b.startMinute);
    const flexItems = orderedActivities.filter((a) => a.flexible && !a.completed);
    if (!flexItems.length) {
      return orderedActivities.map((act, idx) => ({ ...act, sortOrder: idx }));
    }

    let cursor = Math.min(...flexItems.map((a) => a.startMinute));
    const result = [];

    orderedActivities.forEach((act, idx) => {
      if (!act.flexible || act.completed) {
        if (act.sortOrder !== idx) {
          result.push({ ...act, sortOrder: idx });
        }
      } else {
        const dur = durationOf(act);
        const newStart = fitAfterFixed(cursor, dur, fixedBlocks, 1440, bufferMinutes);
        const newEnd = Math.min(1440, newStart + dur);
        cursor = newEnd + bufferMinutes;
        if (act.sortOrder !== idx || act.startMinute !== newStart || act.endMinute !== newEnd) {
          result.push({
            ...act,
            sortOrder: idx,
            startMinute: newStart,
            endMinute: newEnd,
            status: act.startMinute !== newStart ? "RESCHEDULED" : act.status
          });
        }
      }
    });

    return result;
  }

  function computeHabitStats(habit, todayKey) {
    const DT = global.RoutineApp.DateTime;
    const completedSet = new Set(habit.completedDates || []);

    let currentStreak = 0;
    let checkDate = completedSet.has(todayKey) ? todayKey : DT.addDays(todayKey, -1);
    while (completedSet.has(checkDate)) {
      currentStreak++;
      checkDate = DT.addDays(checkDate, -1);
    }

    let bestStreak = 0;
    const sortedDates = [...completedSet].sort();
    let run = 0;
    let prevDate = null;
    for (const date of sortedDates) {
      if (prevDate && DT.addDays(prevDate, 1) === date) {
        run++;
      } else {
        run = 1;
      }
      if (run > bestStreak) bestStreak = run;
      prevDate = date;
    }

    const weekKeys = DT.weekDateKeys(todayKey);
    const weeklyCompleted = weekKeys.filter((k) => completedSet.has(k)).length;

    const todayObj = DT.parseDateKey(todayKey);
    const monthKeys = DT.monthDateKeys(todayObj.getFullYear(), todayObj.getMonth());
    const monthlyCompleted = monthKeys.filter((k) => completedSet.has(k)).length;
    const daysElapsed = Math.max(1, DT.dayNumber(todayKey));

    return {
      currentStreak,
      bestStreak,
      weeklyCompleted,
      weeklyTarget: Math.max(1, Math.min(7, Number(habit.targetDaysPerWeek) || 7)),
      monthlyCompleted,
      monthlyTarget: daysElapsed,
      completedToday: completedSet.has(todayKey)
    };
  }

  global.RoutineApp = global.RoutineApp || {};
  global.RoutineApp.ScheduleEngine = {
    sortDayActivities,
    findOverlaps,
    findNextAvailableSlot,
    delayAndCascade,
    reorganizeRemainingDay,
    reorderAndReflowSlots,
    computeHabitStats
  };
})(window);
