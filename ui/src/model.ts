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
  className: string,
  percent: number,
  symbol: string,
  value: number
}

export function doseName(dose: keyof DoseTotal): string {
  switch (dose) {
    case 'firstDose': return 'First'
    case 'secondDose': return 'Second'
  }
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
    value: Math.abs(difference),
    symbol: difference > 0 ? '▲' : '▼',
    className: difference > 0 ? 'text-success' : 'text-danger',
    percent: Math.abs(Math.round(difference / yesterday * 100)),
  } 
}