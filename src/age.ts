const DAY = 86_400_000;

function parseDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  return date.getUTCFullYear() === Number(match[1]) &&
    date.getUTCMonth() === Number(match[2]) - 1 &&
    date.getUTCDate() === Number(match[3]) ? date : null;
}

function addMonthsClamped(date: Date, months: number): Date {
  const first = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + months, 1));
  const lastDay = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  return new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth(), Math.min(date.getUTCDate(), lastDay)));
}

/** Returns the child's calendar age on a local date (YYYY-MM-DD), without DST/time-zone drift. */
export function ageAtDate(birthDate: string, onDate: string): string | null {
  const birth = parseDate(birthDate);
  const on = parseDate(onDate);
  if (!birth || !on || on.getTime() < birth.getTime()) return null;

  let totalMonths = (on.getUTCFullYear() - birth.getUTCFullYear()) * 12 +
    on.getUTCMonth() - birth.getUTCMonth();
  let anchor = addMonthsClamped(birth, totalMonths);
  if (anchor.getTime() > on.getTime()) {
    totalMonths -= 1;
    anchor = addMonthsClamped(birth, totalMonths);
  }

  const days = Math.floor((on.getTime() - anchor.getTime()) / DAY);
  if (totalMonths === 0) return days === 0 ? 'Newborn' : days < 30 ? `Newborn · ${days} ${days === 1 ? 'day' : 'days'}` : `${days} days`;

  const years = Math.floor(totalMonths / 12);
  const months = totalMonths % 12;
  const parts: string[] = [];
  if (years) parts.push(`${years} ${years === 1 ? 'year' : 'years'}`);
  if (months) parts.push(`${months} ${months === 1 ? 'month' : 'months'}`);
  if (days) parts.push(`${days} ${days === 1 ? 'day' : 'days'}`);
  return parts.join(', ');
}

export function ageForEntry(birthDate: string, entryDate: string): string | null {
  return ageAtDate(birthDate, entryDate);
}
