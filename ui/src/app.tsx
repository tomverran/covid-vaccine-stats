import * as React from "react";
import { CardProps, Cards } from "./cards";
import { DoseChart } from "./chart";
import { DayProjection } from "./projection";
import { Regions } from "./regions";

export class App extends React.Component<{}, CardProps> {

  constructor() {
    super({});
    this.state = { statistics: [] }
  }

  async componentDidMount() {
    let resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/statistics.json");
    this.setState({ statistics: await resp.json() });
  }

  render() {
    return <div className="container">
      <h1 className="mb-1 mt-1 mx-auto px-2 py-2">UK COVID-19 Vaccine Tracker</h1>
      <p className="px-2 mb-3 text-muted">
        Data obtained from the <a href="https://coronavirus.data.gov.uk/">GOV.UK API</a>.
        This site is in no way affiliated with the NHS or UK Government!
        For daily updates follow us on <a href="https://twitter.com/stats_vaccine">Twitter</a>.
      </p>
      <Cards statistics={this.state.statistics}></Cards>
      <DayProjection statistics={this.state.statistics} />
      <DoseChart statistics={this.state.statistics} type="today"/>
      <DoseChart statistics={this.state.statistics} type="total"/>
      <Regions></Regions>
      <p className="text-center text-muted mt-2 mb-2">
        An open source project by <a href="https://tomverran.uk" rel="nofollow">Tom Verran</a>.<br />
        Source code on <a href="https://github.com/tomverran/covid-vaccine-stats">GitHub</a>.
      </p>
    </div>
  }
}



