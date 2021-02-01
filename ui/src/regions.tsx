import * as React from "react";

type State = {
  svg: string,
  data: RegionData
}

type RegionData = {
  [name: string]: number
}

export class Regions extends React.Component<{}, State> {

  constructor(props: {}) {
    super(props)
    this.state = { svg: "nope", data: { foo: 0 } }
  }

  async componentDidMount() {
    const resp = await fetch('nhs.svg');
    const data = await fetch('data.json');
    this.setState({svg: await resp.text(), data: await data.json() as RegionData}, () => this.debug());
  }

  shouldComponentUpdate() {
    return true;
  }

  debug() {

    const max = Object.values(this.state.data).reduce((a, b) => a + b, 0);
    console.log(max)

    Object.entries(this.state.data).forEach(([key, value]) => {
      const opacity = (value / max)
      const region = document.querySelector(`path[inkscape\\:label="${key}"]`)
      region?.setAttribute('style', `opacity:${opacity}`);
      region?.classList.add("valid")
    })
  }

  render() {
    return <div className="bg-white border shadow-sm p-4">
      <div id="region-map" className="text-center" dangerouslySetInnerHTML={{ __html: this.state.svg }} />
    </div>
  }
}
