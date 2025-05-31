This app was turned off long ago.

I've put the statistics it collected in `statistics.json` and `regional.json` for posterity, but better data is no doubt available elsewhere.

## COVID Vaccine Stats

An app to provide hopefully cheering statistics regarding the progress of the UK's vaccine rollout. This app was mostly made on a Sunday afternoon so please judge its quality accordingly.

You can see the stats at [https://covid-vaccine-stats.uk/](https://covid-vaccine-stats.uk/) and also [on Twitter](https://twitter.com/stats_vaccine).

### Lambda

The data powering the app is obtained from https://api.coronavirus.data.gov.uk (for the daily data) and https://www.england.nhs.uk/statistics/statistical-work-areas/covid-19-vaccinations/ (for the weekly England only data) with a Scala lambda that polls the API and relays the data to S3 & Twitter.

### Frontend

The frontend is a React app, partially rendered at build time with a wonky webpack plugin of my own making.
