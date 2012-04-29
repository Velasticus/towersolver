package com.kalulu.sgs.swtor.towersolver.persistence

import scala.xml.NodeSeq
import scala.xml.NamespaceBinding
import scala.io.Source
import scala.io.Source.fromInputStream
import scala.xml.MetaData
import java.net.URL
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerContinent
import com.kalulu.sgs.swtor.towersolver.protocol.server.SingleServer
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerType
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerType._
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerRegion
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerRegion._
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerPopulation
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerStatus
import com.kalulu.sgs.swtor.towersolver.protocol.server.ServerStatus._
import com.kalulu.sgs.swtor.towersolver.protocol.PersistentObjectType
import scala.xml.Node
import scala.xml.pull.EvText
import scala.xml.pull.XMLEventReader
import scala.xml.pull.EvElemStart
import java.io.BufferedInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import org.cyberneko.html.parsers.SAXParser
import org.xml.sax.DocumentHandler
import org.xml.sax.ContentHandler
import org.xml.sax.InputSource
import org.xml.sax.helpers.AttributesImpl
import com.kalulu.sgs.swtor.towersolver.traits.ServerTrait
import com.kalulu.sgs.swtor.towersolver.impl.ServerImpl
import org.cyberneko.html.HTMLConfiguration
import org.cyberneko.html.filters.Purifier
import org.cyberneko.html.filters.Writer
import scala.util.matching.Regex.Match


class SWTORServerParser (urlLoc:String = "http://www.swtor.com/server-status") {
  def servers = _servers

  private def attr2map(attrs:Attributes) = {
    var attrMap = Map[String,String]()
    var i = 0
    var name = attrs.getLocalName(i)
    while (name != null) {
      attrMap += (name->attrs.getValue(i))
      i+=1
      name = attrs.getLocalName(i)
    }
    attrMap
  }
  
  private var textProcessor : ( EvText => Unit ) = processTextNode
  private def processTextNode(node:EvText) = {}
  private def processContinent(node:EvText) = {
    val Region = "(?s)(.*) Servers.*".r
    node.text match {
      case Region(region) => region match {
        case "US" => continent = ServerContinent.AMERICA
        case "European" => continent = ServerContinent.EUROPE
        case "Asia Pacific" => continent = ServerContinent.ASIA
      }
    }
  }
  private def processServerName(node:EvText) = {
    builder.setName(node.text)
    _servers ::= ServerImpl(builder.build)
    builder = null
  }
  private val handler = new DefaultHandler() {
    var text : StringBuilder = null
    override def startElement(uri:String,localName:String,qName:String,attrs:Attributes) = {
      val attrMap = attr2map(attrs)
      qName match {
        case "h2" =>
          if (attrMap.getOrElse("class","").contains("serverStatusTitle") ) {
            text = new StringBuilder()
            textProcessor = processContinent
          }
        case "div" => {
          if(attrMap.getOrElse("class","").contains("serverBody row")) {
            builder = SingleServer.newBuilder
            processServerAttrs(attrMap)
          }
          if(builder != null && attrMap.getOrElse("class","").contains("name")) {
            text = new StringBuilder()
            textProcessor = processServerName
          }
        }
        case _ =>
      }
    }
    override def characters(ch:Array[Char],start:Int,length:Int) = {
      if ( text != null )
        text.append(ch,start,length)
    }
    override def endElement(uri:String,localName:String,qName:String) {
      if (text != null) {
        textProcessor(EvText(text.toString.trim))
        textProcessor = processTextNode
        text = null
      }
    }
  }
  private var builder : SingleServer.Builder = null
  private var continent : ServerContinent = null
  private var _servers = List[ServerTrait]() 
  
  private val sax = new SAXParser()
  sax.setFeature("http://xml.org/sax/features/validation", false);
  sax.setProperty("http://cyberneko.org/html/properties/names/elems", "lower")
  //sax.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
  sax.setContentHandler(handler.asInstanceOf[ContentHandler])

  private def inputSource = { 
    val c = new URL(urlLoc).openConnection
    c.setReadTimeout(1000)
    c.setConnectTimeout(1000)
    new InputSource(c.getInputStream)
  }
  
  sax.parse(inputSource)
  
  private def processServerAttrs(attrs: Map[String,String]) {
    builder.setObjectType(PersistentObjectType.SERVER)
    builder.setContinent(continent)
    builder.setType( ServerTypeExtractor(attrs("data-type")) )
    builder.setRegion( ServerRegionExtractor(attrs.getOrElse("data-timezone",attrs.get("data-language").get)))
    builder.setPopulation( ServerPopulation.values()(attrs("data-population").toInt-1))
    builder.setStatus( ServerStatusExtractor(attrs("data-status")) )
  }

}

private object ServerTypeExtractor {
  private val pve = "(?i)PvE".r
  private val pvp = "(?i)PvP".r
  private val rppve = "(?i)RP-PvE".r
  private val rppvp = "(?i)RP-PvP".r
  def apply( x:String ) = x match {
    case pve() => PVE
    case pvp() => PVP
    case rppve() => RPPVE
    case rppvp() => RPPVP
  }
  def unapply ( x:ServerType ) = x match {
    case PVE => pve
    case PVP => pvp
    case RPPVE => rppve
    case RPPVP => rppvp
  }
}

private object ServerRegionExtractor {
  private val east = "(?i)EAST".r
  private val west = "(?i)WEST".r
  private val english = "(?i)ENGLISH".r
  private val german = "(?i)GERMAN".r
  private val french = "(?i)FRENCH".r
  def apply( x:String ) = x match {
    case east() => EAST
    case west() => WEST
    case english() => ENGLISH
    case german() => GERMAN
    case french() => FRENCH
  }
  def unapply ( x:ServerRegion ) = x match {
    case EAST => east
    case WEST => west
    case ENGLISH => english
    case GERMAN => german
    case FRENCH => french
  }
}

private object ServerStatusExtractor {
  private val up = "UP".r
  private val down = "DOWN".r
  def apply( x:String ) = x match {
    case up() => UP
    case down() => DOWN
  }
  def unapply ( x:ServerStatus ) = x match {
    case UP => up
    case DOWN => down
  }
}
