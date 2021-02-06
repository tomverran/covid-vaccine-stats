import { formatDistanceToNow } from "date-fns";
import * as React from "react";
import { Map } from './map'

type State = {
  data: RegionalData[],
  percentages: RegionPercentage
  hoverRegion?: string
}

type ByAge = {
  type: string,
  "16-69"?: number,
  "16-79"?: number,
  "70-74"?: number,
  "75-79"?: number,
  "80+": number
}

type RegionInfo = {
  name: string,
  population: ByAge,
  firstDose:  ByAge,
  secondDose: ByAge
}

type RegionalData = {
  date: string,
  statistics: { [name: string]: RegionInfo }
}

type RegionPercentage = {
  [id: string]: number
}

function total(ba: ByAge): number {
  return (ba["16-69"] || 0) +
         (ba["16-79"] || 0) +
         (ba["70-74"] || 0) +
         (ba["75-79"] || 0) +
         (ba["80+"])
}

function calculatePercentages(rd: RegionalData): RegionPercentage {
  return Object.entries(rd.statistics).reduce((obj, [id, region]) => {
    const percent = total(region.firstDose) / total(region.population) * 100
    return { ...obj, [id]: percent }
  }, {})
}

const TableRow: React.FunctionComponent<{ value: string | number | undefined }> =
  (props) => {
    if (!props.value) {
      return <React.Fragment></React.Fragment>
    } else {
      return <tr>
        <th>{props.children}</th>
        <td className="text-end">{props.value.toLocaleString("en-gb")}</td>
      </tr>
    }
  }

export class Regions extends React.Component<{}, State> {

  constructor(props: {}) {
    super(props)
    this.state = { 
      data: [],
      percentages: {}
    }
  }

  async componentDidMount() {
    const resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/regional_v2.json");
    const data: RegionalData[] = await resp.json() as RegionalData[];
    const percentages = data[0] ? calculatePercentages(data[0]) : {}
    this.setState({ data, percentages })
  }

  opacity(id: string): number {
    const max = Object.values(this.state.percentages).reduce((a, b) => Math.max(a, b), 0);
    const min = Object.values(this.state.percentages).reduce((a, b) => Math.min(a, b), 100);
    return ((this.state.percentages[id] || max) - min) / (max - min)
  }

  updated() {
    if (!this.state.data[0]) return '';
    return formatDistanceToNow(new Date(this.state.data[0].date))
  }

  table(): React.ReactElement {
    const region = this.state.hoverRegion ? this.state.data[0]?.statistics[this.state.hoverRegion] : null
    const percent = this.state.hoverRegion ? this.state.percentages[this.state.hoverRegion] : 0
    if (region) {
      return <div className="text-center">
        <div className="mt-4 d-inline-block mx-auto">
          <table className="table table-bordered w-100">
            <thead>
              <tr><th colSpan={3}>{region.name}</th></tr>
            </thead>
            <tbody>
              <TableRow value={total(region.population)}>Approx. adult population</TableRow>
              <TableRow value={region.firstDose["16-69"]}>First doses: Under 70s</TableRow>
              <TableRow value={region.firstDose["16-79"]}>First doses: Under 80s</TableRow>
              <TableRow value={region.firstDose["70-74"]}>First doses: Aged 70-74</TableRow>
              <TableRow value={region.firstDose["75-79"]}>First doses: Aged 75-79</TableRow>
              <TableRow value={region.firstDose["80+"]}>First doses: Aged 80+</TableRow>
              <TableRow value={percent.toFixed(2) + "%"}>Percent with one dose</TableRow>
            </tbody>
          </table>
        </div>
      </div>
    } else {
      return <React.Fragment />
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
