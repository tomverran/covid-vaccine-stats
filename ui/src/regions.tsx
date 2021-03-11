import { formatDistanceToNow } from "date-fns";
import * as React from "react";
import { Map } from './map'

type State = {
  mapMode: MapMode
  hoverRegion?: string,
  mapValues?: WeeklyRegionData
}

enum AgeRange {
  Aged16To59 = "16-59",
  Aged16To64 = "16-64",
  Aged16To69 = "16-69",
  Aged16To79 = "16-79",
  Aged60To64 = "60-64",
  Aged64To69 = "64-69",
  Aged70To74 = "70-74",
  Aged75To79 = "75-79",
  Aged80Plus = "80+",
}

type ByAge = { type: string } & {
  [key in AgeRange]?: number
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
  population: ByAge,
  firstDose: ByAge,
  secondDose: ByAge
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
  secondDosesLastWeek: number,
  changeInDoses: number
}

type WeeklyRegionData = {
  lastUpdated: Date,
  firstDoses: MinMax,
  secondDoses: MinMax,
  overallDoses: MinMax,
  dosesLastWeek: MinMax,
  changeInDoses: MinMax
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
  { type: "ChangeInDoses" } |
  { type: AgeRange }


/**
 * Total up data that is broken down into age groups
 * to end up with a single value
 */
function total(ba: ByAge): number {
  return Object.values(ba).reduce<number>((a, b) => a + (typeof b == "number" ? b : 0), 0)
}

/**
 * Given data bucketed by age
 * return the buckets available as keys
 */
function ageRanges(ba: ByAge): AgeRange[] {
  return Object.keys(ba).filter(t => t != "type") as AgeRange[]
}

/**
 * Given two sets of statistics bucketed by age
 * return the age ranges common to both
 */
function commonKeys(ba1: ByAge, ba2: ByAge): AgeRange[] {
  return Array.from(new Set([...ageRanges(ba1), ...ageRanges(ba2)]));
}

/**
 * Given two sets of data broken down by age, find the common age groups
 * then return new grouped data by applying the given function to each 
 * common age group from both groups in turn
 */
function mapByAge(ba1: ByAge, ba2: ByAge, f: (a: number, b: number) => number): ByAge {
  return commonKeys(ba1, ba2).reduce((k, v) => ({ ...k, [v]: f(ba1[v] || 0, ba2[v] || 0) }), { type: "intersection" })
}

/**
 * Show a row in the statistics table containing the region data
 */
const TableRow: React.FunctionComponent<{ group: AgeRange, region: RegionData }> =
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
const MapModeSelect: React.FunctionComponent<{ ages: AgeRange[], mode: MapMode, set: (mode: MapMode) => void }> =
  ({ ages, mode, set: setValue }) => {

    const set: (s: string) => MapMode =
      s => {
        if ((ages as Array<string>).includes(s)) {
          return { type: s as AgeRange }
        } else {
          switch (s) {
            case "DosesAllTime": return { type: "DosesAllTime" }
            case "ChangeInDoses": return { type: "ChangeInDoses" }
            case "DosesLastWeek": return { type: "DosesLastWeek" }
            case "OverallPercent": return { type: "OverallPercent" }
            default: return { type: "DosesAllTime" }
          }
        }
      }

    return <div className="text-center mt-2 mt-md-0">
      <div className="form-group text-start p-2 border d-inline-block">
        <label htmlFor="map-mode" className="text-muted small">Colour map according to</label>
        <select id="map-mode" onChange={e => setValue(set(e.target.value))} className="form-control form-control-sm">
          <optgroup label="Last week">
            <option selected={mode.type == "DosesLastWeek"} value="DosesLastWeek">
              Doses given
            </option>
            <option selected={mode.type == "ChangeInDoses"} value="ChangeInDoses">
              Percent change
            </option>
          </optgroup>
          <optgroup label="All time">
            <option selected={mode.type == "DosesAllTime"} value="DosesAllTime">
              Doses given
            </option>
            <option selected={mode.type == "OverallPercent"} value="OverallPercent">
              Percent first doses: All adults
            </option>
            {ages.map(age => {
              return <option selected={mode.type == age} value={age}>
                {`Percent first doses: ${age}`}
              </option>
            })}
          </optgroup>
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
      <p className="text-bold mt-4 mb-1 border-bottom">{region.name}</p>
      <p className="text-muted text-center small mb-2">Approximate population: {total(region.population).toLocaleString("en-gb")}</p>
      <div className="mt-2 text-start row g-0">
        <div className="col-md me-md-1">
          <table className="table table-bordered w-100 mb-2">
            <tbody>
              <tr>
                <th>First doses in week</th>
                <td className="text-end">{region.firstDosesLastWeek.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Total first doses</th>
                <td className="text-end">{total(region.firstDoses).toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Second doses in week</th>
                <td className="text-end">{region.secondDosesLastWeek.toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Total second doses</th>
                <td className="text-end">{total(region.secondDoses).toLocaleString("en-gb")}</td>
              </tr>
              <tr>
                <th>Change&nbsp;since&nbsp;last&nbsp;week</th>
                <td className={`text-end ${region.changeInDoses > 0 ? "text-success" : "text-danger"}`}>
                  {region.changeInDoses > 0 ? "▲" : "▼"}&nbsp;{Math.abs(region.changeInDoses).toFixed(2)}%
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div className="col-md ms-md-1">
          <table className="table table-bordered w-100">
            <thead>
              <tr>
                <th>Age</th>
                <th>First&nbsp;dose</th>
                <th>Second&nbsp;dose</th>
              </tr>
            </thead>
            <tbody>
              <TableRow region={region} group={AgeRange.Aged80Plus}>80+</TableRow>
              <TableRow region={region} group={AgeRange.Aged75To79}>75-79</TableRow>
              <TableRow region={region} group={AgeRange.Aged70To74}>70-74</TableRow>
              <TableRow region={region} group={AgeRange.Aged16To79}>16-79</TableRow>
              <TableRow region={region} group={AgeRange.Aged16To69}>16-69</TableRow>
              <TableRow region={region} group={AgeRange.Aged64To69}>64-69</TableRow>
              <TableRow region={region} group={AgeRange.Aged60To64}>60-64</TableRow>
              <TableRow region={region} group={AgeRange.Aged16To64}>16-64</TableRow>
              <TableRow region={region} group={AgeRange.Aged16To59}>16-59</TableRow>
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
function transformRegion(region: RawRegionData, previousWeeks: RawRegionData[]): RegionData {

  const lastWeek: RawRegionData | undefined = previousWeeks[0]
  const weekBeforelast: RawRegionData | undefined = previousWeeks[1]

  const firstDoses = total(region.firstDose)
  const secondDoses = total(region.secondDose)
  const secondDoseLastWeek = lastWeek ? total(lastWeek.secondDose) : 0
  const firstDoseLastWeek = lastWeek ? total(lastWeek.firstDose) : 0

  const dosesLastWeek = firstDoseLastWeek + secondDoseLastWeek
  const dosesWeekBeforeLast = weekBeforelast ? total(weekBeforelast.firstDose) + total(weekBeforelast.secondDose) : 0
  const changeInDoses = ((firstDoses + secondDoses) - dosesLastWeek) - (dosesLastWeek - dosesWeekBeforeLast)
  const percentChange = changeInDoses / dosesLastWeek * 100

  return {
    name: region.name,
    firstDoses: region.firstDose,
    secondDoses: region.secondDose,
    population: region.population,
    firstDosesLastWeek: firstDoses - firstDoseLastWeek,
    secondDosesLastWeek: secondDoses - secondDoseLastWeek,
    percentFirstDoses: mapByAge(region.firstDose, region.population, (a, b) => (a / Math.max(a, b)) * 100),
    percentSecondDoses: mapByAge(region.secondDose, region.population, (a, b) => (a / Math.max(a, b)) * 100),
    changeInDoses: percentChange
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
    case "ChangeInDoses": return td.changeInDoses.max
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
    case "ChangeInDoses": return td.changeInDoses.min
    default: return 0;
  }
}

/**
 * Given a mapmode and the weekly region data extract the value
 * we want to plot onto the map
 */
function currValue(td: RegionData, mm: MapMode): number {
  switch (mm.type) {
    case "ChangeInDoses": return td.changeInDoses
    case "DosesAllTime": return total(td.firstDoses) + total(td.secondDoses)
    case "DosesLastWeek": return td.firstDosesLastWeek + td.secondDosesLastWeek
    case "OverallPercent": return (total(td.firstDoses) / Math.max(total(td.population), total(td.firstDoses))) * 100
    default: return td.percentFirstDoses[mm.type as AgeRange] || 0
  }
}

/**
 * Given a list of weekly regional data, transform it into the data we need for the map,
 * we aggregate totals as we go along to avoid repeatedly iterating the data
 */
function transformWeeklyRegionData(region: WeeklyRawRegionData[]): WeeklyRegionData | undefined {

  if (!region[0]) return undefined
  const [thisWeek, ...others] = region

  return Object
    .entries(thisWeek.statistics)
    .reduce((prev, [id, region]) => {
      const thisRegion = transformRegion(region, [...others.map(a => a.statistics[id])])
      return {
        ...prev,
        firstDoses: add(prev.firstDoses, total(thisRegion.firstDoses)),
        secondDoses: add(prev.secondDoses, total(thisRegion.secondDoses)),
        overallDoses: add(prev.overallDoses, total(thisRegion.firstDoses) + total(thisRegion.secondDoses)),
        dosesLastWeek: add(prev.dosesLastWeek, thisRegion.firstDosesLastWeek + thisRegion.secondDosesLastWeek),
        changeInDoses: add(prev.changeInDoses, Math.abs(thisRegion.changeInDoses)),
        regions: { ...prev.regions, [id]: thisRegion }
      }
    }, {
      firstDoses: emptyMinMax,
      secondDoses: emptyMinMax,
      overallDoses: emptyMinMax,
      dosesLastWeek: emptyMinMax,
      changeInDoses: emptyMinMax,
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
    const resp = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/regional_v3.json");
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
    return (Math.abs(currValue(region, this.state.mapMode)) - min) / (max - min)
  }

  colour(id: string): string {
    if (!this.state.mapValues) {
      return ''
    } else {
      switch (this.state.mapMode.type) {
        case "ChangeInDoses": return this.state.mapValues.regions[id].changeInDoses > 0 ? "#198754" : "#dc3545"
        default: return '#17a2b8'
      }
    }
  }

  updated() {
    if (!this.state.mapValues) return '';
    return formatDistanceToNow(new Date(this.state.mapValues.lastUpdated))
  }

  table(): React.ReactElement {
    const region = this.state.hoverRegion ? this.state.mapValues?.regions[this.state.hoverRegion] : null
    return region ? <RegionTable {...region} /> : <React.Fragment />
  }

  availableAges(): AgeRange[] {
    const regions = this.state.mapValues?.regions
    const region = regions ? Object.values(regions)[0] : null
    return region ? ageRanges(region.firstDoses) : []
  }

  render() {
    return <div className="bg-white border shadow-sm p-4">
      <h5 className="mb-0 text-center" id="regional">Regional statistics for England</h5>
      <p className="text-muted text-center mt-2 mb-4">
        Hover over / tap the map to see more.<br />
        Last updated {this.updated()} ago.
      </p>
      <Map fill={this.colour.bind(this)} opacity={this.opacity.bind(this)} hover={(hoverRegion) => this.setState({ ...this.state, hoverRegion })}></Map>
      <MapModeSelect ages={this.availableAges()} mode={this.state.mapMode} set={this.setMapMode.bind(this)}></MapModeSelect>
      {this.table.call(this)}
      <p className="text-muted mt-4 mb-0 small">
        <strong>About the data: </strong>
        The NHS in England is divided into either <em><a href="https://www.england.nhs.uk/integratedcare/integrated-care-systems/">Integrated Care Systems</a></em> or
        <em><a href="https://www.england.nhs.uk/integratedcare/stps/"> Sustainability and Transformation Partnerships</a>. </em>
        The data are sourced from the NHS England <a href="https://www.england.nhs.uk/statistics/statistical-work-areas/covid-19-vaccinations/">statistics website </a>. 
        The percentages in these data are the percentages of the <em>adult</em> population vaccinated so are not directly comparable
        with the whole population percentages at the top of this page. For details of how populations are approximated see the documentation on the NHS website.
      </p>
    </div>
  }
}
