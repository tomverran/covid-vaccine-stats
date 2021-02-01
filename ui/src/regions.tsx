import { formatDistanceToNow } from "date-fns";
import * as React from "react";
import { Map } from './map'

type State = {
  data: RegionalData[]
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
    [name: string]: RegionInfo }
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
    this.state = { data: [] }
  }

  async componentDidMount() {
    let data = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/regional.json");
    this.setState({ data: await data.json() as RegionalData[]});
  }

  opacity(id: string): number {
    if (!this.state || !this.state.data[0]) return 0;
    const percentages = calculatePercentages(this.state.data[0]);
    const max = percentages.reduce((a, b) => Math.max(a, b.percent), 0);
    return percentages.find(e => id == e.id)?.percent || 1
  }

  updated() {
    if (!this.state.data[0]) return '';
    return formatDistanceToNow(new Date(this.state.data[0].date))
  }

  render() {
    return <div className="bg-white border shadow-sm p-4">
      <div className="alert alert-info text-center">These data are England only and updated weekly. Last updated {this.updated()} ago.</div>
      <h5 className="mb-4 text-center">Percentage vaccinated by NHS Region</h5>
      <Map opacity={this.opacity} hover={(region) => console.log(region)}></Map>
      <p className="text-muted mt-4 mb-0">
        The NHS splits its statistics by "Integrated Care Systems" which are essentially administrative regions.
        They replaced Primary Care Trusts in 2013.
      </p>
    </div>
  }
}
