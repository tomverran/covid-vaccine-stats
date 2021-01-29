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

/**
 * Calculate the absolute and percentage differences to yesterday
 * along with the right word to describe them (more/fewer)
 */
export function calculateDifferences(statistics: DailyTotal[]): Differences {
  const today = statistics[0].today;
  const yesterday = statistics[1].today;
  const difference = today.firstDose - yesterday.firstDose

  return {
    value: Math.abs(difference),
    percent: Math.abs(Math.round(difference / yesterday.firstDose * 100)),
  }
}
