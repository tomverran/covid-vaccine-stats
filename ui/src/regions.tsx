import { formatDistanceToNow } from "date-fns";
import * as React from "react";
import { Map } from './map'

type State = {
  mapMode: MapMode
  hoverRegion?: string,
  mapValues?: WeeklyRegionData
}

type ByAge = {
  "16-69"?: number,
  "16-79"?: number,
  "70-74"?: number,
  "75-79"?: number,
  "80+": number
}

type WithType = {
  type: string
}

type MinMax = {
  min: number,
  max: number
}

const emptyMinMax: MinMax =
  { min: Number.MAX_SAFE_INTEGER, max: 0 }

function add(mm: MinMax, num: number): MinMax {
  return { min: Math.min(mm.min, num), max: Math.max(mm.max, num) }
}

/**
 * These two types are the data as it comes from the server
 * we then transform them into the other types below
 */
type RawRegionData = {
  name: string,
  population: ByAge & WithType,
  firstDose:  ByAge & WithType,
  secondDose: ByAge & WithType
}

type WeeklyRawRegionData = {
  date: string,
  statistics: {
    [name: string]: RawRegionData
  }
}

/**
 * Transformed data to make it easier
 * to display all the information on the map
 */
type RegionData = {
  name: string,
  population: ByAge,
  firstDoses: ByAge,
  secondDoses: ByAge,
  percentFirstDoses: ByAge,
  percentSecondDoses: ByAge,
  firstDosesLastWeek: number,
  secondDosesLastWeek: number
}

type WeeklyRegionData = {
  lastUpdated: Date,
  firstDoses: MinMax,
  secondDoses: MinMax,
  overallDoses: MinMax
  dosesLastWeek: MinMax
  regions: { [id: string]: RegionData }
}

/**
 * Viewing modes available for the map - 
 * influence what we use to decide which colour each region is
 */
type MapMode =
  { type: "DosesAllTime" } |
  { type: "DosesLastWeek" } |
  { type: "OverallPercent" } |
  { type: "ByAgePercent", age: keyof ByAge }

/**
 * Total up data that is broken down into age groups
 * to end up with a single value
 */
function total(ba: ByAge): number {
  return (ba["16-69"] || 0) +
         (ba["16-79"] || 0) +
         (ba["70-74"] || 0) +
         (ba["75-79"] || 0) +
         (ba["80+"])
}

/**
 * Given two sets of data broken down by age, find the common age groups
 * then return new grouped data by applying the given function to each 
 * common age group from both groups in turn
 */
function mapByAge(ba1: ByAge, ba2: ByAge, f: (a: number, b: number) => number): ByAge {
  const seed: ByAge = { "80+": f(ba1["80+"], ba2["80+"]) }
  const keys = Array.from(new Set([...Object.keys(ba1), ...Object.keys(ba2)])) as (keyof ByAge)[]
  return keys.reduce((ba, k) => ({ ...ba, [k]: f(ba1[k] || 0, ba2[k] || 0) }), seed)
}

/**
 * Show a row in the statistics table containing the region data
 */
const TableRow: React.FunctionComponent<{ group: keyof ByAge, region: RegionData }> =
  (props) => {
    if (!props.region.percentFirstDoses[props.group]) {
      return <React.Fragment></React.Fragment>
    } else {
      return <tr>
        <th>{props.children}</th>
        <td className="text-end">{(props.region.percentFirstDoses[props.group] || 0).toFixed(2)}%</td>
        <td className="text-end">{(props.region.percentSecondDoses[props.group] || 0).toFixed(2)}%</td>
      </tr>
    }
  }

/**
 * A dropdown box component that emits MapModes when set
 * This is pretty abominable I don't know if there's an easier way to do this
 */
