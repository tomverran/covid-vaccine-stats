## COVID Vaccine Stats

An app to provide hopefully cheering statistics regarding the progress of the UK's vaccine rollout.

This app was mostly made on a Sunday afternoon so please judge its quality accordingly.

You can see the stats at [https://covid-vaccine-stats.uk/](https://covid-vaccine-stats.uk/)

### Lambda

The data powering the app is obtained from https://api.coronavirus.data.gov.uk with a Scala lambda that polls the API and relays the data to S3. 
The frontend could talk directly to the API but I have plans to post updates to twitter, hence the lambda. Also at first I was parsing XLSX files. Don't ask.

### Frontend

The frontend is just plain JS + HTML, using Bootstrap because I was absolutely prioritising speed here.
