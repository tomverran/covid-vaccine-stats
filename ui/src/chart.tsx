import * as React from "react";
import { DailyTotal } from "./model";
import { Chart } from 'chart.js'
import { format } from "date-fns";

export type ChartProps = {
  statistics: DailyTotal[],
  type: "total" | "today"
}

export type ChartState = {
  chart: Chart | null
}

function makeChart(statistics: DailyTotal[], chartType: "total" | "today"): Chart {

  const data = [...statistics].reverse().slice(1)
  const ctx = (document.querySelector(`#chart-${chartType}`) as HTMLCanvasElement).getContext('2d')!;

  return new Chart(ctx, {
    type: 'line',
    data: {
      labels: data.map(d => format(new Date(d.date), 'do MMM')),
      datasets: [
        { 
          label: 'First Dose', 
          data: data.map(d => d[chartType].firstDose), 
          backgroundColor: 'transparent',
          borderColor: '#17a2b8'
        },
        { 
          label: 'Second Dose', 
          data: data.map(d => d[chartType].secondDose), 
          backgroundColor: 'transparent',
          borderColor: '#28a745'
        }
      ]
    }
  });  
}

function chartTitle(type: "today" | "total") {
  switch(type) {
    case "today": return "Vaccinations given per day"
    case "total": return "Cumulative vaccinations over time"
  }
}

export class DoseChart extends React.Component<ChartProps, ChartState> {

  constructor(props: ChartProps) {
    super(props)
    this.state = { chart: null };
  }

  createOrUpdateChart() {
    if (this.state.chart == null && this.props.statistics.length > 0) {
      this.setState({ chart: makeChart(this.props.statistics, this.props.type) });
    }
  }

  componentDidMount() {
    this.createOrUpdateChart();
  }

  componentDidUpdate() {
    this.createOrUpdateChart();
  }

  render() {
    return <div className="bg-white border p-2 p-sm-4 mb-2 shadow-sm">
      <h5 className="text-center mb-4 mt-sm-0 mt-2">{chartTitle(this.props.type)}</h5>
      <canvas id={`chart-${this.props.type}`}></canvas>
    </div>
  }
}
