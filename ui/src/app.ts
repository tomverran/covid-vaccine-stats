import { format, addDays } from 'date-fns';
import { template } from './dom';
import { Chart } from 'chart.js';

function formatDateStr(str: Date): string {
  return format(new Date(str), 'do MMM');
}

function formatLongDateStr(str: Date): string {
  return format(new Date(str), "do  MMMM");
}

type DoseTotal = {
  firstDose: number,
  secondDose: number
}

type DailyTotal = {
  date: string,
  today: DoseTotal,
  total: DoseTotal
}

type Differences = {
  moreOrFewer: string,
  upOrDown: string,
  percent: number,
  value: number
}

type Projection = {
  firstFour: string,
  allSix: string
}

type Statistics = {
  latestDate: string,
  firstDoses: number,
  differences: Differences,
  projection: Projection,
  totals: Totals
}

type Totals = {
  cumulative: number,
  percent: number,
  ratio: number,
}

/**
 * Calculate the absolute and percentage differences to yesterday
 * along with the right word to describe them (more/fewer)
 */
function calculateDifferences(statistics: DailyTotal[]): Differences {
  const today = statistics[0].today;
  const yesterday = statistics[1].today;
  const difference = today.firstDose - yesterday.firstDose

  return {
    value: Math.abs(difference),
    percent: Math.abs(Math.round(difference / yesterday.firstDose * 100)),
    moreOrFewer: difference > 0 ? "more" : "fewer",
    upOrDown: difference > 0 ? "up" : "down"
  }
}

/**
 * Project when all vulnerable groups will have received a dose
 * by extrapolating from the last day's data
 */
function projectCompletion(statistics: DailyTotal[]): Projection {
  const firstFour = 14600000
  const allSix = 31800000

  const latest = statistics[0];
  const firstFourDays = Math.ceil((firstFour - latest.total.firstDose) / latest.today.firstDose)
  const allSixDays = Math.ceil((allSix - latest.total.firstDose) / latest.today.firstDose)

  return {
    firstFour: formatLongDateStr(addDays(new Date(), firstFourDays)),
    allSix: formatLongDateStr(addDays(new Date(), allSixDays)),
  }
}

/**
 * I figure if I split out the ugly substitutions into functions
 * no-one will notice how horrendous this all is
 */
function calculateTotals(statistics: DailyTotal[]): Totals {
  const ukPopulation = 68000000 // the UK population is famously constant
  const percentVaccinated = (statistics[0].total.firstDose / ukPopulation) * 100

  return {
    cumulative: statistics[0].total.firstDose,
    ratio: Math.round(1 / (percentVaccinated / 100)),
    percent: Math.round(percentVaccinated)
  }
}

/**
 * Fill in the headings with the daily total
 * and the percent / absolute change since the day before
 */
function calculateStatistics(statistics: DailyTotal[]): Statistics {
  return {
    firstDoses: statistics[0].today.firstDose,
    latestDate: formatDateStr(new Date(statistics[0].date)),
    differences: calculateDifferences(statistics),
    projection: projectCompletion(statistics),
    totals: calculateTotals(statistics)
  }
}

/**
 * Abominable abstraction across drawing both graphs
 * accessor is a fn from DailyTotal => DoseTotal 
 */
function graph(statistics: DailyTotal[], selector: string, accessor: (d: DailyTotal) => DoseTotal) {

  const data = [...statistics].reverse().slice(1)
  const ctx = (document.querySelector(selector) as HTMLCanvasElement).getContext('2d')

  new Chart(ctx as CanvasRenderingContext2D, {
    type: 'line',
    data: {
      labels: data.map(d => formatDateStr(new Date(d.date))),
      datasets: [
        { 
          label: 'First Dose', 
          data: data.map(d => accessor(d).firstDose ), 
          backgroundColor: 'transparent',
          borderColor: '#17a2b8'
        },
        { 
          label: 'Second Dose', 
          data: data.map(d => accessor(d).secondDose ), 
          backgroundColor: 'transparent',
          borderColor: '#28a745'
        }
      ]
    }
  });  
}

/**
 * This is the ideal JavaScript application
 * you might not like it but this is what peak performance looks like
 */
(
  async function() {

    let resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/statistics.json");
    let statistics = await resp.json();

    graph(statistics, '#chart-per-day', d => d.today);
    graph(statistics, '#chart-total', d => d.total);

    template(calculateStatistics(statistics));
    document.querySelectorAll('.loading').forEach(e => e.classList.remove('loading'));
  }
)()
