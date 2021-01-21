## COVID Vaccine Stats

An app to provide hopefully cheering statistics regarding the progress of the UK's vaccine rollout. This app was mostly made on a Sunday afternoon so please judge its quality accordingly.

You can see the stats at [https://covid-vaccine-stats.uk/](https://covid-vaccine-stats.uk/) and also [on Twitter](https://twitter.com/stats_vaccine).

### Lambda

The data powering the app is obtained from https://api.coronavirus.data.gov.uk with a Scala lambda that polls the API and relays the data to S3 & Twitter.

### Frontend

The frontend is just plain JS + HTML, using Bootstrap because I was absolutely prioritising speed here.