const MapModeSelect: React.FunctionComponent<{ mode: MapMode, set: (mode: MapMode) => void }> =
  ({ mode, set: setValue }) => {

    const set: (s: string) => MapMode =
      s => {
        switch (s) {
          case "DosesAllTime": return { type: "DosesAllTime" }
          case "DosesLastWeek": return { type: "DosesLastWeek" }
          case "OverallPercent": return { type: "OverallPercent" }
          case "ByAge1669": return { type: "ByAgePercent", age: "16-69" }
          case "ByAge7074": return { type: "ByAgePercent", age: "70-74" }
          case "ByAge7579": return { type: "ByAgePercent", age: "75-79" }
          case "ByAge80plus": return { type: "ByAgePercent", age: "80+" }
          default: return { type: "DosesAllTime" }
        }
      }

    return <div className="text-center mt-2 mt-md-0">
      <div className="form-group text-start p-2 border d-inline-block">
        <label htmlFor="map-mode" className="text-muted small">Colour map according to</label>
        <select id="map-mode" onChange={e => setValue(set(e.target.value))} className="form-control form-control-sm">
          <option selected={mode.type == "DosesAllTime"} value="DosesAllTime">
            Doses given: All time
            </option>
          <option selected={mode.type == "DosesLastWeek"} value="DosesLastWeek">
            Doses given last week
            </option>
          <option selected={mode.type == "OverallPercent"} value="OverallPercent">
            Percent first doses: All adults
            </option>
          <option selected={mode.type == "ByAgePercent" && mode.age == "16-69"} value="ByAge1669">
            Percent first doses: under 70s
            </option>
          <option selected={mode.type == "ByAgePercent" && mode.age == "70-74"} value="ByAge7074">
            Percent first doses: 70-74
            </option>
          <option selected={mode.type == "ByAgePercent" && mode.age == "75-79"} value="ByAge7579">
            Percent first doses 75-79
            </option>
          <option selected={mode.type == "ByAgePercent" && mode.age == "80+"} value="ByAge80plus">
            Percent first doses 80+
          </option>
        </select>
      </div>
    </div>
  }

/**
 * React component to display information for a region
 * in a series of tables, optionally in columns
 */
