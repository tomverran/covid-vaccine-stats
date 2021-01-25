
/**
 * React JS version 0.1
 */
function setText(selector, text) {
  document.querySelectorAll(selector).forEach(e => e.innerText = text);
}

function addClass(selector, className) {
  document.querySelectorAll(selector).forEach(e => e.classList.add(className));
}

function formatDateStr(str) {
  return dateFns.format(new Date(str), 'Do MMM');
}

function formatLongDateStr(str) {
  return dateFns.format(new Date(str), 'Do of MMMM');
}

function totalDoses(day) {
  return day.firstDose + day.secondDose
}

/**
 * Fill in the headings with the daily total
 * and the percent / absolute change since the day before
 */
function populateHeadings(statistics) {
  const today = statistics[0].today;
  const yesterday = statistics[1].today;
  const difference = totalDoses(today) - totalDoses(yesterday)
  const percentChange = Math.round(difference / totalDoses(yesterday) * 100)
  const isUp = percentChange > 0

  setText(".date-today", formatDateStr(statistics[0].date));
  setText(".today", totalDoses(today).toLocaleString("en-GB"));

  setText(".difference-value", Math.abs(difference).toLocaleString("en-GB"));
  setText(".difference-percent", Math.abs(percentChange) + "%");

  addClass(".difference-updown, .difference-percent", isUp ? "text-success" : "text-danger");
  setText(".difference-gtlt", isUp ? "more" : "fewer");
  setText(".difference-updown", isUp ? "Up" : "Down");
}

/**
 * Project when all vulnerable groups will have received a dose
 * by extrapolating from the last day's data
 */
function projectCompletion(statistics) {
  const firstFour = 14600000
  const allSix = 31800000

  const latest = statistics[0];
  const firstFourDays = Math.ceil((firstFour - latest.total.firstDose) / latest.today.firstDose)
  const allSixDays = Math.ceil((allSix - latest.total.firstDose) / latest.today.firstDose)

  setText('.projected-first-four', formatLongDateStr(dateFns.addDays(new Date(), firstFourDays)))
  setText('.projected-all-six', formatLongDateStr(dateFns.addDays(new Date(), allSixDays)))
}

/**
 * I figure if I split out the ugly substitutions into functions
 * no-one will notice how horrendous this all is
 */
function populateDetails(statistics) {
  const ukPopulation = 68000000 // the UK population is famously constant
  const percentVaccinated = (statistics[0].total.firstDose / ukPopulation) * 100
  setText('.cumulative-total', statistics[0].total.firstDose.toLocaleString('en-GB'));
  setText('.population-percent', percentVaccinated.toFixed(2) + '%')
  setText('.one-in-x', Math.round(1 / (percentVaccinated / 100)));
}

/**
 * Abominable abstraction across drawing both graphs
 * accessor is a fn from DailyTotal => DoseTotal 
 */
function graph(statistics, selector, accessor) {

  const data = [...statistics].reverse().slice(1)
  const ctx = document.querySelector(selector).getContext('2d');

  new Chart(ctx, {
    type: 'line',
    data: {
      labels: data.map(d => formatDateStr(d.date)),
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

    populateDetails(statistics);
    populateHeadings(statistics);
    projectCompletion(statistics);
    document.querySelectorAll('.loading').forEach(e => e.classList.remove('loading'));
  }
)()
