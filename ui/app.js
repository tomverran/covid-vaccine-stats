
/**
 * React JS version 0.1
 */
function setText(selector, text) {
  document.querySelectorAll(selector).forEach(e => e.innerText = text);
}

function addClass(selector, className) {
  document.querySelectorAll(selector).forEach(e => e.classList.add(className));
}

/**
 * Fill in the headings with the daily total
 * and the percent / absolute change since the day before
 */
function populateHeadings(statistics) {
  const today = statistics[0].today;
  const yesterday = statistics[1].today;
  const difference = today.firstDose - yesterday.firstDose
  const percentChange = Math.round(difference / yesterday.firstDose * 100)
  const isUp = percentChange > 0

  setText(".date-today", dateFns.format(new Date(statistics[0].date), 'Do MMM'));
  setText(".today", today.firstDose.toLocaleString("en-GB"));

  setText(".difference-value", Math.abs(difference).toLocaleString("en-GB"));
  setText(".difference-percent", Math.abs(percentChange) + "%");

  addClass(".difference-updown, .difference-percent", isUp ? "text-success" : "text-danger");
  setText(".difference-gtlt", isUp ? "more" : "fewer");
  setText(".difference-updown", isUp ? "Up" : "Down");
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
 * Graph the number of vaccines given per day
 */
function vaccinationsPerDay(statistics) {

  const data = new google.visualization.arrayToDataTable(
    [['Date', 'First Dose', 'Second Dose']].concat(
      [...statistics.reverse()].slice(1).map(day => {
        return [
          dateFns.format(new Date(day.date), 'Do MMM'),
          day.today.firstDose,
          day.today.secondDose
        ]
      })
    )
  )

  const chart = new google.charts.Line(document.querySelector('#chart-per-day'));
  chart.draw(data, google.charts.Line.convertOptions({ height: 400 }));
}

/**
 * Graph the cumulative number of vaccines over time
 */
function totalVaccinations(statistics) {

  const data = new google.visualization.arrayToDataTable(
    [['Date', 'First Dose', 'Second Dose']].concat(
      [...statistics].reverse().slice(1).map(day => {
        return [
          dateFns.format(new Date(day.date), 'Do MMM'),
          day.total.firstDose,
          day.total.secondDose,
        ]
      })
    )
  )

  const chart = new google.charts.Line(document.querySelector('#chart-total'));
  chart.draw(data, google.charts.Line.convertOptions({ height: 400 }));
}

/**
 * This is the ideal JavaScript application
 * you might not like it but this is what peak performance looks like
 */
(
  async function() {

    let resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/statistics.json");
    let statistics = await resp.json();

    google.charts.load('current', {'packages':['line']});
    google.charts.setOnLoadCallback(() => { totalVaccinations(statistics); vaccinationsPerDay(statistics)});
    populateHeadings(statistics);
    populateDetails(statistics);
  }
)()