const RegionTable: React.FunctionComponent<RegionData> =
  (region) => <div className="text-center">
    <div className="d-inline-block">
      <p className="text-bold mt-4 mb-2 border-bottom">{region.name}</p>
      <div className="mt-2 text-start row g-0">
        <div className="col-md me-md-1">
          <table className="table table-bordered w-100 mb-2">
            <tbody>
              <tr>
                <th>Population</th>
                <td className="text-end">{total(region.population).toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>First doses last week</th>
                <td className="text-end">{region.firstDosesLastWeek.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Second doses last week</th>
                <td className="text-end">{region.secondDosesLastWeek.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Total first doses</th>
                <td className="text-end">{total(region.firstDoses).toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Total second doses</th>
                <td className="text-end">{total(region.secondDoses).toLocaleString("en-gb")}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div className="col-md ms-md-1">
          <table className="table table-bordered w-100">
            <thead>
              <tr>
                <th></th>
                <th>First&nbsp;dose</th>
                <th>Second&nbsp;dose</th>
              </tr>
            </thead>
            <tbody>
              <TableRow region={region} group={"80+"}>Over&nbsp;80s</TableRow>
              <TableRow region={region} group={"75-79"}>Aged&nbsp;75-79</TableRow>
              <TableRow region={region} group={"70-74"}>Aged&nbsp;70-74</TableRow>
              <TableRow region={region} group={"16-79"}>Under&nbsp;80s</TableRow>
              <TableRow region={region} group={"16-69"}>Under&nbsp;70s</TableRow>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>


/**
 * Given raw regional data for this week and optionally for last week too
 * produce a nicely transformed RegionData that summarises all the info we need
 */
function transformRegion(region: RawRegionData, lastWeek?: RawRegionData): RegionData {

  const firstDoses = total(region.firstDose)
  const secondDoses = total(region.secondDose)
  const secondDoseLastWeek = lastWeek ? total(lastWeek.secondDose) : 0
  const firstDoseLastWeek = lastWeek ? total(lastWeek.firstDose) : 0

  return {
    name: region.name,
    firstDoses: region.firstDose,
    secondDoses: region.secondDose,
    population: region.population, 
    firstDosesLastWeek: firstDoses - firstDoseLastWeek,
    secondDosesLastWeek: secondDoses - secondDoseLastWeek,
    percentFirstDoses: mapByAge(region.firstDose, region.population, (a, b) => (a / b) * 100),
    percentSecondDoses: mapByAge(region.secondDose, region.population, (a, b) => (a / b) * 100)
  }
}

/**
 * Find the maximum value we could plot onto the map for the given map mode
 * for percentages we display everything relative to 100%, for other totals
 * we display relative to the maximum of that total
 */
function maxValue(td: WeeklyRegionData, mm: MapMode): number {
  switch (mm.type) {
    case "DosesAllTime": return td.overallDoses.max
    case "DosesLastWeek": return td.dosesLastWeek.max
    default: return 100;
  }
}

/**
 * Find the maximum value we could plot onto the map for the given map mode
 * Same as the above function
 */
function minValue(td: WeeklyRegionData, mm: MapMode): number {
  switch (mm.type) {
    case "DosesAllTime": return td.overallDoses.min
    case "DosesLastWeek": return td.dosesLastWeek.min
    default: return 0;
  }
}

/**
 * Given a mapmode and the weekly region data extract the value
 * we want to plot onto the map
 */
function currValue(td: RegionData, mm: MapMode): number {
  switch(mm.type) {
    case "ByAgePercent": return td.percentFirstDoses[mm.age] || 0
    case "DosesAllTime": return total(td.firstDoses) + total(td.secondDoses)
    case "DosesLastWeek": return td.firstDosesLastWeek + td.secondDosesLastWeek
    case "OverallPercent": return (total(td.firstDoses) / total(td.population)) * 100
  }
}

/**
 * Given a list of weekly regional data, transform it into the data we need for the map,
 * we aggregate totals as we go along to avoid repeatedly iterating the data
 */
function transformWeeklyRegionData(region: WeeklyRawRegionData[]): WeeklyRegionData | undefined {

  if (!region[0]) return undefined
  const thisWeek: WeeklyRawRegionData = region[0]
  const lastWeek: WeeklyRawRegionData | undefined = region[1]

  return Object
    .entries(thisWeek.statistics)
    .reduce((prev, [id, region]) => {
      const thisRegion = transformRegion(region, lastWeek?.statistics[id])
      return {
        ...prev,
        firstDoses: add(prev.firstDoses, total(thisRegion.firstDoses)),
        secondDoses: add(prev.secondDoses, total(thisRegion.secondDoses)),
        overallDoses: add(prev.overallDoses, total(thisRegion.firstDoses) + total(thisRegion.secondDoses)),
        dosesLastWeek: add(prev.dosesLastWeek, thisRegion.firstDosesLastWeek + thisRegion.secondDosesLastWeek),
        regions: { ...prev.regions, [id]: thisRegion }
      }
    }, {
      firstDoses: emptyMinMax,
      secondDoses: emptyMinMax,
      overallDoses: emptyMinMax,
      dosesLastWeek: emptyMinMax,
      lastUpdated: new Date(thisWeek.date),
      regions: {}
    })
}


export class Regions extends React.Component<{}, State> {

  constructor(props: {}) {
    super(props)
    this.state = { mapMode: { type: "DosesLastWeek" } }
  }

  async componentDidMount() {
    const resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/regional_v2.json");
    const regionalData: WeeklyRawRegionData[] = await resp.json() as WeeklyRawRegionData[];
    this.setState({ ...this.state, mapValues: transformWeeklyRegionData(regionalData) })
  }

  setMapMode(mode: MapMode) {
    this.setState({ ...this.state, mapMode: mode })
  }

  opacity(id: string): number {
    if (!this.state.mapValues) return 0;
    const region = this.state.mapValues.regions[id] || 0
    const min = minValue(this.state.mapValues, this.state.mapMode)
    const max = maxValue(this.state.mapValues, this.state.mapMode)
    return (currValue(region, this.state.mapMode) - min) / (max - min)
  }

  updated() {
    if (!this.state.mapValues) return '';
    return formatDistanceToNow(new Date(this.state.mapValues.lastUpdated))
  }

  table(): React.ReactElement {
    const region = this.state.hoverRegion ? this.state.mapValues?.regions[this.state.hoverRegion] : null
    return region ? <RegionTable {...region} /> : <React.Fragment />
  }

  render() {
    return <div className="bg-white border shadow-sm p-4">
      <h5 className="mb-0 text-center" id="regional">Regional statistics for England</h5>
      <p className="text-muted text-center mt-2 mb-4">
        Last updated {this.updated()} ago.<br />
        Hover over / tap the map to see more.
      </p>
      <Map opacity={this.opacity.bind(this)} hover={(hoverRegion) => this.setState({...this.state, hoverRegion})}></Map>
      <MapModeSelect mode={this.state.mapMode} set={this.setMapMode.bind(this)}></MapModeSelect>
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
