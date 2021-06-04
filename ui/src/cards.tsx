import { dailyDifference, DailyTotal, doseName, DoseTotal } from "./model"
import { format } from 'date-fns'
import * as React from "react";

const Card: React.FunctionComponent<{loaded: boolean}> =
  (props) => {
    return <div className={`stat-card bg-card shadow-sm border px-3 py-2 flex-grow-1 ${props.loaded ? '' : 'loading'}`}>
      {props.children}
    </div>
  }

const LightText: React.FunctionComponent<{}> =
  props => <p className="text-muted mb-0 d-inline-block align-self-end">{props.children}</p>

export type CardProps = {
  statistics: DailyTotal[]
}

/**
 * Card to show the number of first doses today
 */
const DailyDoses: React.FunctionComponent<CardProps & { dose: keyof DoseTotal }> =
  props => {
    const difference = dailyDifference(props.statistics, props.dose)
    const prevDay = props.statistics[1] ? format(new Date(props.statistics[1].date), 'EEEE') : 'Mon'
    const today = props.statistics[0] ? format(new Date(props.statistics[0].date), 'EEEE do MMM') : 'Blah'
    return <Card loaded={props.statistics.length > 0}>
      <div className="mb-1 pb-2 border-bottom">
        <h1 className="mb-0"> {props.statistics[0]?.today[props.dose].toLocaleString("en-GB") || "0"}</h1>
        <LightText>{doseName(props.dose)} doses given on {today}</LightText>
      </div>
      <div className="pt-1">
        <h4 className={`${difference.value > 0 ? "text-success" : "text-danger"} mb-0`}>
          {difference.value > 0 ? 'An increase ' : 'A decrease'} of {difference.percent}%
        </h4>
        <p className="mb-0 text-muted">
          {Math.abs(difference.value).toLocaleString('en-GB')}
          {difference.value > 0 ? ' more ' : ' fewer '}
           doses than {prevDay}
        </p>
      </div>
    </Card>
  }

/**
 * Show the total number of people with >=1 dose
 */
const TotalDoses: React.FunctionComponent<CardProps & { dose: keyof DoseTotal }> =
  props => {

    const ukPopulation = 68000000 // the UK population is famously constant
    const percentVaccinated = props.statistics[0] ? (props.statistics[0].total[props.dose] / ukPopulation) * 100 : 0;
    const ratioVaccinated = Math.round(1 / (percentVaccinated / 100));

    return <Card loaded={props.statistics.length > 0}>
      <div className="mb-1 pb-2 border-bottom">
        <h1 className="mb-0">{props.statistics[0]?.total[props.dose].toLocaleString("en-GB") || "0"}</h1>
        <LightText>{doseName(props.dose)} doses given in total.</LightText>
      </div>
      <div className="pt-1">
        <h4 className="mb-0">1 in {ratioVaccinated} people</h4>
        <LightText>{percentVaccinated.toFixed(1)}% of the UK population. (68M people)</LightText>
      </div>
    </Card>
  }


/**
 * Render all the cards together
 * in a two column layout on sizes above mobile
 */
export const Cards: React.FunctionComponent<CardProps> =
  ({ statistics }) => <React.Fragment>
    <div className="row g-0">
      <div className="col-md me-md-1 mb-2 d-flex"><DailyDoses dose="firstDose" statistics={statistics} /></div>
      <div className="col-md ms-md-1 mb-2 d-flex"><TotalDoses dose="firstDose" statistics={statistics} /></div>
    </div>
    <div className="row g-0">
      <div className="col-md me-md-1 mb-2 d-flex"><DailyDoses dose="secondDose" statistics={statistics} /></div>
      <div className="col-md ms-md-1 mb-2 d-flex"><TotalDoses dose="secondDose" statistics={statistics} /></div>
    </div>
  </React.Fragment>
