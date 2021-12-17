import * as React from "react";
import { DailyTotal } from "./model";
import { Chart, ChartConfiguration } from 'chart.js'
import { format } from "date-fns";

export type ChartProps = {
  statistics: DailyTotal[],
  type: "total" | "today"
}

export type ChartState = {
  chart: Chart | null
}

function today(stats: DailyTotal[]): ChartConfiguration {
  return {
    type: 'bar',
    options: {
      scales: {
        yAxes: [
          {
            stacked: true, 
            ticks: {
              callback: formatNumber,
              lineHeight: 2
            },
          }
        ], 
        xAxes: [
          {
            stacked: true
          }
        ]
      },
    },
    data: {
      labels: stats.map(d => format(new Date(d.date), 'do MMM')),
      datasets: [
        {
          data: stats.map(d => d["today"].firstDose), 
          backgroundColor: "#1ab9d2dd",
          borderColor: "#17a2b8",
          label: "First Dose",
          borderWidth: 1
        },
        {
          data: stats.map(d => d["today"].secondDose),
          backgroundColor: "#2dbf4fdd",
          borderColor: "#28a745",
          label: "Second Dose",
          borderWidth: 1
        },
        {
          data: stats.map(d => d["today"].thirdDose),
          backgroundColor: "#f5c211",
          borderColor: "#c49a08",
          label: "Third / Booster Dose",
          borderWidth: 1
        }
     ]
    }
  }
}

function formatNumber(value: number) {
  return value == 0 ? value : (
    value >= 1_000_000 
      ? (value / 1_000_000).toFixed(0) + 'M' 
      : (value / 1_000).toFixed(0) + 'K'
    )
}

function total(data: DailyTotal[]): ChartConfiguration {
  return {
    type: 'line',
    data: {
      labels: data.map(d => format(new Date(d.date), 'do MMM')),
      datasets: [
        { 
          label: 'First Dose', 
          data: data.map(d => d["total"].firstDose), 
          backgroundColor: 'transparent',
          borderColor: '#17a2b8'
        },
        { 
          label: 'Second Dose', 
          data: data.map(d => d["total"].secondDose), 
          backgroundColor: 'transparent',
          borderColor: '#28a745'
        },
        {
          data: data.map(d => d["total"].thirdDose),
          backgroundColor: 'transparent',
          borderColor: "#f5c211",
          label: "Third / Booster Dose",
          borderWidth: 1
        },

      ]
    },
    options: {
      scales: {
        yAxes: [
          {
            ticks: {
              callback: formatNumber,
              lineHeight: 2
            }
          }
        ]
      }
    }
  }
}

function makeChart(statistics: DailyTotal[], chartType: "total" | "today"): Chart {
  const data = [...statistics].reverse().slice(1)
  const ctx = (document.querySelector(`#chart-${chartType}`) as HTMLCanvasElement).getContext('2d')!;
  return new Chart(ctx, chartType == "today" ? today(data) : total(data));  
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
    return <div className="bg-card border p-2 p-sm-4 mb-2 shadow-sm">
      <h5 className="text-center mb-4 mt-sm-0 mt-2">{chartTitle(this.props.type)}</h5>
      <canvas id={`chart-${this.props.type}`}></canvas>
    </div>
  }
}
