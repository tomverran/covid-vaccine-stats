import * as React from "react";
import { CardProps, Cards } from "./cards";

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
      <h1 className="mb-2 mx-auto px-2 py-2">UK COVID-19 Vaccine Statistics</h1>
      <p className="px-2 mb-3 text-muted">
        Data obtained from the <a href="https://coronavirus.data.gov.uk/">GOV.UK API</a>.
        This site is in no way affiliated with the NHS or UK Government!
        For daily updates follow us on <a href="https://twitter.com/stats_vaccine">Twitter</a>.
      </p>
      <Cards statistics={this.state.statistics}></Cards>
    </div>
  }
}



