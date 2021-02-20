import { addDays } from 'date-fns'


export type DoseTotal = {
  firstDose: number,
  secondDose: number
}

export type DailyTotal = {
  date: string,
  today: DoseTotal,
  total: DoseTotal
}

export type Differences = {
  percent: number,
  value: number
}

export function doseName(dose: keyof DoseTotal): string {
  switch (dose) {
    case 'firstDose': return 'First'
    case 'secondDose': return 'Second'
  }
}

export type Projection = {
  firstSix: Date
}

/**
 * Calculate the absolute and percentage differences to yesterday
 * along with the right word to describe them (more/fewer)
 */
export function dailyDifference(statistics: DailyTotal[], dose: keyof DoseTotal): Differences {
  const yesterday = statistics[1] ? statistics[1].today[dose] : 0
  const today = statistics[0] ? statistics[0].today[dose] : 0
  const difference = today - yesterday;

  return {
    value: difference,
    percent: Math.abs(Math.round(difference / yesterday * 100)),
  } 
}

/**
 * Project when vaccinations will finish by extrapolating today's data
 */
export function project(statistics: DailyTotal[]): Projection {
  const allSix = 31800000

  const latest = statistics[0];
  const firstSixDays = Math.ceil((allSix - latest.total.firstDose) / latest.today.firstDose);

  return {
    firstSix: addDays(new Date(), firstSixDays),
  }
}
