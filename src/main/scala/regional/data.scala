package io.tvc.vaccines
package regional

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, KeyDecoder, KeyEncoder}

import java.time.LocalDate

case class Region(name: String) extends AnyVal


sealed trait ByAge

object ByAge {
  implicit val cfg: Configuration = Configuration.default.withDiscriminator("type")
  implicit val codec: Codec[ByAge] = deriveConfiguredCodec
  type L = Long

  case class Over80s(`16-79`: L, `80+`: L) extends ByAge
  case class Over70s(`16-69`: L, `70-74`: L, `75-79`: L, `80+`: L) extends ByAge
  case class Over65s(`16-64`: L, `64-69`: L, `70-74`: L, `75-79`: L, `80+`: L) extends ByAge
  case class Over60s(`16-59`: L, `60-64`: L, `64-69`: L, `70-74`: L, `75-79`: L, `80+`: L) extends ByAge
  case class Over55s(`16-54`: L, `55-59`: L, `60-64`: L, `64-69`: L, `70-74`: L, `75-79`: L, `80+`: L) extends ByAge
}

case class RegionStatistics(
  name: String,
  population: ByAge,
  firstDose: ByAge,
  secondDose: ByAge
)

object RegionStatistics {
  implicit val codec: Codec[RegionStatistics] = deriveCodec
}

case class RegionalTotals(
  statistics: Map[Region, RegionStatistics],
  date: LocalDate
)

object RegionalTotals {
  implicit val codec: Codec[RegionalTotals] = deriveCodec
}

object Region {

  implicit val encoder: KeyEncoder[Region] =
    KeyEncoder.encodeKeyString.contramap(_.name)

  implicit val decoder: KeyDecoder[Region] =
    KeyDecoder.decodeKeyString.map(Region.apply)

  /**
   * Map the names showing up in the NHS Breakdown by ICS/STP of residence
   * to the names I gave the ICS/STP regions in the SVG map
   */
  def forName(name: String): Option[Region] =
    name match {
      case "Cambridgeshire and Peterborough" =>
        Some(Region("cambridgeshire_and_peterborough"))
      case "Bedfordshire, Luton and Milton Keynes" =>
        Some(Region("milton_keynes_bedfordshire_and_luton"))
      case "Hertfordshire and West Essex" =>
        Some(Region("hertfordshire_and_west_essex"))
      case "Mid and South Essex" =>
        Some(Region("mid_and_south_essex"))
      case "Norfolk and Waveney Health and Care Partnership" =>
        Some(Region("norfolk_and_waveney"))
      case "Suffolk and North East Essex" =>
        Some(Region("suffolk_and_north_east_essex"))
      case "East London Health and Care Partnership" =>
        Some(Region("north_east_london"))
      case "North London Partners in Health and Care" =>
        Some(Region("north_central_london"))
      case "North West London Health and Care Partnership" =>
        Some(Region("north_west_london"))
      case "Our Healthier South East London" =>
        Some(Region("south_east_london"))
      case "South West London Health and Care Partnership" =>
        Some(Region("south_west_london"))
      case "Birmingham and Solihull" =>
        Some(Region("birmingham_and_solihull"))
      case "Coventry and Warwickshire" =>
        Some(Region("coventry_and_warwickshire"))
      case "Herefordshire and Worcestershire" =>
        Some(Region("herefordshire_and_worcestershire"))
      case "Joined Up Care Derbyshire" =>
        Some(Region("derbyshire"))
      case "Leicester, Leicestershire and Rutland" =>
        Some(Region("leicester_leicestershire_and_rutland"))
      case "Lincolnshire" =>
        Some(Region("lincolnshire"))
      case "Northamptonshire" =>
        Some(Region("northamptonshire"))
      case "Nottingham and Nottinghamshire Health and Care" =>
        Some(Region("nottinghamshire"))
      case "Shropshire and Telford and Wrekin" =>
        Some(Region("shropshire_and_telford_and_wrekin"))
      case "Staffordshire and Stoke on Trent" =>
        Some(Region("staffordshire"))
      case "The Black Country and West Birmingham" =>
        Some(Region("the_black_country"))
      case "Cumbria and North East" =>
        Some(Region("the_north_east_and_north_cumbria"))
      case "Humber, Coast and Vale" =>
        Some(Region("humber_coast_and_vale"))
      case "South Yorkshire and Bassetlaw" =>
        Some(Region("south_yorkshire_and_bassetlaw"))
      case "West Yorkshire and Harrogate (Health and Care Partnership)" =>
        Some(Region("west_yorkshire"))
      case "Cheshire and Merseyside" =>
        Some(Region("cheshire_and_merseyside"))
      case "Greater Manchester Health and Social Care Partnership" =>
        Some(Region("greater_manchester"))
      case "Healthier Lancashire and South Cumbria" | "Lancashire and South Cumbria ICS" =>
        Some(Region("lancashire_and_south_cumbria"))
      case "Buckinghamshire, Oxfordshire and Berkshire West" =>
        Some(Region("buckinghamshire_oxfordshire_and_berkshire_west"))
      case "Frimley Health and Care ICS" =>
        Some(Region("frimley_health"))
      case "Hampshire and the Isle of Wight" =>
        Some(Region("hampshire_and_the_isle_of_wight"))
      case "Kent and Medway" =>
        Some(Region("kent_and_medway"))
      case "Surrey Heartlands Health and Care Partnership" =>
        Some(Region("surrey_heartlands"))
      case "Sussex Health and Care Partnership" =>
        Some(Region("sussex_and_east_surrey"))
      case "Bath and North East Somerset, Swindon and Wiltshire" =>
        Some(Region("bath_swindon_and_wiltshire"))
      case "Bristol, North Somerset and South Gloucestershire" =>
        Some(Region("bristol_north_somerset_and_south_gloucestershire"))
      case "Cornwall and the Isles of Scilly Health and Social Care Partnership" =>
        Some(Region("cornwall"))
      case "Devon" =>
        Some(Region("devon"))
      case "Dorset" =>
        Some(Region("dorset"))
      case "Gloucestershire" =>
        Some(Region("gloucestershire"))
      case "Somerset" =>
        Some(Region("somerset"))
      case _ =>
        None
    }
}


