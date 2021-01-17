
/**
 * Set the banner at the top of the screen
 * to flag whether the numbers are valid for today or not
 */
function setUpToDateBanner(statistics) {
  let lastUpdate = new Date(statistics[0].date)
  let upToDate = new Date().getDate() == lastUpdate.getDate()
  let upToDateBanner = document.querySelector("#up-to-date")
  upToDateBanner.classList.add(upToDate ? "alert-success" : "alert-warning")
  upToDateBanner.innerText = upToDate ? "Statistics up-to-date" : `Statistics not updated yet today. They're usually published in the evening.`
}

/**
 * Fill in the headings with the daily total
 * and the percent / absolute change since the day before 
 */
function populateHeadings(statistics) {
  let today = statistics[0].today;
  let yesterday = statistics[1].today;
  let difference = today.firstDose - yesterday.firstDose
  let percentChange = Math.round(difference / yesterday.firstDose * 100)
  let isUp = percentChange > 0

  document.querySelector(".date-today").innerText = dateFns.format(new Date(statistics[0].date), 'Do MMM');
  document.querySelector(".difference-value").innerText = difference.toLocaleString("en-GB");
  document.querySelector(".today").innerText = today.firstDose.toLocaleString("en-GB")
  document.querySelector(".difference-percent").innerText = Math.abs(percentChange);
  document.querySelector(".difference-updown").innerText = isUp ? "Up" : "Down";
}

/**
 * This is the ideal JavaScript application
 * you might not like it but this is what peak performance looks like
 */
(
  async function() {

    let resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/statistics.json");
    let statistics = await resp.json();

    setUpToDateBanner(statistics);
    populateHeadings(statistics);
  }
)()