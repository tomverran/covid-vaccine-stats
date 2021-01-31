import { DailyTotal, project, Projection } from "./model";
import { format } from 'date-fns'
import * as React from "react";


const noData: Projection = {
  firstFour: new Date(),
  firstSix: new Date(),
}

export const DayProjection: React.FunctionComponent<{ statistics: DailyTotal[] }> =
  props => {
    const projection = props.statistics[0] ? project(props.statistics) : noData;
    return <div className={`bg-white p-4 mb-2 border shadow-sm ${props.statistics.length ? '' : 'loading'}`}>
      <h3 className="mb-3">If every day were like this...</h3>
      <p className="mb-md-0 mb-1 text-muted">
        Everyone in <strong>the first four</strong> vulnerable groups would have a dose by the
        <strong> {format(projection.firstFour, `do 'of' MMMM`)}</strong>.
      </p>
      <p className="mb-1 mb-md-2 text-muted">
        Everyone in <strong>the first six</strong> vulnerable groups would have a dose by the
        <strong> {format(projection.firstSix, `do 'of' MMMM`)}</strong>.
      </p>
      <small className="text-muted">
        These are very approximate projections assuming 100% uptake. Group size data from the
        <a href="https://www.bbc.co.uk/news/health-55045639"> BBC</a>.
      </small>
    </div>
  }
