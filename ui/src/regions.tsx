import * as React from "react";

type State = {
  svg: string,
  data: RegionData[]
}

type  DosesByAge = {
  percentOver80: number,
  under80: number,
  over80: number
}

type RegionStatistics = {
  firstDose: DosesByAge,
  secondDose: DosesByAge
}

type RegionData = {
  date: string,
  statistics: { 
    [name: string]: RegionStatistics }
}


export class Regions extends React.Component<{}, State> {

  constructor(props: {}) {
    super(props)
    this.state = { svg: "nope", data: [] }
  }

  async componentDidMount() {
    const svg = await fetch('nhs.svg');
    let data = await fetch("https://vaccine-statistics-20210117140726225700000002.s3-eu-west-1.amazonaws.com/regional.json");
    this.setState({svg: await svg.text(), data: await data.json() as RegionData[]}, () => this.debug());
  }

  shouldComponentUpdate() {
    return true;
  }

  debug() {

    if (!this.state.data[0]) return;
    const max = Object.values(this.state.data[0].statistics).reduce((a, b) => Math.max(a, b.firstDose.percentOver80), 0);

    Object.entries(this.state.data[0].statistics).forEach(([key, value]) => {
      const opacity = (value.firstDose.percentOver80 / max)
      const region = document.querySelector(`path[inkscape\\:label="${key}"]`)
      region?.setAttribute('style', region?.getAttribute('style') + `; fill-opacity:${opacity}`);
      region?.classList.add("valid")
    })
  }

  render() {
    return <div className="bg-white border shadow-sm p-4">
      <div id="region-map" className="text-center" dangerouslySetInnerHTML={{ __html: this.state.svg }} />
    </div>
  }
}
