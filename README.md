## COVID Vaccine Stats

An app to provide hopefully cheering statistics regarding the progress of the UK's vaccine rollout.

This app was made on a Sunday afternoon so please judge its quality accordingly.

Until I get a working `.uk` domain you can see the stats [here](http://vaccine-statistics-20210117140726225700000002.s3-website-eu-west-1.amazonaws.com/).

### Lambda

The data powering the app is obtained from https://api.coronavirus.data.gov.uk with a Scala lambda that polls the API and relays the data to S3. 
The frontend could talk directly to the API but I have plans to post updates to twitter, hence the lambda.

### Frontend

The frontend is just plain JS + HTML, using Bootstrap because I was absolutely prioritising speed here.
