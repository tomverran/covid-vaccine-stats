## COVID Vaccine Stats

An app to provide hopefully cheering statistics regarding the progress of the UK's vaccine rollout.

This app was made on a Sunday afternoon so please judge its quality accordingly.


### Lambda

The data powering the app is obtained from https://www.england.nhs.uk/statistics/statistical-work-areas/covid-19-vaccinations/
with a Scala lambda that pulls the cumulative total vaccinations out of the provided XLSX files and uploads them to S3.

### Frontend

The frontend is just plain JS + HTML, using Bootstrap because I was absolutely prioritising speed here.
