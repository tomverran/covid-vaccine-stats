import { DailyTotal, calculateDifferences } from "./model"
import * as React from "react";

/**
 * Render a bootstrap card
 * with a particular title & subtitle
 */
function Card(props: { title: string, subtitle: string }): JSX.Element {
  return <div className="card shadow-sm">
    <div className="card-body">
      <h1 className="card-title mb-3">{props.title}</h1>
      <h6 className="card-subtitle mb-1 text-muted">{props.subtitle}</h6>
    </div>
  </div>
}


export type CardProps = {
  statistics: DailyTotal[]
}

/**
 * Card to show the number of first doses today
 */
function DosesToday(props: CardProps): JSX.Element {
  return Card(
    {
      title: props.statistics[0]?.today.firstDose.toLocaleString("en-GB"),
      subtitle: `First doses of any COVID vaccine given on ${props.statistics[0]?.date}`
    }
  )
}

/**
 * Card to compare the number of doses today
 * with the number of doses the day before
 */
function Comparison(props: CardProps): JSX.Element {
  const diffs = props.statistics.length > 1 ? calculateDifferences(props.statistics) : { value: 0, percent: 0 }
  return Card(
    {
      title: `${diffs.value > 0 ? "Up" : "Down"} ${diffs.percent}%`,
      subtitle: `${diffs.value.toLocaleString("en-GB")} ${diffs.value > 0 ? "more" : "fewer"} doses than the day before.`
    }
  )
}

/**
 * Show the total number of people with >=1 dose
 */
function TotalDoses(props: CardProps): JSX.Element {
  return Card(
    {
      title: props.statistics[0]?.total.firstDose.toLocaleString("en-GB"),
      subtitle: `People in total have had at least one dose.`
    }
  )
}

/**
 * Produce a ratio of vaccinated:unvaccinated people
 * using a very rough approximation of the UK population
 */
function OneInXPeople(props: CardProps): JSX.Element {

  const ukPopulation = 68000000 // the UK population is famously constant
  const percentVaccinated = props.statistics.length ? (props.statistics[0].total.firstDose / ukPopulation) * 100 : 0;

  return Card(
    {
      title: `1 in ${Math.round(1 / (percentVaccinated / 100))} people`,
      subtitle: `Assuming a UK population of 68M people.`
    }
  )
}

/**
 * Render all the cards together
 * in a two column layout on sizes above mobile
 */
export function Cards(props: CardProps): JSX.Element {
  return <div className="container">
    <div className="row g-0">
      <div className="col-sm me-sm-1 mb-2">{DosesToday(props)}</div>
      <div className="col-sm ms-sm-1 mb-2">{Comparison(props)}</div>
    </div>
    <div className="row g-0">
      <div className="col-sm me-sm-1 mb-2">{TotalDoses(props)}</div>
      <div className="col-sm ms-sm-1 mb-2">{OneInXPeople(props)}</div>
    </div>
  </div>
}