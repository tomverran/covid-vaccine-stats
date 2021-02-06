import { formatDistanceToNow } from "date-fns";
import * as React from "react";
import { Map } from './map'

type State = {
  data: RegionalData[],
  percentages: RegionPercentage[]
  hoverRegion?: string
}

type  DosesByAge = {
  percentOver80: number,
  under80: number,
  over80: number
}

type RegionInfo = {
  name: string,
  population: number,
  firstDose: DosesByAge,
  secondDose: DosesByAge
}

type RegionalData = {
  date: string,
  statistics: {
    [name: string]: RegionInfo
  }
}

type RegionPercentage = {
  id: string,
  name: string,
  percent: number
}

function calculatePercentages(rd: RegionalData): RegionPercentage[] {
  return Object.entries(rd.statistics).map(([id, region]) => {
    const vaccinated = region.firstDose.under80 + region.firstDose.over80
    return { id, name: region.name, percent: vaccinated / region.population * 100 }
  })
}

export class Regions extends React.Component<{}, State> {

  constructor(props: {}) {
    super(props)
    this.state = { 
      data: [],
      percentages: []
    }
  }

  async componentDidMount() {
    const resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/regional.json");
    const data: RegionalData[] = await resp.json() as RegionalData[];
    const percentages = data[1] ? calculatePercentages(data[1]) : []
    this.setState({ data, percentages })
  }

  opacity(id: string): number {
    if (!this.state || !this.state.data[1]) return 0;
    const max = this.state.percentages.reduce((a, b) => Math.max(a, b.percent), 0);
    const min = this.state.percentages.reduce((a, b) => Math.min(a, b.percent), 100);
    return ((this.state.percentages.find(e => id == e.id)?.percent || max) - min) / (max - min)
  }

  updated() {
    if (!this.state.data[0]) return '';
    return formatDistanceToNow(new Date(this.state.data[1].date))
  }

  table(): React.ReactElement {
    const region = this.state.hoverRegion ? this.state.data[1]?.statistics[this.state.hoverRegion] : null
    if (region) {
      return <div className="text-center">
        <div className="mt-4 d-inline-block mx-auto">
          <table className="table table-bordered w-100">
            <thead>
              <tr><th colSpan={3}>{region.name}</th></tr>
            </thead>
            <tbody>
              <tr>
                <th>Approx. adult population</th>
                <td className="text-end">{region.population.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>First doses: Over 80s</th>
                <td className="text-end">{region.firstDose.over80.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>First doses: Under 80s</th>
                <td className="text-end">{region.firstDose.under80.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Percent with one dose</th>
                <td className="text-end">{this.state.percentages.find(p => p.id == this.state.hoverRegion)?.percent?.toFixed(2)}%</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    } else {
      return <React.Fragment/>
    }
  }

  render() {
    return <div className="bg-white border shadow-sm p-4">
      <div className="alert alert-info text-center">Available for England only and updated weekly. Last updated {this.updated()} ago.</div>
      <h5 className="mb-0 text-center" id="regional">Percent of adults with one dose by Region</h5>
      <p className="text-muted text-center mb-4">Hover over / tap the regions to see the statistics.</p>
      <Map opacity={this.opacity.bind(this)} hover={(hoverRegion) => this.setState({...this.state, hoverRegion})}></Map>
      {this.table.call(this)}

      <p className="text-muted mt-4 mb-0 small">
        <strong>About the data: </strong> 
        The NHS in England is divided into either <em><a href="https://www.england.nhs.uk/integratedcare/integrated-care-systems/">Integrated Care Systems</a></em> or 
        <em><a href="https://www.england.nhs.uk/integratedcare/stps/"> Sustainability and Transformation Partnerships</a>. </em>
        The data are sourced from the NHS England <a href="https://www.england.nhs.uk/statistics/statistical-work-areas/covid-19-vaccinations/">statistics website </a>
        and are automatically updated on Thursdays. The percentages in this data are the percentages of the <em>adult</em> population vaccinated so are not directly comparable
        with the whole population percentages at the top of this page. For details of how populations are approximated see the documentation on the NHS website.
      </p>
    </div>
  }
}
