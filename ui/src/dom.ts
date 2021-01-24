import { Chart } from 'chart.js'

type NodeProperties = {
  classes: string[]
  text: string
}

type TemplateData = {
  [key: string]: TemplateData | string | number | NodeProperties
}

function hyphenate(what: string): string {
  return what.replace(/([a-z])([A-Z])/g, "$1-$2").toLowerCase()
}

function isNodeProps(what: TemplateData | NodeProperties): what is NodeProperties {
  return what.classes !== undefined && Array.isArray(what.classes)
}

export function template(what: TemplateData, prefix: string = ""): void {
  Object.entries(what).forEach(([key, value]) => {
    if (typeof value === "string" || typeof value === "number") {
      const rendered = typeof value == "string" ? value : value.toLocaleString("en-GB")
      document.querySelectorAll(`.${prefix}${hyphenate(key)}`).forEach(e => e.textContent = rendered)
      console.log(`.${prefix}${hyphenate(key)}`)
    } else if (isNodeProps(value)) {
      document.querySelectorAll(`.${prefix}${hyphenate(key)}`).forEach(e => {
        value.classes.forEach(c => e.classList.add(c));
        e.textContent = value.text
      })
    } else {
      template(value, hyphenate(key) + "-");
    }
  })
}
